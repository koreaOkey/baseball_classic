import hashlib
import json
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import delete, func, or_, select
from sqlalchemy.orm import Session

from .models import Game, GameBatterStat, GameEvent, GameLineupSlot, GameNote, GamePitcherStat, TeamRecord
from .schemas import (
    BaseStatus,
    CrawlerBatterStatIn,
    CrawlerEventIn,
    CrawlerGameNoteIn,
    CrawlerLineupSlotIn,
    CrawlerPitcherStatIn,
    CrawlerSnapshotRequest,
    CrawlerTeamRecordRequest,
    EventType,
    GameEventOut,
    GameStateOut,
    GameStatus,
    GameSummaryOut,
    TeamRecordOut,
)


STATUS_MAP = {
    "LIVE": GameStatus.LIVE,
    "PLAYING": GameStatus.LIVE,
    "IN_PROGRESS": GameStatus.LIVE,
    "STARTED": GameStatus.LIVE,
    "READY": GameStatus.SCHEDULED,
    "SCHEDULED": GameStatus.SCHEDULED,
    "PREGAME": GameStatus.SCHEDULED,
    "PRE_GAME": GameStatus.SCHEDULED,
    "END": GameStatus.FINISHED,
    "ENDED": GameStatus.FINISHED,
    "FINAL": GameStatus.FINISHED,
    "RESULT": GameStatus.FINISHED,
    "FINISHED": GameStatus.FINISHED,
    "CANCELED": GameStatus.CANCELED,
    "CANCELLED": GameStatus.CANCELED,
    "CANCEL": GameStatus.CANCELED,
    "RAIN_CANCEL": GameStatus.CANCELED,
    "NO_GAME": GameStatus.CANCELED,
    "POSTPONED": GameStatus.POSTPONED,
    "PPD": GameStatus.POSTPONED,
    "SUSPENDED": GameStatus.POSTPONED,
    "DELAYED": GameStatus.POSTPONED,
}

EVENT_MAP = {
    "BALL": EventType.BALL,
    "STRIKE": EventType.STRIKE,
    "WALK": EventType.WALK,
    "BASE_ON_BALLS": EventType.WALK,
    "INTENTIONAL_WALK": EventType.WALK,
    "OUT": EventType.OUT,
    "DOUBLE_PLAY": EventType.DOUBLE_PLAY,
    "DOUBLEPLAY": EventType.DOUBLE_PLAY,
    "DP": EventType.DOUBLE_PLAY,
    "TRIPLE_PLAY": EventType.TRIPLE_PLAY,
    "TRIPLEPLAY": EventType.TRIPLE_PLAY,
    "TP": EventType.TRIPLE_PLAY,
    "HALF_INNING_CHANGE": EventType.HALF_INNING_CHANGE,
    "HALFINNINGCHANGE": EventType.HALF_INNING_CHANGE,
    "OFFENSE_CHANGE": EventType.HALF_INNING_CHANGE,
    "HIT": EventType.HIT,
    "HOMERUN": EventType.HOMERUN,
    "HOME_RUN": EventType.HOMERUN,
    "SCORE": EventType.SCORE,
    "SAC_FLY_SCORE": EventType.SAC_FLY_SCORE,
    "SACRIFICE_FLY_SCORE": EventType.SAC_FLY_SCORE,
    "TAG_UP_ADVANCE": EventType.TAG_UP_ADVANCE,
    "TAG_UP": EventType.TAG_UP_ADVANCE,
    "STEAL": EventType.STEAL,
    "STOLEN_BASE": EventType.STEAL,
    "PITCHER_CHANGE": EventType.PITCHER_CHANGE,
    "PITCHING_CHANGE": EventType.PITCHER_CHANGE,
    "PITCHER_SUBSTITUTION": EventType.PITCHER_CHANGE,
}


def now_utc() -> datetime:
    return datetime.now(UTC)


def ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def normalize_status(raw: str) -> GameStatus:
    return STATUS_MAP.get(raw.strip().upper(), GameStatus.SCHEDULED)


STATUS_PROGRESS = {
    GameStatus.SCHEDULED: 0,
    GameStatus.LIVE: 1,
    GameStatus.POSTPONED: 2,
    GameStatus.CANCELED: 2,
    GameStatus.FINISHED: 3,
}


def _resolve_next_game_status(existing_raw: str | None, incoming: GameStatus) -> GameStatus:
    existing = normalize_status(existing_raw or "")
    if STATUS_PROGRESS[incoming] >= STATUS_PROGRESS[existing]:
        return incoming
    return existing


def normalize_event_type(raw: str) -> EventType:
    return EVENT_MAP.get(raw.strip().upper(), EventType.OTHER)


