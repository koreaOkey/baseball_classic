from datetime import datetime
from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field


class GameStatus(str, Enum):
    LIVE = "LIVE"
    SCHEDULED = "SCHEDULED"
    FINISHED = "FINISHED"
    CANCELED = "CANCELED"
    POSTPONED = "POSTPONED"


class EventType(str, Enum):
    BALL = "BALL"
    STRIKE = "STRIKE"
    WALK = "WALK"
    OUT = "OUT"
    DOUBLE_PLAY = "DOUBLE_PLAY"
    TRIPLE_PLAY = "TRIPLE_PLAY"
    HALF_INNING_CHANGE = "HALF_INNING_CHANGE"
    HIT = "HIT"
    HOMERUN = "HOMERUN"
    SCORE = "SCORE"
    SAC_FLY_SCORE = "SAC_FLY_SCORE"
    TAG_UP_ADVANCE = "TAG_UP_ADVANCE"
    STEAL = "STEAL"
    PITCHER_CHANGE = "PITCHER_CHANGE"
    OTHER = "OTHER"


class BaseStatus(BaseModel):
    first: bool = False
    second: bool = False
    third: bool = False


class CrawlerEventIn(BaseModel):
    sourceEventId: str = Field(min_length=1, max_length=80)
    type: str = Field(min_length=1, max_length=32)
    description: str = ""
    occurredAt: datetime
    hapticPattern: str | None = None
    metadata: dict[str, Any] | None = None


TeamSide = Literal["home", "away"]


class CrawlerLineupSlotIn(BaseModel):
    teamSide: TeamSide
    battingOrder: int = Field(ge=1, le=9)
    playerId: str | None = Field(default=None, max_length=64)
    playerName: str = Field(min_length=1, max_length=128)
    positionCode: str | None = Field(default=None, max_length=32)
    positionName: str | None = Field(default=None, max_length=64)
    isStarter: bool = False
    isActive: bool = True
    enteredAtSourceEventId: str | None = Field(default=None, max_length=80)
    exitedAtSourceEventId: str | None = Field(default=None, max_length=80)


class CrawlerBatterStatIn(BaseModel):
    teamSide: TeamSide
    playerId: str | None = Field(default=None, max_length=64)
    playerName: str = Field(min_length=1, max_length=128)
    battingOrder: int | None = Field(default=None, ge=1, le=9)
    primaryPosition: str | None = Field(default=None, max_length=64)
    isStarter: bool = False
    plateAppearances: int = Field(default=0, ge=0)
    atBats: int = Field(default=0, ge=0)
    runs: int = Field(default=0, ge=0)
    hits: int = Field(default=0, ge=0)
    rbi: int = Field(default=0, ge=0)
    doubles: int = Field(default=0, ge=0)
    triples: int = Field(default=0, ge=0)
    homeRuns: int = Field(default=0, ge=0)
    walks: int = Field(default=0, ge=0)
    strikeouts: int = Field(default=0, ge=0)
    stolenBases: int = Field(default=0, ge=0)
    caughtStealing: int = Field(default=0, ge=0)
    hitByPitch: int = Field(default=0, ge=0)
    sacBunts: int = Field(default=0, ge=0)
    sacFlies: int = Field(default=0, ge=0)
    leftOnBase: int = Field(default=0, ge=0)


class CrawlerPitcherStatIn(BaseModel):
    teamSide: TeamSide
    appearanceOrder: int | None = Field(default=None, ge=1, le=99)
    playerId: str | None = Field(default=None, max_length=64)
    playerName: str = Field(min_length=1, max_length=128)
    isStarter: bool = False
    outsRecorded: int = Field(default=0, ge=0)
    hitsAllowed: int = Field(default=0, ge=0)
    runsAllowed: int = Field(default=0, ge=0)
    earnedRuns: int = Field(default=0, ge=0)
    walksAllowed: int = Field(default=0, ge=0)
    strikeouts: int = Field(default=0, ge=0)
    homeRunsAllowed: int = Field(default=0, ge=0)
    battersFaced: int = Field(default=0, ge=0)
    atBatsAgainst: int = Field(default=0, ge=0)
    pitchesThrown: int = Field(default=0, ge=0)


class CrawlerGameNoteIn(BaseModel):
    teamSide: TeamSide | None = None
    noteType: str = Field(min_length=1, max_length=64)
    noteTitle: str = Field(default="", max_length=255)
    noteBody: str = Field(default="", max_length=2000)
    inning: str | None = Field(default=None, max_length=32)
    sourceEventId: str | None = Field(default=None, max_length=80)


