from datetime import UTC, datetime

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from .models import Game, GameBatterStat, GameEvent, GameLineupSlot, GameNote, GamePitcherStat
from .schemas import (
    BaseStatus,
    CrawlerBatterStatIn,
    CrawlerEventIn,
    CrawlerGameNoteIn,
    CrawlerLineupSlotIn,
    CrawlerPitcherStatIn,
    CrawlerSnapshotRequest,
    EventType,
    GameEventOut,
    GameStateOut,
    GameStatus,
    GameSummaryOut,
)


STATUS_MAP = {
    "LIVE": GameStatus.LIVE,
    "PLAYING": GameStatus.LIVE,
    "IN_PROGRESS": GameStatus.LIVE,
    "READY": GameStatus.SCHEDULED,
    "SCHEDULED": GameStatus.SCHEDULED,
    "PREGAME": GameStatus.SCHEDULED,
    "END": GameStatus.FINISHED,
    "FINAL": GameStatus.FINISHED,
    "RESULT": GameStatus.FINISHED,
    "FINISHED": GameStatus.FINISHED,
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
}


def now_utc() -> datetime:
    return datetime.now(UTC)


def ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def normalize_status(raw: str) -> GameStatus:
    return STATUS_MAP.get(raw.strip().upper(), GameStatus.SCHEDULED)


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


def upsert_game_from_snapshot(db: Session, game_id: str, payload: CrawlerSnapshotRequest) -> Game:
    game = db.get(Game, game_id)
    if game is None:
        game = Game(id=game_id, home_team=payload.homeTeam, away_team=payload.awayTeam)
        db.add(game)

    game.home_team = payload.homeTeam
    game.away_team = payload.awayTeam
    game.status = normalize_status(payload.status).value
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


def insert_events(
    db: Session,
    game_id: str,
    events: list[CrawlerEventIn],
) -> tuple[list[GameEvent], int]:
    if not events:
        return [], 0

    source_ids = [item.sourceEventId for item in events]
    existing_source_ids = set(
        db.execute(
            select(GameEvent.source_event_id).where(
                GameEvent.game_id == game_id,
                GameEvent.source_event_id.in_(source_ids),
            )
        ).scalars()
    )

    inserted: list[GameEvent] = []
    duplicate_count = 0
    seen_in_batch: set[str] = set()

    for event_in in sorted(events, key=lambda item: ensure_utc(item.occurredAt)):
        source_id = event_in.sourceEventId
        if source_id in existing_source_ids or source_id in seen_in_batch:
            duplicate_count += 1
            continue

        seen_in_batch.add(source_id)
        event = GameEvent(
            game_id=game_id,
            source_event_id=source_id,
            event_type=normalize_event_type(event_in.type).value,
            description=event_in.description,
            event_time=ensure_utc(event_in.occurredAt),
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


def _lineup_from_snapshot(db: Session, game_id: str, lineup_slots: list[CrawlerLineupSlotIn]) -> None:
    db.execute(delete(GameLineupSlot).where(GameLineupSlot.game_id == game_id))
    if not lineup_slots:
        return

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
        db.add(
            GameLineupSlot(
                game_id=game_id,
                team_side=slot.teamSide,
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

    dedup: dict[tuple[str, str], CrawlerBatterStatIn] = {}
    for stat in batter_stats:
        dedup_key = stat.playerId or f"{stat.playerName}#{stat.battingOrder or 0}"
        dedup[(stat.teamSide, dedup_key)] = stat

    for stat in dedup.values():
        db.add(
            GameBatterStat(
                game_id=game_id,
                team_side=stat.teamSide,
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

    dedup: dict[tuple[str, str], CrawlerPitcherStatIn] = {}
    for stat in pitcher_stats:
        dedup_key = stat.playerId or f"{stat.playerName}#{stat.appearanceOrder or 0}"
        dedup[(stat.teamSide, dedup_key)] = stat

    for stat in dedup.values():
        db.add(
            GamePitcherStat(
                game_id=game_id,
                team_side=stat.teamSide,
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


def sync_snapshot_details(db: Session, game_id: str, payload: CrawlerSnapshotRequest) -> None:
    if payload.lineupSlots:
        _lineup_from_snapshot(db, game_id, payload.lineupSlots)
    if payload.batterStats:
        _batter_stats_from_snapshot(db, game_id, payload.batterStats)
    if payload.pitcherStats:
        _pitcher_stats_from_snapshot(db, game_id, payload.pitcherStats)
    if payload.notes:
        _notes_from_snapshot(db, game_id, payload.notes)
    db.flush()


def to_game_summary(game: Game) -> GameSummaryOut:
    return GameSummaryOut(
        id=game.id,
        homeTeam=game.home_team,
        awayTeam=game.away_team,
        homeScore=game.home_score,
        awayScore=game.away_score,
        inning=game.inning,
        status=normalize_status(game.status),
        updatedAt=game.updated_at,
    )


def to_event_out(event: GameEvent) -> GameEventOut:
    return GameEventOut(
        cursor=event.cursor,
        id=event.source_event_id,
        type=normalize_event_type(event.event_type),
        description=event.description,
        time=event.event_time,
        hapticPattern=event.haptic_pattern,
    )


def build_game_state(db: Session, game: Game) -> GameStateOut:
    latest_event = db.execute(
        select(GameEvent).where(GameEvent.game_id == game.id).order_by(GameEvent.cursor.desc()).limit(1)
    ).scalar_one_or_none()

    return GameStateOut(
        gameId=game.id,
        homeTeam=game.home_team,
        awayTeam=game.away_team,
        homeScore=game.home_score,
        awayScore=game.away_score,
        inning=game.inning,
        status=normalize_status(game.status),
        ball=game.ball_count,
        strike=game.strike_count,
        out=game.out_count,
        bases=BaseStatus(first=game.base_first, second=game.base_second, third=game.base_third),
        pitcher=game.pitcher,
        batter=game.batter,
        lastEventType=normalize_event_type(latest_event.event_type) if latest_event else None,
        lastEventAt=latest_event.event_time if latest_event else None,
        updatedAt=game.updated_at,
    )