def _event_out_count(event_type: EventType, description: str) -> int:
    if event_type == EventType.TRIPLE_PLAY:
        return 3
    if event_type == EventType.DOUBLE_PLAY:
        return 2

    text = (description or "").lower()
    if "삼중살" in text or "triple play" in text:
        return 3
    if "병살" in text or "double play" in text:
        return 2
    if event_type in {EventType.OUT, EventType.SAC_FLY_SCORE, EventType.TAG_UP_ADVANCE}:
        return 1
    return 0


def _compute_event_summary(payload: CrawlerSnapshotRequest) -> dict[str, int]:
    summary = {
        "home_hits": 0,
        "away_hits": 0,
        "home_home_runs": 0,
        "away_home_runs": 0,
        "home_outs_total": 0,
        "away_outs_total": 0,
    }
    for event in payload.events:
        side: str | None = None
        half = (event.metadata or {}).get("half")
        if half in {"top", "TOP", "초"}:
            side = "away"
        elif half in {"bottom", "BOTTOM", "말"}:
            side = "home"

        if side is None:
            continue

        etype = normalize_event_type(event.type)
        if etype in {EventType.HIT, EventType.HOMERUN}:
            summary[f"{side}_hits"] += 1
        if etype == EventType.HOMERUN:
            summary[f"{side}_home_runs"] += 1
        summary[f"{side}_outs_total"] += _event_out_count(etype, event.description)

    return summary


def _normalize_start_time(raw: str | None) -> str | None:
    if raw is None:
        return None

    text = raw.strip()
    if len(text) < 5 or text[2] != ":":
        return None

    hh_raw = text[:2]
    mm_raw = text[3:5]
    if not (hh_raw.isdigit() and mm_raw.isdigit()):
        return None

    hh = int(hh_raw)
    mm = int(mm_raw)
    if not (0 <= hh <= 23 and 0 <= mm <= 59):
        return None

    return f"{hh:02d}:{mm:02d}"


def _normalize_game_date(raw: str | None) -> str | None:
    if raw is None:
        return None

    text = raw.strip()
    if len(text) != 10:
        return None
    if text[4] != "-" or text[7] != "-":
        return None

    try:
        parsed = datetime.strptime(text, "%Y-%m-%d")
    except ValueError:
        return None

    return parsed.date().isoformat()


def _game_date_from_game_id(game_id: str) -> str | None:
    if len(game_id) < 8:
        return None

    # Case 1) Standard format: YYYYMMDD...
    if game_id[:8].isdigit():
        raw = game_id[:8]
        try:
            parsed = datetime.strptime(raw, "%Y%m%d")
            if 2000 <= parsed.year <= 2100:
                return parsed.date().isoformat()
        except ValueError:
            pass

    # Case 2) WBC-like format: ####MMDD....YYYY (e.g. 88880308AUJP02026)
    # - month/day at positions [4:8]
    # - season year in trailing 4 digits
    mmdd = game_id[4:8] if len(game_id) >= 8 else ""
    tail_year = game_id[-4:] if len(game_id) >= 4 else ""
    if mmdd.isdigit() and tail_year.isdigit():
        month = int(mmdd[:2])
        day = int(mmdd[2:])
        year = int(tail_year)
        if 2000 <= year <= 2100:
            try:
                parsed = datetime(year=year, month=month, day=day)
                return parsed.date().isoformat()
            except ValueError:
                return None
    return None


def upsert_game_from_snapshot(db: Session, game_id: str, payload: CrawlerSnapshotRequest) -> Game:
    game = db.get(Game, game_id)
    if game is None:
        game = Game(id=game_id, home_team=payload.homeTeam, away_team=payload.awayTeam)
        db.add(game)

    incoming_status = normalize_status(payload.status)
    next_status = _resolve_next_game_status(game.status, incoming_status)

    game.home_team = payload.homeTeam
    game.away_team = payload.awayTeam
    game.status = next_status.value
    game.inning = payload.inning
    game.home_score = payload.homeScore
    game.away_score = payload.awayScore
    game.ball_count = payload.ball
    game.strike_count = payload.strike
    game.out_count = payload.out
    game.base_first = payload.bases.first
    game.base_second = payload.bases.second
    game.base_third = payload.bases.third
    game.pitcher = payload.pitcher
    game.batter = payload.batter
    normalized_game_date = _game_date_from_game_id(game_id)
    if normalized_game_date is None:
        normalized_game_date = _normalize_game_date(payload.gameDate)
    if normalized_game_date is not None:
        game.game_date = normalized_game_date
    start_time = _normalize_start_time(payload.startTime)
    if start_time is None and next_status == GameStatus.SCHEDULED:
        start_time = _normalize_start_time(payload.inning)
    if start_time is not None:
        game.start_time = start_time
    game.observed_at = ensure_utc(payload.observedAt) if payload.observedAt else game.observed_at

    computed = _compute_event_summary(payload) if payload.events else {}
    game.home_hits = payload.homeHits if payload.homeHits is not None else computed.get("home_hits", game.home_hits)
    game.away_hits = payload.awayHits if payload.awayHits is not None else computed.get("away_hits", game.away_hits)
    game.home_home_runs = (
        payload.homeHomeRuns if payload.homeHomeRuns is not None else computed.get("home_home_runs", game.home_home_runs)
    )
    game.away_home_runs = (
        payload.awayHomeRuns if payload.awayHomeRuns is not None else computed.get("away_home_runs", game.away_home_runs)
    )
    game.home_outs_total = (
        payload.homeOutsTotal if payload.homeOutsTotal is not None else computed.get("home_outs_total", game.home_outs_total)
    )
    game.away_outs_total = (
        payload.awayOutsTotal if payload.awayOutsTotal is not None else computed.get("away_outs_total", game.away_outs_total)
    )

    if payload.events:
        latest_event = max(payload.events, key=lambda item: ensure_utc(item.occurredAt))
        game.last_event_type = normalize_event_type(latest_event.type).value
        game.last_event_desc = latest_event.description
        game.last_event_at = ensure_utc(latest_event.occurredAt)

    game.updated_at = ensure_utc(payload.observedAt) if payload.observedAt else now_utc()
    db.flush()
    return game