class CrawlerSnapshotRequest(BaseModel):
    homeTeam: str = Field(min_length=1, max_length=64)
    awayTeam: str = Field(min_length=1, max_length=64)
    gameDate: str | None = Field(default=None, min_length=10, max_length=10)
    status: str = Field(min_length=1, max_length=32)
    inning: str = Field(min_length=1, max_length=32)
    homeScore: int = Field(ge=0, le=99)
    awayScore: int = Field(ge=0, le=99)
    ball: int = Field(default=0, ge=0, le=4)
    strike: int = Field(default=0, ge=0, le=3)
    out: int = Field(default=0, ge=0, le=3)
    bases: BaseStatus = Field(default_factory=BaseStatus)
    pitcher: str | None = Field(default=None, max_length=128)
    batter: str | None = Field(default=None, max_length=128)
    startTime: str | None = Field(default=None, min_length=4, max_length=5)
    homeHits: int | None = Field(default=None, ge=0)
    awayHits: int | None = Field(default=None, ge=0)
    homeHomeRuns: int | None = Field(default=None, ge=0)
    awayHomeRuns: int | None = Field(default=None, ge=0)
    homeOutsTotal: int | None = Field(default=None, ge=0)
    awayOutsTotal: int | None = Field(default=None, ge=0)
    observedAt: datetime | None = None
    events: list[CrawlerEventIn] = Field(default_factory=list)
    lineupSlots: list[CrawlerLineupSlotIn] | None = None
    batterStats: list[CrawlerBatterStatIn] | None = None
    pitcherStats: list[CrawlerPitcherStatIn] | None = None
    notes: list[CrawlerGameNoteIn] | None = None


class CrawlerTeamRecordIn(BaseModel):
    upperCategoryId: str | None = Field(default=None, max_length=32)
    categoryId: str = Field(min_length=1, max_length=32)
    seasonCode: str = Field(min_length=1, max_length=8)
    teamId: str = Field(min_length=1, max_length=32)
    teamName: str = Field(min_length=1, max_length=64)
    teamShortName: str | None = Field(default=None, max_length=64)
    ranking: int | None = Field(default=None, ge=1)
    orderNo: int | None = Field(default=None, ge=1)
    gameType: str | None = Field(default=None, max_length=32)
    wra: float | None = Field(default=None, ge=0)
    gameCount: int | None = Field(default=None, ge=0)
    winGameCount: int | None = Field(default=None, ge=0)
    drawnGameCount: int | None = Field(default=None, ge=0)
    loseGameCount: int | None = Field(default=None, ge=0)
    gameBehind: float | None = None
    continuousGameResult: str | None = Field(default=None, max_length=32)
    lastFiveGames: str | None = Field(default=None, max_length=16)
    offenseHra: float | None = Field(default=None, ge=0)
    defenseEra: float | None = Field(default=None, ge=0)
    observedAt: datetime | None = None
    raw: dict[str, Any] | None = None


class CrawlerTeamRecordRequest(BaseModel):
    upperCategoryId: str | None = Field(default=None, max_length=32)
    categoryId: str = Field(min_length=1, max_length=32)
    seasonCode: str = Field(min_length=1, max_length=8)
    observedAt: datetime | None = None
    records: list[CrawlerTeamRecordIn] = Field(default_factory=list)


class IngestResult(BaseModel):
    gameId: str
    receivedEvents: int
    insertedEvents: int
    duplicateEvents: int
    status: GameStatus
    updatedAt: datetime


class TeamRecordIngestResult(BaseModel):
    categoryId: str
    seasonCode: str
    receivedRecords: int
    upsertedRecords: int
    updatedAt: datetime


class TeamRecordOut(BaseModel):
    upperCategoryId: str | None = None
    categoryId: str
    seasonCode: str
    teamId: str
    teamName: str
    teamShortName: str | None = None
    ranking: int | None = None
    wra: float | None = None
    gameCount: int | None = None
    winGameCount: int | None = None
    drawnGameCount: int | None = None
    loseGameCount: int | None = None
    gameBehind: float | None = None
    continuousGameResult: str | None = None
    lastFiveGames: str | None = None
    offenseHra: float | None = None
    defenseEra: float | None = None
    observedAt: datetime | None = None
    updatedAt: datetime


class GameSummaryOut(BaseModel):
    id: str
    homeTeam: str
    awayTeam: str
    homeScore: int
    awayScore: int
    inning: str
    status: GameStatus
    startTime: str | None = None
    observedAt: datetime | None = None
    updatedAt: datetime


class GameStateOut(BaseModel):
    gameId: str
    homeTeam: str
    awayTeam: str
    homeScore: int
    awayScore: int
    inning: str
    status: GameStatus
    ball: int
    strike: int
    out: int
    bases: BaseStatus
    pitcher: str | None = None
    batter: str | None = None
    lastEventType: EventType | None = None
    lastEventAt: datetime | None = None
    updatedAt: datetime


class GameEventOut(BaseModel):
    cursor: int
    id: str
    type: EventType
    description: str
    time: datetime
    pitcher: str | None = None
    batter: str | None = None
    hapticPattern: str | None = None


class EventsResponse(BaseModel):
    items: list[GameEventOut]
    nextCursor: int | None = None


class DeviceTokenRequest(BaseModel):
    token: str = Field(min_length=1, max_length=256)
    game_id: str = Field(min_length=1, max_length=64)
    my_team: str | None = Field(default=None, max_length=64)
    platform: str = Field(default="ios", max_length=16)
