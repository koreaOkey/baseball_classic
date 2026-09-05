"""APNs (Apple Push Notification service) HTTP/2 전송 모듈"""
import asyncio
import base64
import json
import logging
import time
from typing import Any

import httpx
import jwt

from .config import get_settings

logger = logging.getLogger(__name__)

APNS_PRODUCTION_URL = "https://api.push.apple.com"
APNS_SANDBOX_URL = "https://api.sandbox.push.apple.com"

# 토큰이 더 이상 유효하지 않아 재시도해도 소용없는 영구 실패 reason.
# 호출부에서 해당 토큰을 DB 에서 정리(prune)하는 근거로 쓴다.
PERMANENT_FAILURE_REASONS = frozenset({"Unregistered", "BadDeviceToken"})


def _is_permanent_failure(status_code: int, reason: str) -> bool:
    return status_code == 410 or reason in PERMANENT_FAILURE_REASONS

# JWT 토큰 캐싱 (최대 50분, APNs는 1시간 만료)
_cached_jwt: str | None = None
_cached_jwt_expires: float = 0
JWT_LIFETIME_SECONDS = 50 * 60
# JWT 생성 실패(잘못된 .p8/환경변수)는 매 발송마다 반복되므로 traceback 은 스로틀해 남긴다.
_jwt_failure_logged_at: float = 0.0
_JWT_FAILURE_LOG_INTERVAL_SEC = 60.0


def _log_jwt_failure(reason: str) -> None:
    global _jwt_failure_logged_at
    now = time.time()
    if now - _jwt_failure_logged_at < _JWT_FAILURE_LOG_INTERVAL_SEC:
        return
    _jwt_failure_logged_at = now
    logger.exception("[APNs] JWT creation failed: %s", reason)


# 성공 발송은 개별 로그를 남기지 않아 "APNs 가 동작 중인지" 를 로그로 알 수 없었다. 60초 단위로
# 성공/실패 건수를 집계해 남긴다 (발송이 있었던 구간에만).
_stats_logger = logging.getLogger("app.apns.stats")
_stats_logger.setLevel(logging.INFO)
_SEND_STATS_LOG_INTERVAL_SEC = 60.0
_send_stats: dict[str, int] = {"ok": 0, "failed": 0, "permanent": 0}
_send_stats_window_started_at: float = 0.0


def _record_send_result(ok: bool, permanent: bool) -> None:
    global _send_stats_window_started_at
    now = time.time()
    if _send_stats_window_started_at == 0.0:
        _send_stats_window_started_at = now
    if ok:
        _send_stats["ok"] += 1
    else:
        _send_stats["failed"] += 1
        if permanent:
            _send_stats["permanent"] += 1
    if now - _send_stats_window_started_at >= _SEND_STATS_LOG_INTERVAL_SEC:
        _stats_logger.info(
            "[APNs-stats] ok=%d failed=%d permanent=%d window_sec=%d",
            _send_stats["ok"], _send_stats["failed"], _send_stats["permanent"],
            int(now - _send_stats_window_started_at),
        )
        _send_stats.update(ok=0, failed=0, permanent=0)
        _send_stats_window_started_at = now


def log_send_exceptions(label: str, results: list[Any]) -> None:
    """gather(return_exceptions=True) 결과 중 예외를 요약 로그로 남긴다.

    발송 코루틴이 HTTP 호출 전에 예외를 내면(예: JWT 생성 실패) 결과가 조용히 버려져
    `sent=0/N` 만 남고 원인이 보이지 않았다(2026-09-05 발견). 첫 예외의 타입·메시지를 남긴다.
    """
    errors = [result for result in results if isinstance(result, BaseException)]
    if not errors:
        return
    first = errors[0]
    logger.warning(
        "[%s] %d/%d sends raised %s: %s",
        label, len(errors), len(results), type(first).__name__, first,
    )

# HTTP/2 keep-alive 커넥션 재사용 전역 클라이언트.
# 요청마다 새로 만들면 TLS handshake 비용이 누적돼 워치 푸시 지연으로 이어진다.
_http_client: httpx.AsyncClient | None = None


def _get_http_client() -> httpx.AsyncClient:
    global _http_client
    if _http_client is None:
        try:
            _http_client = httpx.AsyncClient(
                http2=True,
                timeout=httpx.Timeout(10.0),
                limits=httpx.Limits(max_keepalive_connections=50, max_connections=200),
            )
        except Exception:
            logger.exception("[APNs] HTTP/2 client init failed (is the h2 package installed?)")
            raise
    return _http_client


