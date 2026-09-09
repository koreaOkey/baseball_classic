"""잠금화면 Live Activity 발송기.

ingest 요청의 BackgroundTasks 체인 끝에서 발송하던 구조를 분리한다 (2026-09-09,
openspec `decouple-live-activity-sender`). 9/8 로그 기준 APNs 는 전부 성공인데도 체인
앞단(WS·캐시·사일런트 푸시 팬아웃) 지연과 동시 체인 간 순서 역전으로 발송이 수 분간
사라지거나 낡은 상태가 나가는 것이 확인됐다.

- 경기별 직렬화: 프로세스 안은 asyncio.Lock + 최신 pending 슬롯 drain, 프로세스 간은
  Redis 단기 락. 이미 발송한 것보다 오래된 상태(seq)는 절대 보내지 않는다.
- APNs timestamp 는 경기별 단조 증가(max(now, last+1)). 순서는 서버가 보장하고
  timestamp 는 iOS 가 유효한 갱신을 드롭하지 않게만 한다.
- 볼카운트성 갱신 코얼레싱(20s)은 trailing flush 를 가져 마지막 투구도 반영된다.
- heartbeat 는 ingest 와 무관한 타이머(60s). 파이프라인이 살아 있을 때(마지막 ingest
  10분 이내)만 유지해 크롤러 정지 시 카드가 정직하게 stale 로 떨어진다.
"""
from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

logger = logging.getLogger("app.live_activity")
# 루트 로거가 WARNING 이라 INFO 가 묻히므로 전용 로거만 개방 (발송 정책 검증용).
logger.setLevel(logging.INFO)

LAST_STATE_CACHE_KEY = "live_activity_last_state:{game_id}"
LAST_STATE_KEY_PREFIX = "live_activity_last_state:"
LAST_STATE_TTL_SEC = 6 * 3600
LOCK_KEY = "live_activity_lock:{game_id}"
LOCK_TTL_MS = 5000
LOCK_RETRY_INTERVAL_SEC = 0.2
LOCK_RETRY_MAX_SEC = 3.0
HEARTBEAT_SEC = 60                 # 마지막 발송 후 이 시간이 지나면 같은 상태를 새 stale-date 로 재발송
HEARTBEAT_TICK_SEC = 15            # heartbeat 루프 주기
HEARTBEAT_INGEST_CUTOFF_SEC = 600  # 마지막 ingest 가 이보다 오래되면 heartbeat 중단 (파이프라인 정지)
ROUTINE_MIN_INTERVAL_SEC = 20      # 볼카운트성 갱신 최소 발송 간격
STALE_SECONDS = 180
# 이 필드만 변한 업데이트는 일상 갱신으로 간주 (스코어·주자·아웃·이닝·투수·상태 변화가 significant)
ROUTINE_ONLY_FIELDS = frozenset({"ball", "strike", "batter", "lastEventType"})
SIGNIFICANT_EVENT_TYPES = frozenset({
    "HOMERUN", "SCORE", "SAC_FLY_SCORE",
    "HIT", "WALK", "HIT_BY_PITCH",
    "STEAL", "TAG_UP_ADVANCE",
    "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY",
    "PITCHER_CHANGE",
})

TokenLoader = Callable[[str], Awaitable[list[str]]]
PushSender = Callable[..., Awaitable[tuple[bool, bool]]]
DeadTokenPruner = Callable[[str, list[str]], Awaitable[None]]


def build_content_state(state_payload: dict[str, Any]) -> dict[str, Any]:
    bases = state_payload.get("bases") or {}
    return {
        "homeScore": state_payload.get("homeScore", 0),
        "awayScore": state_payload.get("awayScore", 0),
        "inning": state_payload.get("inning", ""),
        "ball": state_payload.get("ball", 0),
        "strike": state_payload.get("strike", 0),
        "out": state_payload.get("out", 0),
        "baseFirst": bases.get("first", False),
        "baseSecond": bases.get("second", False),
        "baseThird": bases.get("third", False),
        "pitcher": state_payload.get("pitcher", "") or "",
        "batter": state_payload.get("batter", "") or "",
        "status": state_payload.get("status", "LIVE"),
        "lastEventType": state_payload.get("lastEventType"),
    }


def is_significant(prev_state: dict[str, Any] | None, next_state: dict[str, Any]) -> bool:
    if prev_state is None:
        return True
    if any(
        prev_state.get(key) != value
        for key, value in next_state.items()
        if key not in ROUTINE_ONLY_FIELDS
    ):
        return True
    return (next_state.get("lastEventType") or "").upper() in SIGNIFICANT_EVENT_TYPES


