from datetime import datetime
from enum import Enum
from typing import Annotated, Any, Literal

from pydantic import BaseModel, Field, PlainSerializer


# iOS ISO8601DateFormatter 기본 옵션은 fractional seconds 를 파싱하지 못해서
# 파싱 실패 시 fallback 으로 "T" 뒤 문자열 앞 5글자(UTC HH:mm)를 그대로
# 화면에 노출하는 버그가 있다. 모든 응답 datetime 을 마이크로초 없는 정수초
# ISO 로 내보내면 iOS 가 정상 파싱 후 KST 로 변환해서 표시한다.
def _iso_no_microseconds(value: datetime) -> str:
    return value.replace(microsecond=0).isoformat().replace("+00:00", "Z")


IsoDatetime = Annotated[datetime, PlainSerializer(_iso_no_microseconds, return_type=str)]


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


class BaseRunnerStatus(BaseModel):
    first: str | None = Field(default=None, max_length=128)
    second: str | None = Field(default=None, max_length=128)
    third: str | None = Field(default=None, max_length=128)


class CrawlerEventIn(BaseModel):
    sourceEventId: str = Field(min_length=1, max_length=80)
    type: str = Field(min_length=1, max_length=32)
    description: str = ""
    occurredAt: datetime
    hapticPattern: str | None = None
    inning: str | None = Field(default=None, max_length=32)
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
    baseRunners: BaseRunnerStatus = Field(default_factory=BaseRunnerStatus)
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
    updatedAt: IsoDatetime


class TeamRecordIngestResult(BaseModel):
    categoryId: str
    seasonCode: str
    receivedRecords: int
    upsertedRecords: int
    updatedAt: IsoDatetime


class AppNoticeOut(BaseModel):
    enabled: bool = False
    title: str = ""
    message: str = ""


class AppConfigOut(BaseModel):
    platform: str
    minSupportedVersion: str = ""
    latestVersion: str = ""
    forceUpdate: bool = False
    updateTitle: str = "업데이트가 필요합니다"
    updateMessage: str = "안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요."
    storeUrl: str = ""
    notice: AppNoticeOut = Field(default_factory=AppNoticeOut)


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
    observedAt: IsoDatetime | None = None
    updatedAt: IsoDatetime


class GameWeatherSummaryOut(BaseModel):
    stadiumCode: str
    stadiumName: str
    stadiumShortName: str
    forecastDate: str | None = None
    forecastTime: str | None = None
    forecastTimeLabel: str | None = None
    condition: str
    temperatureC: int | None = None
    precipitationProbability: int | None = None
    precipitationType: str | None = None
    windSpeedMps: float | None = None
    isIndoor: bool = False
    displayText: str


class GameWeatherHourlyItemOut(BaseModel):
    forecastDate: str
    forecastTime: str
    timeLabel: str
    condition: str
    temperatureC: int | None = None
    precipitationProbability: int | None = None
    precipitationType: str | None = None
    windSpeedMps: float | None = None
    isGameStartForecast: bool = False


class GameWeatherHourlyOut(BaseModel):
    gameId: str
    stadiumCode: str
    stadiumName: str
    stadiumShortName: str
    gameStartTime: str | None = None
    items: list[GameWeatherHourlyItemOut] = Field(default_factory=list)


class GameSummaryOut(BaseModel):
    id: str
    homeTeam: str
    awayTeam: str
    homeScore: int
    awayScore: int
    inning: str
    status: GameStatus
    startTime: str | None = None
    weather: GameWeatherSummaryOut | None = None
    observedAt: IsoDatetime | None = None
    updatedAt: IsoDatetime


class LineupSlotOut(BaseModel):
    battingOrder: int
    playerName: str
    positionCode: str | None = None
    positionName: str | None = None
    isStarter: bool = False
    isActive: bool = True


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
    baseRunners: BaseRunnerStatus = Field(default_factory=BaseRunnerStatus)
    pitcher: str | None = None
    batter: str | None = None
    pitcherPitchCount: int | None = None
    lastEventType: EventType | None = None
    lastEventAt: IsoDatetime | None = None
    updatedAt: IsoDatetime
    homeLineup: list[LineupSlotOut] = Field(default_factory=list)
    awayLineup: list[LineupSlotOut] = Field(default_factory=list)
    # 선발투수 이름. 라인업 슬롯에는 DH 룰로 타자 9명만 들어가므로 투수는 별도 노출.
    # GamePitcherStat(is_starter=True, appearance_order=1) 우선, 없으면 그냥 is_starter=True 첫 행.
    homeStartingPitcher: str | None = None
    awayStartingPitcher: str | None = None


class GameEventOut(BaseModel):
    cursor: int
    id: str
    type: EventType
    description: str
    time: IsoDatetime
    pitcher: str | None = None
    batter: str | None = None
    hapticPattern: str | None = None
    inning: str | None = None
    # 타석(at-bat) 단위 그룹화 키. source_event_id가 "{inning:02d}-{relayNo:03d}-{seqno:04d}"
    # 형식일 때만 채워지며, 그 외(시뮬레이션·테스트 prefix)에서는 None — 클라가 평면 폴백.
    atBatId: str | None = None
    seqno: int | None = None
    # 이벤트 직후 누적 스코어. 크롤러가 payload_json metadata 에 채운 경우에만 노출.
    # 라이브 상세 "득점" 탭에서 "X회 {공격팀} {타자} 적시타 → 3-2" 같은 표기에 사용.
    # 미수집 이벤트(과거 데이터, 시뮬레이션)에서는 None — 클라가 description 폴백.
    homeScoreAfter: int | None = None
    awayScoreAfter: int | None = None
    # 네이버 relay 원본의 투구 상세/카운트/확률 메타데이터. 크롤러가 채운 경우에만 노출.
    # iOS 라이브 상세 타석 카드에서 구속·구종·투구 후 BSO·승리확률 표기에 사용한다.
    pitchNum: int | None = None
    pitchSpeed: int | None = None
    pitchStuff: str | None = Field(default=None, max_length=64)
    ballAfter: int | None = None
    strikeAfter: int | None = None
    outAfter: int | None = None
    batterRecord: dict[str, Any] | None = None
    homeWinProbability: float | None = None
    awayWinProbability: float | None = None
    wpaByPlate: float | None = None


class EventsResponse(BaseModel):
    items: list[GameEventOut]
    nextCursor: int | None = None


class DeviceTokenRequest(BaseModel):
    token: str = Field(min_length=1, max_length=256)
    game_id: str = Field(min_length=1, max_length=64)
    my_team: str | None = Field(default=None, max_length=64)
    platform: str = Field(default="ios", max_length=16)
    is_sandbox: bool = Field(default=False)


class LiveViewSessionRequest(BaseModel):
    game_id: str = Field(min_length=1, max_length=64)
    user_key: str = Field(min_length=1, max_length=128)
    surface: Literal["ios", "android", "watchos", "wearos"]
    token_key: str | None = Field(default=None, max_length=256)
    my_team: str | None = Field(default=None, max_length=64)
    active: bool = True


class LiveActivityTokenRequest(BaseModel):
    token: str = Field(min_length=1, max_length=256)
    game_id: str = Field(min_length=1, max_length=64)
    my_team: str | None = Field(default=None, max_length=64)


class TeamSubscriptionRequest(BaseModel):
    token: str = Field(min_length=1, max_length=256)
    my_team: str = Field(min_length=1, max_length=64)
    platform: str = Field(default="ios", max_length=16)
    is_sandbox: bool = Field(default=False)