def _get_apns_key() -> str | None:
    """환경 변수에서 .p8 키 내용을 base64 디코딩하여 반환.

    2026-09-05 프로덕션에서 `binascii.Error: Incorrect padding` 으로 모든 APNs 발송이 조용히
    실패하고 있었다. 대시보드 복사·붙여넣기 과정에서 흔히 생기는 변형(끝의 `=` 누락, 76열
    줄바꿈, 따옴표, base64 대신 .p8 원문)을 여기서 복원한다.
    """
    settings = get_settings()
    raw = (settings.apns_key_base64 or "").strip().strip('"').strip("'").strip()
    if not raw:
        return None
    if "-----BEGIN" in raw:
        # base64 가 아니라 .p8 원문이 들어온 경우: 리터럴 \n 을 줄바꿈으로 되돌려 그대로 사용
        return raw.replace("\\n", "\n").rstrip() + "\n"
    compact = "".join(raw.split())
    padded = compact + "=" * (-len(compact) % 4)
    return base64.b64decode(padded).decode("utf-8")


def _create_jwt_token() -> str | None:
    """APNs 인증용 JWT 토큰 생성 (ES256)"""
    global _cached_jwt, _cached_jwt_expires

    now = time.time()
    if _cached_jwt and now < _cached_jwt_expires:
        return _cached_jwt

    settings = get_settings()
    try:
        key_content = _get_apns_key()
    except Exception:
        _log_jwt_failure("APNS_KEY_BASE64 decode failed")
        return None
    if not key_content or not settings.apns_key_id or not settings.apns_team_id:
        logger.warning("[APNs] Missing APNs configuration (key, key_id, or team_id)")
        return None

    try:
        token = jwt.encode(
            {"iss": settings.apns_team_id, "iat": int(now)},
            key_content,
            algorithm="ES256",
            headers={"kid": settings.apns_key_id},
        )
    except Exception:
        _log_jwt_failure("ES256 signing failed (check APNS_KEY_BASE64 is a valid .p8 key)")
        return None

    _cached_jwt = token
    _cached_jwt_expires = now + JWT_LIFETIME_SECONDS
    return token


async def send_push(
    device_token: str,
    payload: dict[str, Any],
    *,
    use_sandbox: bool | None = None,
    platform: str = "ios",
) -> bool:
    """단일 디바이스에 silent push 전송"""
    ok, _ = await send_push_with_result(
        device_token, payload, use_sandbox=use_sandbox, platform=platform,
    )
    return ok


async def send_push_with_result(
    device_token: str,
    payload: dict[str, Any],
    *,
    use_sandbox: bool | None = None,
    platform: str = "ios",
) -> tuple[bool, bool]:
    """단일 디바이스에 silent push 전송. (성공 여부, 영구 실패 여부) 반환."""
    ok, permanent = await _send_push_with_result(
        device_token, payload, use_sandbox=use_sandbox, platform=platform,
    )
    _record_send_result(ok, permanent)
    return ok, permanent


async def _send_push_with_result(
    device_token: str,
    payload: dict[str, Any],
    *,
    use_sandbox: bool | None = None,
    platform: str = "ios",
) -> tuple[bool, bool]:
    settings = get_settings()
    jwt_token = _create_jwt_token()
    if jwt_token is None:
        return False, False

    sandbox = use_sandbox if use_sandbox is not None else settings.apns_use_sandbox

    # watchOS는 별도 번들 ID 사용
    topic = f"{settings.apns_bundle_id}.watchkitapp" if platform == "watchos" else settings.apns_bundle_id

    headers = {
        "authorization": f"bearer {jwt_token}",
        "apns-topic": topic,
        "apns-push-type": "background",
        "apns-priority": "5",
    }

    apns_payload = {
        "aps": {"content-available": 1},
        **payload,
    }

    client = _get_http_client()

    # 먼저 지정된 환경으로 시도, 실패 시 반대 환경으로 폴백
    environments = [sandbox, not sandbox]
    for try_sandbox in environments:
        base_url = APNS_SANDBOX_URL if try_sandbox else APNS_PRODUCTION_URL
        url = f"{base_url}/3/device/{device_token}"

        try:
            response = await client.post(
                url,
                content=json.dumps(apns_payload),
                headers={**headers, "content-type": "application/json"},
            )

            if response.status_code == 200:
                # 환경이 바뀌었으면 DB 업데이트를 위해 로그
                if try_sandbox != sandbox:
                    logger.info("[APNs] Push succeeded with fallback env: sandbox=%s token=%s...", try_sandbox, device_token[:16])
                return True, False

            body = response.text
            reason = ""
            try:
                reason = json.loads(body).get("reason", "")
            except Exception:
                pass

            # 환경 불일치면 반대 환경으로 재시도
            if reason in ("BadEnvironmentKeyInToken",) and try_sandbox == sandbox:
                logger.info("[APNs] Environment mismatch, retrying with sandbox=%s token=%s...", not sandbox, device_token[:16])
                continue

            logger.warning("[APNs] Push failed: status=%s body=%s token=%s... sandbox=%s topic=%s", response.status_code, body, device_token[:16], try_sandbox, topic)
            return False, _is_permanent_failure(response.status_code, reason)

        except Exception:
            logger.exception("[APNs] Push request error: token=%s...", device_token[:16])
            return False, False

    return False, False