@dataclass
class Candidate:
    content_state: dict[str, Any]
    event_type: str
    seq: float
    ingest_at: float


@dataclass
class LastState:
    state: dict[str, Any] | None = None
    sent_at: float = 0.0
    seq: float = 0.0
    ts: int = 0
    ingest_at: float = 0.0
    last_event: str = "update"
    raw: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_cache(cls, cached: Any) -> "LastState":
        if not isinstance(cached, dict):
            return cls()
        state = cached.get("state")
        return cls(
            state=state if isinstance(state, dict) else None,
            sent_at=_as_float(cached.get("sentAt")),
            seq=_as_float(cached.get("seq")),
            ts=int(_as_float(cached.get("ts"))),
            ingest_at=_as_float(cached.get("ingestAt")),
            last_event=str(cached.get("lastEvent") or "update"),
            raw=cached,
        )

    def to_cache(self) -> dict[str, Any]:
        return {
            "state": self.state,
            "sentAt": self.sent_at,
            "seq": self.seq,
            "ts": self.ts,
            "ingestAt": self.ingest_at,
            "lastEvent": self.last_event,
        }


def _as_float(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0.0


class LiveActivitySender:
    def __init__(
        self,
        *,
        relay: Any,
        token_loader: TokenLoader,
        push_sender: PushSender,
        prune_dead_tokens: DeadTokenPruner | None = None,
        clock: Callable[[], float] = time.time,
        sleeper: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        self._relay = relay
        self._token_loader = token_loader
        self._push_sender = push_sender
        self._prune_dead_tokens = prune_dead_tokens
        self._clock = clock
        self._sleep = sleeper
        self._pending: dict[str, Candidate] = {}
        self._locks: dict[str, tuple[asyncio.AbstractEventLoop, asyncio.Lock]] = {}
        self._flush_timers: dict[str, asyncio.Task[None]] = {}
        self._tasks: set[asyncio.Task[None]] = set()
        self.stats: dict[str, int] = {
            "dispatched": 0, "sent": 0, "heartbeat": 0,
            "skip_unchanged": 0, "skip_coalesce": 0, "skip_stale": 0,
            "no_tokens": 0, "lock_busy": 0, "errors": 0,
        }

    # MARK: - 진입점

    def dispatch(
        self,
        game_id: str,
        state_payload: dict[str, Any],
        *,
        event_type: str = "update",
        seq: float | None = None,
        ingest_at: float | None = None,
    ) -> asyncio.Task[None]:
        """fire-and-forget. ingest 체인의 나머지(WS·캐시·팬아웃)와 병렬로 발송한다."""
        self.stats["dispatched"] += 1
        task = asyncio.create_task(
            self.submit(game_id, state_payload, event_type=event_type, seq=seq, ingest_at=ingest_at),
            name=f"live-activity:{game_id}",
        )
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    async def submit(
        self,
        game_id: str,
        state_payload: dict[str, Any],
        *,
        event_type: str = "update",
        seq: float | None = None,
        ingest_at: float | None = None,
    ) -> None:
        now = self._clock()
        candidate = Candidate(
            content_state=build_content_state(state_payload),
            event_type=event_type,
            seq=seq if seq is not None else now,
            ingest_at=ingest_at if ingest_at is not None else now,
        )
        existing = self._pending.get(game_id)
        if existing is None or candidate.seq >= existing.seq:
            self._pending[game_id] = candidate
        try:
            await self._drain(game_id)
        except Exception:
            self.stats["errors"] += 1
            logger.exception("[APNs-LA] game=%s result=error", game_id)

    # MARK: - 경기별 직렬화

    def _game_lock(self, game_id: str) -> asyncio.Lock:
        loop = asyncio.get_running_loop()
        entry = self._locks.get(game_id)
        if entry is None or entry[0] is not loop:
            entry = (loop, asyncio.Lock())
            self._locks[game_id] = entry
        return entry[1]

    async def _drain(self, game_id: str) -> None:
        lock = self._game_lock(game_id)
        while True:
            if lock.locked():
                # 진행 중인 drain 이 pending 슬롯을 이어서 처리한다.
                return
            async with lock:
                while True:
                    candidate = self._pending.pop(game_id, None)
                    if candidate is None:
                        break
                    await self._process(game_id, candidate)
            # 락 해제와 이 확인 사이에는 await 가 없어 새 후보가 끼어들 수 없다.
            if game_id not in self._pending:
                return

    async def _acquire_redis_lock(self, game_id: str, *, wait: bool) -> str | None:
        key = LOCK_KEY.format(game_id=game_id)
        deadline = self._clock() + (LOCK_RETRY_MAX_SEC if wait else 0.0)
        while True:
            token = await self._relay.try_lock(key, LOCK_TTL_MS)
            if token is not None:
                return token
            if self._clock() >= deadline:
                return None
            await self._sleep(LOCK_RETRY_INTERVAL_SEC)

    async def _release_redis_lock(self, game_id: str, token: str | None) -> None:
        if token is None:
            return
        try:
            await self._relay.release_lock(LOCK_KEY.format(game_id=game_id), token)
        except Exception:
            logger.debug("[APNs-LA] game=%s unlock failed", game_id, exc_info=True)

    async def _load_last(self, game_id: str) -> LastState:
        cached = await self._relay.get_cache(LAST_STATE_CACHE_KEY.format(game_id=game_id))
        return LastState.from_cache(cached)

    async def _store_last(self, game_id: str, last: LastState) -> None:
        await self._relay.set_cache(
            LAST_STATE_CACHE_KEY.format(game_id=game_id),
            last.to_cache(),
            ttl_sec=LAST_STATE_TTL_SEC,
        )

    # MARK: - 발송 결정

    async def _process(self, game_id: str, candidate: Candidate) -> None:
        tokens = await self._token_loader(game_id)
        if not tokens:
            self.stats["no_tokens"] += 1
            self._log(game_id, "no_tokens", 0, 0, False, candidate.event_type, candidate.ingest_at, 0)
            return

        lock_token = await self._acquire_redis_lock(game_id, wait=True)
        if lock_token is None:
            # 다른 프로세스가 오래 잡고 있음. 발송을 막기보다 락 없이 진행하고 흔적을 남긴다.
            self.stats["lock_busy"] += 1
            logger.warning("[APNs-LA] game=%s result=lock_busy proceeding without lock", game_id)
        try:
            last = await self._load_last(game_id)
            now = self._clock()
            # 파이프라인 생존 표식은 스킵 여부와 무관하게 갱신한다 (heartbeat 컷오프 근거).
            last.ingest_at = max(last.ingest_at, candidate.ingest_at)

            if last.state is not None and candidate.seq <= last.seq:
                self.stats["skip_stale"] += 1
                self._log(game_id, "skip_stale", 0, len(tokens), False, candidate.event_type, candidate.ingest_at, last.ts)
                await self._store_last(game_id, last)
                return

            if candidate.event_type == "end":
                significant = True
            elif last.state == candidate.content_state:
                if now - last.sent_at < HEARTBEAT_SEC:
                    # 내용 동일 → 보낼 것 없음. seq 는 넘겨서 같은 상태의 재시도가 stale 로 정리되게 한다.
                    last.seq = candidate.seq
                    self.stats["skip_unchanged"] += 1
                    self._log(game_id, "skip_unchanged", 0, len(tokens), False, candidate.event_type, candidate.ingest_at, last.ts)
                    await self._store_last(game_id, last)
                    return
                significant = False
            else:
                significant = is_significant(last.state, candidate.content_state)
                if not significant and now - last.sent_at < ROUTINE_MIN_INTERVAL_SEC:
                    self.stats["skip_coalesce"] += 1
                    self._log(game_id, "skip_coalesce", 0, len(tokens), False, candidate.event_type, candidate.ingest_at, last.ts)
                    await self._store_last(game_id, last)
                    self._arm_trailing_flush(game_id, candidate, ROUTINE_MIN_INTERVAL_SEC - (now - last.sent_at))
                    return

            ts = max(int(now), last.ts + 1)
            ok_count, dead = await self._send_all(tokens, candidate.content_state, candidate.event_type, ts)
            self.stats["sent"] += 1
            self._log(game_id, "sent", ok_count, len(tokens), significant, candidate.event_type, candidate.ingest_at, ts)
            last.state = candidate.content_state
            last.sent_at = self._clock()
            last.seq = candidate.seq
            last.ts = ts
            last.last_event = candidate.event_type
            await self._store_last(game_id, last)
            self._cancel_trailing_flush(game_id)
        finally:
            await self._release_redis_lock(game_id, lock_token)

        if dead and self._prune_dead_tokens is not None:
            await self._prune_dead_tokens(game_id, dead)

    async def _send_all(
        self,
        tokens: list[str],
        content_state: dict[str, Any],
        event_type: str,
        ts: int,
    ) -> tuple[int, list[str]]:
        results = await asyncio.gather(
            *(
                self._push_sender(
                    token, content_state,
                    event_type=event_type, timestamp=ts, stale_seconds=STALE_SECONDS,
                )
                for token in tokens
            ),
            return_exceptions=True,
        )
        errors = [r for r in results if isinstance(r, BaseException)]
        if errors:
            first = errors[0]
            logger.warning(
                "[live-activity-push] %d/%d sends raised %s: %s",
                len(errors), len(results), type(first).__name__, first,
            )
        ok_count = 0
        dead: list[str] = []
        for token, result in zip(tokens, results):
            if isinstance(result, BaseException):
                continue
            ok, permanent = result
            if ok:
                ok_count += 1
            elif permanent:
                dead.append(token)
        return ok_count, dead

    # MARK: - trailing flush (코얼레싱으로 미뤄진 마지막 상태)

    def _arm_trailing_flush(self, game_id: str, candidate: Candidate, delay_sec: float) -> None:
        self._cancel_trailing_flush(game_id)

        async def _flush() -> None:
            try:
                await self._sleep(max(0.05, delay_sec))
                pending = self._pending.get(game_id)
                if pending is None or candidate.seq >= pending.seq:
                    self._pending[game_id] = candidate
                self._flush_timers.pop(game_id, None)
                await self._drain(game_id)
            except asyncio.CancelledError:
                raise
            except Exception:
                self.stats["errors"] += 1
                logger.exception("[APNs-LA] game=%s result=error trailing flush", game_id)

        task = asyncio.create_task(_flush(), name=f"live-activity-flush:{game_id}")
        self._flush_timers[game_id] = task
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)

    def _cancel_trailing_flush(self, game_id: str) -> None:
        task = self._flush_timers.pop(game_id, None)
        if task is not None and not task.done():
            task.cancel()

    # MARK: - heartbeat

    async def run_heartbeat_loop(self) -> None:
        while True:
            try:
                await self.heartbeat_tick()
            except asyncio.CancelledError:
                raise
            except Exception:
                self.stats["errors"] += 1
                logger.exception("[APNs-LA] heartbeat tick failed")
            await self._sleep(HEARTBEAT_TICK_SEC)

    async def heartbeat_tick(self) -> None:
        keys = await self._relay.scan_keys(f"{LAST_STATE_KEY_PREFIX}*")
        for key in keys:
            if not key.startswith(LAST_STATE_KEY_PREFIX):
                continue
            game_id = key[len(LAST_STATE_KEY_PREFIX):]
            last = await self._load_last(game_id)
            if not self._heartbeat_due(last, self._clock()):
                continue
            await self._heartbeat_game(game_id)

    @staticmethod
    def _heartbeat_due(last: LastState, now: float) -> bool:
        if last.state is None or last.last_event == "end":
            return False
        if str(last.state.get("status") or "").upper() != "LIVE":
            return False
        if last.ingest_at <= 0 or now - last.ingest_at > HEARTBEAT_INGEST_CUTOFF_SEC:
            return False
        return now - last.sent_at >= HEARTBEAT_SEC

    async def _heartbeat_game(self, game_id: str) -> None:
        lock = self._game_lock(game_id)
        if lock.locked():
            return  # 이 프로세스에서 발송 진행 중
        async with lock:
            lock_token = await self._acquire_redis_lock(game_id, wait=False)
            if lock_token is None:
                return  # 다른 프로세스가 처리 중
            try:
                last = await self._load_last(game_id)
                now = self._clock()
                if not self._heartbeat_due(last, now):
                    return
                tokens = await self._token_loader(game_id)
                if not tokens:
                    return
                assert last.state is not None
                ts = max(int(now), last.ts + 1)
                ok_count, dead = await self._send_all(tokens, last.state, "update", ts)
                self.stats["heartbeat"] += 1
                self._log(game_id, "heartbeat", ok_count, len(tokens), False, "update", last.ingest_at, ts)
                last.sent_at = self._clock()
                last.ts = ts
                await self._store_last(game_id, last)
            finally:
                await self._release_redis_lock(game_id, lock_token)
        if dead and self._prune_dead_tokens is not None:
            await self._prune_dead_tokens(game_id, dead)

    # MARK: - 로그

    def _log(
        self,
        game_id: str,
        result: str,
        ok_count: int,
        token_count: int,
        significant: bool,
        event_type: str,
        ingest_at: float,
        ts: int,
    ) -> None:
        lag_ms = int(max(0.0, self._clock() - ingest_at) * 1000) if ingest_at > 0 else -1
        logger.info(
            "[APNs-LA] game=%s result=%s sent=%d/%d significant=%s event=%s lag_ms=%d ts=%d",
            game_id, result, ok_count, token_count, significant, event_type, lag_ms, ts,
        )

    async def shutdown(self) -> None:
        for task in list(self._tasks):
            task.cancel()
        for task in list(self._tasks):
            try:
                await task
            except (asyncio.CancelledError, Exception):
                pass
        self._tasks.clear()
        self._flush_timers.clear()