def _metadata_player_name(metadata: dict[str, Any] | None, keys: tuple[str, ...]) -> str | None:
    if metadata is None:
        return None

    for key in keys:
        value = metadata.get(key)
        if isinstance(value, str):
            normalized = value.strip()
            if normalized:
                return normalized
        if isinstance(value, dict):
            for nested_key in ("name", "playerName", "player_name"):
                nested_value = value.get(nested_key)
                if isinstance(nested_value, str):
                    normalized = nested_value.strip()
                    if normalized:
                        return normalized
    return None


_INSERT_EVENTS_CHUNK_SIZE = 100


def _chunked_count(db: Session, game_id: str, source_ids: list[str]) -> int:
    total = 0
    for i in range(0, len(source_ids), _INSERT_EVENTS_CHUNK_SIZE):
        chunk = source_ids[i : i + _INSERT_EVENTS_CHUNK_SIZE]
        total += db.execute(
            select(func.count())
            .select_from(GameEvent)
            .where(GameEvent.game_id == game_id, GameEvent.source_event_id.in_(chunk))
        ).scalar_one()
    return total


def _chunked_needs_merge(db: Session, game_id: str, source_ids: list[str]) -> bool:
    """Check if any matching events need backfill (null pitcher/batter or OTHER type)."""
    for i in range(0, len(source_ids), _INSERT_EVENTS_CHUNK_SIZE):
        chunk = source_ids[i : i + _INSERT_EVENTS_CHUNK_SIZE]
        count = db.execute(
            select(func.count())
            .select_from(GameEvent)
            .where(
                GameEvent.game_id == game_id,
                GameEvent.source_event_id.in_(chunk),
                or_(
                    GameEvent.pitcher.is_(None),
                    GameEvent.batter.is_(None),
                    GameEvent.event_type == EventType.OTHER.value,
                ),
            )
        ).scalar_one()
        if count > 0:
            return True
    return False


def _chunked_load(db: Session, game_id: str, source_ids: list[str]) -> dict[str, GameEvent]:
    result: dict[str, GameEvent] = {}
    for i in range(0, len(source_ids), _INSERT_EVENTS_CHUNK_SIZE):
        chunk = source_ids[i : i + _INSERT_EVENTS_CHUNK_SIZE]
        rows = db.execute(
            select(GameEvent).where(GameEvent.game_id == game_id, GameEvent.source_event_id.in_(chunk))
        ).scalars().all()
        for row in rows:
            result[row.source_event_id] = row
    return result