# stale-date 미포함 push 는 스로틀링으로 업데이트가 끊겨도 카드가 낡은 데이터를
# 살아있는 것처럼 계속 보여준다. heartbeat(60s) 2회 유실까지는 흡수하는 여유폭.
LIVE_ACTIVITY_STALE_SECONDS = 180


async def send_live_activity_push(
    push_token: str,
    content_state: dict[str, Any],
    *,
    event_type: str = "update",  # "update" or "end"
    timestamp: int | None = None,
    priority: int = 10,
    stale_seconds: int | None = LIVE_ACTIVITY_STALE_SECONDS,
) -> bool:
    """ActivityKit Live Activity push 전송"""
    ok, _ = await send_live_activity_push_with_result(
        push_token, content_state, event_type=event_type, timestamp=timestamp,
        priority=priority, stale_seconds=stale_seconds,
    )
    return ok


async def send_live_activity_push_with_result(
    push_token: str,
    content_state: dict[str, Any],
    *,
    event_type: str = "update",  # "update" or "end"
    timestamp: int | None = None,
    priority: int = 10,
    stale_seconds: int | None = LIVE_ACTIVITY_STALE_SECONDS,
) -> tuple[bool, bool]:
    """ActivityKit Live Activity push 전송. (성공 여부, 영구 실패 여부) 반환."""
    ok, permanent = await _send_live_activity_push_with_result(
        push_token, content_state, event_type=event_type, timestamp=timestamp,
        priority=priority, stale_seconds=stale_seconds,
    )
    _record_send_result(ok, permanent)
    return ok, permanent


async def _send_live_activity_push_with_result(
    push_token: str,
    content_state: dict[str, Any],
    *,
    event_type: str = "update",  # "update" or "end"
    timestamp: int | None = None,
    priority: int = 10,
    stale_seconds: int | None = LIVE_ACTIVITY_STALE_SECONDS,
) -> tuple[bool, bool]:
    """ActivityKit Live Activity push 전송 본체.

    priority 10 은 기기별 Live Activity 업데이트 budget 을 소모한다
    (frequent-updates 미지원 기기는 초과 시 조용히 드롭). priority 5 는 budget 을
    안 쓰지만 잠금 상태 전달이 지연/유실됨이 실기기에서 확인돼(2026-08-18) 발송
    단은 코얼레싱으로 볼륨을 줄이고 전부 10 으로 보낸다. 파라미터는 향후
    토큰별 차등 발송용으로 유지.
    """
    settings = get_settings()
    jwt_token = _create_jwt_token()
    if jwt_token is None:
        return False, False

    base_url = APNS_SANDBOX_URL if settings.apns_use_sandbox else APNS_PRODUCTION_URL
    url = f"{base_url}/3/device/{push_token}"

    ts = timestamp or int(time.time())

    headers = {
        "authorization": f"bearer {jwt_token}",
        "apns-topic": f"{settings.apns_bundle_id}.push-type.liveactivity",
        "apns-push-type": "liveactivity",
        "apns-priority": str(priority),
    }

    aps: dict[str, Any] = {
        "timestamp": ts,
        "event": event_type,
        "content-state": content_state,
    }
    if stale_seconds is not None:
        aps["stale-date"] = ts + stale_seconds

    apns_payload = {"aps": aps}

    client = _get_http_client()

    try:
        response = await client.post(
            url,
            content=json.dumps(apns_payload),
            headers={**headers, "content-type": "application/json"},
        )

        if response.status_code == 200:
            return True, False

        reason = ""
        try:
            reason = json.loads(response.text).get("reason", "")
        except Exception:
            pass
        logger.warning(
            "[APNs-LA] Push failed: status=%s body=%s token=%s...",
            response.status_code, response.text, push_token[:16],
        )
        return False, _is_permanent_failure(response.status_code, reason)

    except Exception:
        logger.exception("[APNs-LA] Push request error: token=%s...", push_token[:16])
        return False, False


async def send_push_to_tokens(
    tokens: list[str],
    payload: dict[str, Any],
) -> list[str]:
    """여러 디바이스에 push 전송 (병렬), 실패한 토큰 목록 반환"""
    if not tokens:
        return []
    results = await asyncio.gather(
        *(send_push(token, payload) for token in tokens),
        return_exceptions=True,
    )
    log_send_exceptions("APNs-batch", results)
    failed_tokens: list[str] = []
    for token, result in zip(tokens, results):
        if isinstance(result, BaseException) or result is False:
            failed_tokens.append(token)
    return failed_tokens


