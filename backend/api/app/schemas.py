from datetime import datetime
from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class GameStatus(str, Enum):
    LIVE = "LIVE"
    SCHEDULED = "SCHEDULED"
    FINISHED = "FINISHED"


class EventType(str, Enum):
    BALL = "BALL"
    STRIKE = "STRIKE"
    WALK = "WALK"
    OUT = "OUT"
    HIT = "HIT"
    HOMERUN = "HOMERUN"
    SCORE = "SCORE"
    SAC_FLY_SCORE = "SAC_FLY_SCORE"
    TAG_UP_ADVANCE = "TAG_UP_ADVANCE"
    STEAL = "STEAL"
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


class CrawlerSnapshotRequest(BaseModel):
    homeTeam: str = Field(min_length=1, max_length=64)
    awayTeam: str = Field(min_length=1, max_length=64)
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
    observedAt: datetime | None = None
    events: list[CrawlerEventIn] = Field(default_factory=list)


class IngestResult(BaseModel):
    gameId: str
    receivedEvents: int
    insertedEvents: int
    duplicateEvents: int
    status: GameStatus
    updatedAt: datetime


class GameSummaryOut(BaseModel):
    id: str
    homeTeam: str
    awayTeam: str
    homeScore: int
    awayScore: int
    inning: str
    status: GameStatus
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
    hapticPattern: str | None = None


class EventsResponse(BaseModel):
    items: list[GameEventOut]
    nextCursor: int | None = None