def insert_events(
    db: Session,
    game_id: str,
    events: list[CrawlerEventIn],
    fallback_pitcher: str | None = None,
    fallback_batter: str | None = None,
) -> tuple[list[GameEvent], int]:
    if not events:
        return [], 0

    source_ids = [item.sourceEventId for item in events]
    unique_source_ids = list(dict.fromkeys(source_ids))

    # Fast path: chunked COUNT to detect all-duplicate case without loading full objects
    existing_count = _chunked_count(db, game_id, unique_source_ids)
    if existing_count >= len(unique_source_ids):
        # All events exist. Only skip full load if no backfill is needed.
        if not _chunked_needs_merge(db, game_id, unique_source_ids):
            return [], len(events)

    # Slow path: chunked full load for partial-duplicate / new-events case
    existing_by_source = _chunked_load(db, game_id, unique_source_ids)

    inserted: list[GameEvent] = []
    duplicate_count = 0
    seen_in_batch: set[str] = set()

    for event_in in sorted(events, key=lambda item: ensure_utc(item.occurredAt)):
        source_id = event_in.sourceEventId
        normalized_event_type = normalize_event_type(event_in.type).value
        event_pitcher = _metadata_player_name(
            event_in.metadata,
            ("pitcher", "pitcherName", "currentPitcher", "current_pitcher"),
        ) or fallback_pitcher
        event_batter = _metadata_player_name(
            event_in.metadata,
            ("batter", "batterName", "currentBatter", "current_batter"),
        ) or fallback_batter
        existing_event = existing_by_source.get(source_id)
        if existing_event is not None:
            duplicate_count += 1
            if existing_event.event_type == EventType.OTHER.value and normalized_event_type != EventType.OTHER.value:
                existing_event.event_type = normalized_event_type
            if event_in.description and existing_event.description != event_in.description:
                existing_event.description = event_in.description
            if existing_event.pitcher is None and event_pitcher is not None:
                existing_event.pitcher = event_pitcher
            if existing_event.batter is None and event_batter is not None:
                existing_event.batter = event_batter
            if event_in.metadata:
                existing_metadata = existing_event.payload_json if isinstance(existing_event.payload_json, dict) else {}
                merged_metadata = {**existing_metadata, **event_in.metadata}
                existing_event.payload_json = merged_metadata
            continue

        if source_id in seen_in_batch:
            duplicate_count += 1
            continue

        seen_in_batch.add(source_id)
        event = GameEvent(
            game_id=game_id,
            source_event_id=source_id,
            event_type=normalized_event_type,
            description=event_in.description,
            event_time=ensure_utc(event_in.occurredAt),
            pitcher=event_pitcher,
            batter=event_batter,
            haptic_pattern=event_in.hapticPattern,
            payload_json=event_in.metadata,
        )
        db.add(event)
        inserted.append(event)

    db.flush()
    return inserted, duplicate_count


def _source_event_cursor_map(db: Session, game_id: str, source_ids: set[str]) -> dict[str, int]:
    if not source_ids:
        return {}
    rows = db.execute(
        select(GameEvent.source_event_id, GameEvent.cursor).where(
            GameEvent.game_id == game_id,
            GameEvent.source_event_id.in_(source_ids),
        )
    ).all()
    return {source_id: cursor for source_id, cursor in rows}


def _resolve_team_context(game: Game | None, team_side: str) -> tuple[str | None, str | None, str | None, str | None]:
    if game is None:
        return None, None, None, None

    if team_side == "home":
        player_team = game.home_team
    elif team_side == "away":
        player_team = game.away_team
    else:
        player_team = None

    return player_team, game.game_date, game.home_team, game.away_team


def _lineup_from_snapshot(db: Session, game_id: str, lineup_slots: list[CrawlerLineupSlotIn]) -> None:
    db.execute(delete(GameLineupSlot).where(GameLineupSlot.game_id == game_id))
    if not lineup_slots:
        return

    game = db.get(Game, game_id)
    source_ids: set[str] = set()
    for slot in lineup_slots:
        if slot.enteredAtSourceEventId:
            source_ids.add(slot.enteredAtSourceEventId)
        if slot.exitedAtSourceEventId:
            source_ids.add(slot.exitedAtSourceEventId)
    source_map = _source_event_cursor_map(db, game_id, source_ids)

    dedup: dict[tuple[str, int], CrawlerLineupSlotIn] = {}
    for slot in lineup_slots:
        dedup[(slot.teamSide, slot.battingOrder)] = slot

    for slot in dedup.values():
        player_team, game_date, home_team, away_team = _resolve_team_context(game, slot.teamSide)
        db.add(
            GameLineupSlot(
                game_id=game_id,
                team_side=slot.teamSide,
                player_team=player_team,
                game_date=game_date,
                home_team=home_team,
                away_team=away_team,
                batting_order=slot.battingOrder,
                player_id=slot.playerId,
                player_name=slot.playerName,
                position_code=slot.positionCode,
                position_name=slot.positionName,
                is_starter=slot.isStarter,
                is_active=slot.isActive,
                entered_at_event_cursor=source_map.get(slot.enteredAtSourceEventId or ""),
                exited_at_event_cursor=source_map.get(slot.exitedAtSourceEventId or ""),
            )
        )