async def send_visible_push(
    device_token: str,
    *,
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    use_sandbox: bool | None = None,
    category: str | None = None,
) -> bool:
    """단일 iOS 디바이스에 visible(alert) push 전송.

    silent push (send_push) 와 달리 알림 센터에 표시되며 탭 시 앱이 열린다.
    `category` 를 지정하면 클라이언트에 등록된 UNNotificationCategory 의 액션
    버튼이 알림에 표시된다.
    """
    ok, _ = await send_visible_push_with_result(
        device_token,
        title=title,
        body=body,
        data=data,
        use_sandbox=use_sandbox,
        category=category,
    )
    return ok


async def send_visible_push_with_result(
    device_token: str,
    *,
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    use_sandbox: bool | None = None,
    category: str | None = None,
) -> tuple[bool, bool]:
    """단일 iOS 디바이스에 visible push 전송. (성공 여부, 영구 실패 여부) 반환."""
    ok, permanent = await _send_visible_push_with_result(
        device_token, title=title, body=body, data=data,
        use_sandbox=use_sandbox, category=category,
    )
    _record_send_result(ok, permanent)
    return ok, permanent


async def _send_visible_push_with_result(
    device_token: str,
    *,
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    use_sandbox: bool | None = None,
    category: str | None = None,
) -> tuple[bool, bool]:
    settings = get_settings()
    jwt_token = _create_jwt_token()
    if jwt_token is None:
        return False, False

    sandbox = use_sandbox if use_sandbox is not None else settings.apns_use_sandbox
    headers = {
        "authorization": f"bearer {jwt_token}",
        "apns-topic": settings.apns_bundle_id,
        "apns-push-type": "alert",
        "apns-priority": "10",
    }
    aps: dict[str, Any] = {
        "alert": {"title": title, "body": body},
        "sound": "default",
    }
    if category:
        aps["category"] = category
    apns_payload: dict[str, Any] = {"aps": aps}
    if data:
        apns_payload.update({k: v for k, v in data.items() if k != "aps"})

    client = _get_http_client()
    environments = [sandbox, not sandbox]
    for try_sandbox in environments:
        base_url = APNS_SANDBOX_URL if try_sandbox else APNS_PRODUCTION_URL
        url = f"{base_url}/3/device/{device_token}"
        try:
            response = await client.post(
                url,
                content=json.dumps(apns_payload),
                headers={**headers, "content-type": "application/json"},
            )
            if response.status_code == 200:
                return True, False

            reason = ""
            try:
                reason = json.loads(response.text).get("reason", "")
            except Exception:
                pass
            if reason == "BadEnvironmentKeyInToken" and try_sandbox == sandbox:
                continue
            logger.warning(
                "[APNs-visible] send failed status=%s body=%s token=%s... sandbox=%s",
                response.status_code, response.text, device_token[:16], try_sandbox,
            )
            return False, _is_permanent_failure(response.status_code, reason)
        except Exception:
            logger.exception("[APNs-visible] request error token=%s...", device_token[:16])
            return False, False

    return False, False


async def send_visible_push_to_tokens_detailed(
    tokens_with_sandbox: list[tuple[str, bool]],
    *,
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    category: str | None = None,
) -> tuple[list[str], list[str]]:
    """여러 iOS 디바이스에 visible push 병렬 전송.

    (실패 토큰, 영구 실패 토큰) 을 반환한다. 영구 실패(410 Unregistered /
    BadDeviceToken) 토큰은 호출부에서 DB 정리(prune) 대상으로 쓴다.
    """
    if not tokens_with_sandbox:
        return [], []
    results = await asyncio.gather(
        *(
            send_visible_push_with_result(
                token,
                title=title,
                body=body,
                data=data,
                use_sandbox=is_sandbox,
                category=category,
            )
            for token, is_sandbox in tokens_with_sandbox
        ),
        return_exceptions=True,
    )
    log_send_exceptions("APNs-visible-batch", results)
    failed: list[str] = []
    permanently_failed: list[str] = []
    for (token, _), result in zip(tokens_with_sandbox, results):
        if isinstance(result, BaseException):
            failed.append(token)
            continue
        ok, permanent = result
        if not ok:
            failed.append(token)
            if permanent:
                permanently_failed.append(token)
    return failed, permanently_failed


async def send_visible_push_to_tokens(
    tokens_with_sandbox: list[tuple[str, bool]],
    *,
    title: str,
    body: str,
    data: dict[str, Any] | None = None,
    category: str | None = None,
) -> list[str]:
    """여러 iOS 디바이스에 visible push 병렬 전송. 실패 토큰 반환."""
    failed, _ = await send_visible_push_to_tokens_detailed(
        tokens_with_sandbox, title=title, body=body, data=data, category=category,
    )
    return failed