def _batter_stats_from_snapshot(db: Session, game_id: str, batter_stats: list[CrawlerBatterStatIn]) -> None:
    db.execute(delete(GameBatterStat).where(GameBatterStat.game_id == game_id))
    if not batter_stats:
        return

    game = db.get(Game, game_id)
    dedup: dict[tuple[str, str], CrawlerBatterStatIn] = {}
    for stat in batter_stats:
        dedup_key = stat.playerId or f"{stat.playerName}#{stat.battingOrder or 0}"
        dedup[(stat.teamSide, dedup_key)] = stat

    for stat in dedup.values():
        player_team, game_date, home_team, away_team = _resolve_team_context(game, stat.teamSide)
        db.add(
            GameBatterStat(
                game_id=game_id,
                team_side=stat.teamSide,
                player_team=player_team,
                game_date=game_date,
                home_team=home_team,
                away_team=away_team,
                player_id=stat.playerId,
                player_name=stat.playerName,
                batting_order=stat.battingOrder,
                primary_position=stat.primaryPosition,
                is_starter=stat.isStarter,
                plate_appearances=stat.plateAppearances,
                at_bats=stat.atBats,
                runs=stat.runs,
                hits=stat.hits,
                rbi=stat.rbi,
                doubles=stat.doubles,
                triples=stat.triples,
                home_runs=stat.homeRuns,
                walks=stat.walks,
                strikeouts=stat.strikeouts,
                stolen_bases=stat.stolenBases,
                caught_stealing=stat.caughtStealing,
                hit_by_pitch=stat.hitByPitch,
                sac_bunts=stat.sacBunts,
                sac_flies=stat.sacFlies,
                left_on_base=stat.leftOnBase,
            )
        )


def _pitcher_stats_from_snapshot(db: Session, game_id: str, pitcher_stats: list[CrawlerPitcherStatIn]) -> None:
    db.execute(delete(GamePitcherStat).where(GamePitcherStat.game_id == game_id))
    if not pitcher_stats:
        return

    game = db.get(Game, game_id)
    dedup: dict[tuple[str, str], CrawlerPitcherStatIn] = {}
    for stat in pitcher_stats:
        dedup_key = stat.playerId or f"{stat.playerName}#{stat.appearanceOrder or 0}"
        dedup[(stat.teamSide, dedup_key)] = stat

    for stat in dedup.values():
        player_team, game_date, home_team, away_team = _resolve_team_context(game, stat.teamSide)
        db.add(
            GamePitcherStat(
                game_id=game_id,
                team_side=stat.teamSide,
                player_team=player_team,
                game_date=game_date,
                home_team=home_team,
                away_team=away_team,
                appearance_order=stat.appearanceOrder,
                player_id=stat.playerId,
                player_name=stat.playerName,
                is_starter=stat.isStarter,
                outs_recorded=stat.outsRecorded,
                hits_allowed=stat.hitsAllowed,
                runs_allowed=stat.runsAllowed,
                earned_runs=stat.earnedRuns,
                walks_allowed=stat.walksAllowed,
                strikeouts=stat.strikeouts,
                home_runs_allowed=stat.homeRunsAllowed,
                batters_faced=stat.battersFaced,
                at_bats_against=stat.atBatsAgainst,
                pitches_thrown=stat.pitchesThrown,
            )
        )


def _notes_from_snapshot(db: Session, game_id: str, notes: list[CrawlerGameNoteIn]) -> None:
    db.execute(delete(GameNote).where(GameNote.game_id == game_id))
    if not notes:
        return

    source_ids = {note.sourceEventId for note in notes if note.sourceEventId}
    source_map = _source_event_cursor_map(db, game_id, source_ids)

    for note in notes:
        db.add(
            GameNote(
                game_id=game_id,
                team_side=note.teamSide,
                note_type=note.noteType,
                note_title=note.noteTitle,
                note_body=note.noteBody,
                inning=note.inning,
                event_cursor=source_map.get(note.sourceEventId or ""),
            )
        )


def _snapshot_block_hash(items: list[dict[str, Any]]) -> str:
    encoded = json.dumps(items, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _lineup_slots_payload_hash(db: Session, game_id: str, items: list[CrawlerLineupSlotIn]) -> str:
    source_ids: set[str] = set()
    for slot in items:
        if slot.enteredAtSourceEventId:
            source_ids.add(slot.enteredAtSourceEventId)
        if slot.exitedAtSourceEventId:
            source_ids.add(slot.exitedAtSourceEventId)
    source_map = _source_event_cursor_map(db, game_id, source_ids)
    normalized = [
        {
            "team_side": slot.teamSide,
            "batting_order": slot.battingOrder,
            "player_id": slot.playerId,
            "player_name": slot.playerName,
            "position_code": slot.positionCode,
            "position_name": slot.positionName,
            "is_starter": slot.isStarter,
            "is_active": slot.isActive,
            "entered_at_event_cursor": source_map.get(slot.enteredAtSourceEventId or ""),
            "exited_at_event_cursor": source_map.get(slot.exitedAtSourceEventId or ""),
        }
        for slot in sorted(
            items,
            key=lambda slot: (
                slot.teamSide,
                slot.battingOrder,
                slot.playerId or "",
                slot.playerName,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _lineup_slots_current_hash(db: Session, game_id: str) -> str:
    rows = db.execute(select(GameLineupSlot).where(GameLineupSlot.game_id == game_id)).scalars().all()
    normalized = [
        {
            "team_side": row.team_side,
            "batting_order": row.batting_order,
            "player_id": row.player_id,
            "player_name": row.player_name,
            "position_code": row.position_code,
            "position_name": row.position_name,
            "is_starter": row.is_starter,
            "is_active": row.is_active,
            "entered_at_event_cursor": row.entered_at_event_cursor,
            "exited_at_event_cursor": row.exited_at_event_cursor,
        }
        for row in sorted(
            rows,
            key=lambda row: (
                row.team_side,
                row.batting_order,
                row.player_id or "",
                row.player_name,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _batter_stats_payload_hash(items: list[CrawlerBatterStatIn]) -> str:
    normalized = [
        {
            "team_side": stat.teamSide,
            "player_id": stat.playerId,
            "player_name": stat.playerName,
            "batting_order": stat.battingOrder,
            "primary_position": stat.primaryPosition,
            "is_starter": stat.isStarter,
            "plate_appearances": stat.plateAppearances,
            "at_bats": stat.atBats,
            "runs": stat.runs,
            "hits": stat.hits,
            "rbi": stat.rbi,
            "doubles": stat.doubles,
            "triples": stat.triples,
            "home_runs": stat.homeRuns,
            "walks": stat.walks,
            "strikeouts": stat.strikeouts,
            "stolen_bases": stat.stolenBases,
            "caught_stealing": stat.caughtStealing,
            "hit_by_pitch": stat.hitByPitch,
            "sac_bunts": stat.sacBunts,
            "sac_flies": stat.sacFlies,
            "left_on_base": stat.leftOnBase,
        }
        for stat in sorted(
            items,
            key=lambda stat: (
                stat.teamSide,
                stat.playerId or "",
                stat.playerName,
                stat.battingOrder or 0,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _batter_stats_current_hash(db: Session, game_id: str) -> str:
    rows = db.execute(select(GameBatterStat).where(GameBatterStat.game_id == game_id)).scalars().all()
    normalized = [
        {
            "team_side": row.team_side,
            "player_id": row.player_id,
            "player_name": row.player_name,
            "batting_order": row.batting_order,
            "primary_position": row.primary_position,
            "is_starter": row.is_starter,
            "plate_appearances": row.plate_appearances,
            "at_bats": row.at_bats,
            "runs": row.runs,
            "hits": row.hits,
            "rbi": row.rbi,
            "doubles": row.doubles,
            "triples": row.triples,
            "home_runs": row.home_runs,
            "walks": row.walks,
            "strikeouts": row.strikeouts,
            "stolen_bases": row.stolen_bases,
            "caught_stealing": row.caught_stealing,
            "hit_by_pitch": row.hit_by_pitch,
            "sac_bunts": row.sac_bunts,
            "sac_flies": row.sac_flies,
            "left_on_base": row.left_on_base,
        }
        for row in sorted(
            rows,
            key=lambda row: (
                row.team_side,
                row.player_id or "",
                row.player_name,
                row.batting_order or 0,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _pitcher_stats_payload_hash(items: list[CrawlerPitcherStatIn]) -> str:
    normalized = [
        {
            "team_side": stat.teamSide,
            "appearance_order": stat.appearanceOrder,
            "player_id": stat.playerId,
            "player_name": stat.playerName,
            "is_starter": stat.isStarter,
            "outs_recorded": stat.outsRecorded,
            "hits_allowed": stat.hitsAllowed,
            "runs_allowed": stat.runsAllowed,
            "earned_runs": stat.earnedRuns,
            "walks_allowed": stat.walksAllowed,
            "strikeouts": stat.strikeouts,
            "home_runs_allowed": stat.homeRunsAllowed,
            "batters_faced": stat.battersFaced,
            "at_bats_against": stat.atBatsAgainst,
            "pitches_thrown": stat.pitchesThrown,
        }
        for stat in sorted(
            items,
            key=lambda stat: (
                stat.teamSide,
                stat.playerId or "",
                stat.playerName,
                stat.appearanceOrder or 0,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _pitcher_stats_current_hash(db: Session, game_id: str) -> str:
    rows = db.execute(select(GamePitcherStat).where(GamePitcherStat.game_id == game_id)).scalars().all()
    normalized = [
        {
            "team_side": row.team_side,
            "appearance_order": row.appearance_order,
            "player_id": row.player_id,
            "player_name": row.player_name,
            "is_starter": row.is_starter,
            "outs_recorded": row.outs_recorded,
            "hits_allowed": row.hits_allowed,
            "runs_allowed": row.runs_allowed,
            "earned_runs": row.earned_runs,
            "walks_allowed": row.walks_allowed,
            "strikeouts": row.strikeouts,
            "home_runs_allowed": row.home_runs_allowed,
            "batters_faced": row.batters_faced,
            "at_bats_against": row.at_bats_against,
            "pitches_thrown": row.pitches_thrown,
        }
        for row in sorted(
            rows,
            key=lambda row: (
                row.team_side,
                row.player_id or "",
                row.player_name,
                row.appearance_order or 0,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _notes_payload_hash(db: Session, game_id: str, items: list[CrawlerGameNoteIn]) -> str:
    source_ids = {note.sourceEventId for note in items if note.sourceEventId}
    source_map = _source_event_cursor_map(db, game_id, source_ids)
    normalized = [
        {
            "team_side": note.teamSide,
            "note_type": note.noteType,
            "note_title": note.noteTitle,
            "note_body": note.noteBody,
            "inning": note.inning,
            "event_cursor": source_map.get(note.sourceEventId or ""),
        }
        for note in sorted(
            items,
            key=lambda note: (
                note.teamSide or "",
                note.noteType,
                note.noteTitle,
                note.inning or "",
                note.sourceEventId or "",
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def _notes_current_hash(db: Session, game_id: str) -> str:
    rows = db.execute(select(GameNote).where(GameNote.game_id == game_id)).scalars().all()
    normalized = [
        {
            "team_side": row.team_side,
            "note_type": row.note_type,
            "note_title": row.note_title,
            "note_body": row.note_body,
            "inning": row.inning,
            "event_cursor": row.event_cursor,
        }
        for row in sorted(
            rows,
            key=lambda row: (
                row.team_side or "",
                row.note_type,
                row.note_title,
                row.inning or "",
                row.event_cursor or 0,
            ),
        )
    ]
    return _snapshot_block_hash(normalized)


def sync_snapshot_details(db: Session, game_id: str, payload: CrawlerSnapshotRequest) -> None:
    if db.get(Game, game_id) is None:
        return

    changed = False

    if payload.lineupSlots:
        next_hash = _lineup_slots_payload_hash(db, game_id, payload.lineupSlots)
        current_hash = _lineup_slots_current_hash(db, game_id)
        if next_hash != current_hash:
            # Break FK dependency first: batter stats reference lineup slots by (game_id, team_side, batting_order).
            db.execute(delete(GameBatterStat).where(GameBatterStat.game_id == game_id))
            _lineup_from_snapshot(db, game_id, payload.lineupSlots)
            # Ensure lineup rows exist before inserting batter stats that reference them.
            db.flush()
            changed = True

    if payload.batterStats:
        next_hash = _batter_stats_payload_hash(payload.batterStats)
        current_hash = _batter_stats_current_hash(db, game_id)
        if next_hash != current_hash:
            _batter_stats_from_snapshot(db, game_id, payload.batterStats)
            changed = True

    if payload.pitcherStats:
        next_hash = _pitcher_stats_payload_hash(payload.pitcherStats)
        current_hash = _pitcher_stats_current_hash(db, game_id)
        if next_hash != current_hash:
            _pitcher_stats_from_snapshot(db, game_id, payload.pitcherStats)
            changed = True

    if payload.notes:
        next_hash = _notes_payload_hash(db, game_id, payload.notes)
        current_hash = _notes_current_hash(db, game_id)
        if next_hash != current_hash:
            _notes_from_snapshot(db, game_id, payload.notes)
            changed = True

    if changed:
        db.flush()


@dataclass
class TeamRecordUpsertResult:
    upserted_records: int
    changed_records: list[TeamRecord]


def upsert_team_records(db: Session, payload: CrawlerTeamRecordRequest) -> TeamRecordUpsertResult:
    if not payload.records:
        return TeamRecordUpsertResult(upserted_records=0, changed_records=[])

    dedup: dict[str, Any] = {}
    for record in payload.records:
        dedup[record.teamId] = record

    team_ids = list(dedup.keys())
    existing_rows = db.execute(
        select(TeamRecord).where(
            TeamRecord.category_id == payload.categoryId,
            TeamRecord.season_code == payload.seasonCode,
            TeamRecord.team_id.in_(team_ids),
        )
    ).scalars().all()
    existing_by_team_id = {row.team_id: row for row in existing_rows}

    upserted = 0
    changed_records: list[TeamRecord] = []
    default_observed_at = ensure_utc(payload.observedAt) if payload.observedAt else now_utc()
    for record in dedup.values():
        next_payload_json = (
            record.raw
            if record.raw is not None
            else record.model_dump(mode="json", exclude={"raw"}, exclude_none=True)
        )
        next_observed_at = ensure_utc(record.observedAt) if record.observedAt else default_observed_at

        row = existing_by_team_id.get(record.teamId)
        if row is None:
            row = TeamRecord(
                category_id=payload.categoryId,
                season_code=payload.seasonCode,
                team_id=record.teamId,
            )
            db.add(row)
        else:
            has_meaningful_change = any(
                (
                    row.upper_category_id != (record.upperCategoryId or payload.upperCategoryId),
                    row.team_name != record.teamName,
                    row.team_short_name != record.teamShortName,
                    row.ranking != record.ranking,
                    row.order_no != record.orderNo,
                    row.game_type != record.gameType,
                    row.wra != record.wra,
                    row.game_count != record.gameCount,
                    row.win_game_count != record.winGameCount,
                    row.drawn_game_count != record.drawnGameCount,
                    row.lose_game_count != record.loseGameCount,
                    row.game_behind != record.gameBehind,
                    row.continuous_game_result != record.continuousGameResult,
                    row.last_five_games != record.lastFiveGames,
                    row.offense_hra != record.offenseHra,
                    row.defense_era != record.defenseEra,
                    row.payload_json != next_payload_json,
                )
            )
            if not has_meaningful_change:
                continue

        row.upper_category_id = record.upperCategoryId or payload.upperCategoryId
        row.team_name = record.teamName
        row.team_short_name = record.teamShortName
        row.ranking = record.ranking
        row.order_no = record.orderNo
        row.game_type = record.gameType
        row.wra = record.wra
        row.game_count = record.gameCount
        row.win_game_count = record.winGameCount
        row.drawn_game_count = record.drawnGameCount
        row.lose_game_count = record.loseGameCount
        row.game_behind = record.gameBehind
        row.continuous_game_result = record.continuousGameResult
        row.last_five_games = record.lastFiveGames
        row.offense_hra = record.offenseHra
        row.defense_era = record.defenseEra
        row.payload_json = next_payload_json
        row.observed_at = next_observed_at
        row.updated_at = now_utc()
        upserted += 1
        changed_records.append(row)

    db.flush()
    return TeamRecordUpsertResult(upserted_records=upserted, changed_records=changed_records)


def get_team_record(
    db: Session,
    *,
    category_id: str,
    season_code: str,
    team_id: str,
) -> TeamRecord | None:
    return db.execute(
        select(TeamRecord).where(
            TeamRecord.category_id == category_id,
            TeamRecord.season_code == season_code,
            TeamRecord.team_id == team_id,
        )
    ).scalar_one_or_none()


def to_team_record_out(record: TeamRecord) -> TeamRecordOut:
    return TeamRecordOut(
        upperCategoryId=record.upper_category_id,
        categoryId=record.category_id,
        seasonCode=record.season_code,
        teamId=record.team_id,
        teamName=record.team_name,
        teamShortName=record.team_short_name,
        ranking=record.ranking,
        wra=record.wra,
        gameCount=record.game_count,
        winGameCount=record.win_game_count,
        drawnGameCount=record.drawn_game_count,
        loseGameCount=record.lose_game_count,
        gameBehind=record.game_behind,
        continuousGameResult=record.continuous_game_result,
        lastFiveGames=record.last_five_games,
        offenseHra=record.offense_hra,
        defenseEra=record.defense_era,
        observedAt=record.observed_at,
        updatedAt=record.updated_at,
    )


def to_game_summary(game: Game) -> GameSummaryOut:
    return GameSummaryOut(
        id=game.id,
        homeTeam=game.home_team,
        awayTeam=game.away_team,
        homeScore=game.home_score,
        awayScore=game.away_score,
        inning=game.inning,
        status=normalize_status(game.status),
        startTime=game.start_time,
        observedAt=game.observed_at,
        updatedAt=game.updated_at,
    )


def to_event_out(event: GameEvent) -> GameEventOut:
    return GameEventOut(
        cursor=event.cursor,
        id=event.source_event_id,
        type=normalize_event_type(event.event_type),
        description=event.description,
        time=event.event_time,
        pitcher=event.pitcher,
        batter=event.batter,
        hapticPattern=event.haptic_pattern,
    )


def _normalize_bso_for_state(ball: int, strike: int, out: int) -> tuple[int, int, int]:
    if out >= 3:
        return 0, 0, 0
    return ball, strike, out


def build_game_state(db: Session, game: Game) -> GameStateOut:
    latest_event = db.execute(
        select(GameEvent).where(GameEvent.game_id == game.id).order_by(GameEvent.cursor.desc()).limit(1)
    ).scalar_one_or_none()
    ball, strike, out = _normalize_bso_for_state(game.ball_count, game.strike_count, game.out_count)

    return GameStateOut(
        gameId=game.id,
        homeTeam=game.home_team,
        awayTeam=game.away_team,
        homeScore=game.home_score,
        awayScore=game.away_score,
        inning=game.inning,
        status=normalize_status(game.status),
        ball=ball,
        strike=strike,
        out=out,
        bases=BaseStatus(first=game.base_first, second=game.base_second, third=game.base_third),
        pitcher=game.pitcher,
        batter=game.batter,
        lastEventType=normalize_event_type(latest_event.event_type) if latest_event else None,
        lastEventAt=latest_event.event_time if latest_event else None,
        updatedAt=game.updated_at,
    )
