import React from "react";
import { appColors, myTeam, IPHONE_WIDTH, IPHONE_HEIGHT } from "./theme";
import { TeamLogo } from "./components/TeamLogo";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

interface GameInfo {
  away: { name: string; nameKo: string; logo: string; color: string; score: number };
  home: { name: string; nameKo: string; logo: string; color: string; score: number };
  status: "live" | "scheduled" | "finished";
  inning?: string;
  time?: string;
  isMyTeam: boolean;
  isSynced?: boolean;
}

const games: GameInfo[] = [
  {
    away: { name: "KIA", nameKo: "KIA", logo: "team_kia.png", color: "#EA0029", score: 4 },
    home: { name: "SSG", nameKo: "SSG", logo: "team_ssg.png", color: "#CE0E2D", score: 5 },
    status: "live",
    inning: "9회말",
    isMyTeam: true,
    isSynced: true,
  },
  {
    away: { name: "LG", nameKo: "LG", logo: "team_lg.png", color: "#C30452", score: 3 },
    home: { name: "DOOSAN", nameKo: "두산", logo: "team_doosan.png", color: "#131230", score: 2 },
    status: "live",
    inning: "7회초",
    isMyTeam: false,
  },
  {
    away: { name: "SAMSUNG", nameKo: "삼성", logo: "team_samsung.png", color: "#074CA1", score: 0 },
    home: { name: "NC", nameKo: "NC", logo: "team_nc.png", color: "#315288", score: 0 },
    status: "scheduled",
    time: "18:30",
    isMyTeam: false,
  },
  {
    away: { name: "HANWHA", nameKo: "한화", logo: "team_hanwha.png", color: "#FF6600", score: 0 },
    home: { name: "KIWOOM", nameKo: "키움", logo: "team_kiwoom.png", color: "#820024", score: 0 },
    status: "scheduled",
    time: "18:30",
    isMyTeam: false,
  },
  {
    away: { name: "KT", nameKo: "KT", logo: "team_kt.png", color: "#1A1A1A", score: 0 },
    home: { name: "LOTTE", nameKo: "롯데", logo: "team_lotte.png", color: "#041E42", score: 0 },
    status: "scheduled",
    time: "18:30",
    isMyTeam: false,
  },
];

const TeamScoreRow: React.FC<{
  team: GameInfo["away"];
  isMyTeam: boolean;
  isWinner: boolean;
  showScore: boolean;
}> = ({ team, isMyTeam, isWinner, showScore }) => (
  <div
    style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      width: "100%",
    }}
  >
    <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
      <TeamLogo logo={team.logo} bgColor={team.color} size={64} />
      <span
        style={{
          fontSize: isMyTeam ? 36 : 32,
          fontWeight: isMyTeam ? 600 : 500,
          color: "white",
        }}
      >
        {team.nameKo}
      </span>
    </div>
    <span
      style={{
        fontSize: isMyTeam ? 56 : 48,
        fontWeight: 700,
        color: showScore ? "white" : appColors.gray500,
      }}
    >
      {showScore ? team.score : "-"}
    </span>
  </div>
);

const GameCard: React.FC<{ game: GameInfo }> = ({ game }) => {
  const isLive = game.status === "live";
  const showScore = game.status !== "scheduled";

  return (
    <div
      style={{
        background: game.isMyTeam
          ? `${myTeam.primary}26`
          : appColors.gray900,
        borderRadius: 32,
        padding: "36px 40px",
        display: "flex",
        flexDirection: "column",
        gap: 24,
        border: game.isSynced
          ? `4px solid ${appColors.yellow500}`
          : game.isMyTeam
            ? `2px solid ${appColors.yellow500}80`
            : "2px solid transparent",
      }}
    >
      {/* Status row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          {isLive && (
            <>
              <div
                style={{
                  width: 16,
                  height: 16,
                  borderRadius: 8,
                  background: appColors.red500,
                }}
              />
              <span
                style={{ fontSize: 28, fontWeight: 500, color: appColors.red500 }}
              >
                LIVE
              </span>
              <span
                style={{
                  fontSize: 28,
                  color: game.isMyTeam ? "white" : appColors.gray400,
                  marginLeft: 8,
                }}
              >
                {game.inning}
              </span>
              {game.isSynced && (
                <span
                  style={{
                    fontSize: 24,
                    fontWeight: 500,
                    color: appColors.yellow500,
                    marginLeft: 8,
                  }}
                >
                  (워치에서 중계중)
                </span>
              )}
            </>
          )}
          {game.status === "scheduled" && (
            <>
              <span style={{ fontSize: 28, color: appColors.gray400 }}>
                🕐
              </span>
              <span style={{ fontSize: 28, color: appColors.gray400 }}>
                경기 시작 시간 {game.time}
              </span>
            </>
          )}
        </div>
        {game.isMyTeam && (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 6,
              background: appColors.yellow500,
              borderRadius: 40,
              padding: "6px 24px",
            }}
          >
            <span style={{ fontSize: 22, color: "white" }}>⭐</span>
            <span
              style={{ fontSize: 24, fontWeight: 700, color: "white" }}
            >
              응원팀
            </span>
          </div>
        )}
      </div>

      {/* Scores */}
      <TeamScoreRow
        team={game.away}
        isMyTeam={game.isMyTeam && game.away.name === myTeam.name}
        isWinner={showScore && game.away.score > game.home.score}
        showScore={showScore}
      />
      <TeamScoreRow
        team={game.home}
        isMyTeam={game.isMyTeam && game.home.name === myTeam.name}
        isWinner={showScore && game.home.score > game.away.score}
        showScore={showScore}
      />
    </div>
  );
};

export const HomeScreen: React.FC = () => (
  <div
    style={{
      width: IPHONE_WIDTH,
      height: IPHONE_HEIGHT,
      background: appColors.gray950,
      fontFamily: font,
      display: "flex",
      flexDirection: "column",
      overflow: "hidden",
    }}
  >
    {/* Status bar spacer */}
    <div style={{ height: 110 }} />

    {/* Header with team gradient */}
    <div
      style={{
        background: `linear-gradient(180deg, ${myTeam.primary} 0%, ${myTeam.primaryDark} 100%)`,
        padding: "48px 48px 80px",
        display: "flex",
        flexDirection: "column",
        gap: 24,
      }}
    >
      {/* Top bar */}
      <div
        style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <TeamLogo logo={myTeam.logo} bgColor={myTeam.primary} size={112} />
          <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontSize: 24, color: "rgba(255,255,255,0.7)" }}>
              BaseHaptic Live
            </span>
            <span style={{ fontSize: 48, fontWeight: 700, color: "white" }}>
              {myTeam.nameKo}
            </span>
          </div>
        </div>
        <div
          style={{
            width: 96,
            height: 96,
            borderRadius: 48,
            background: "rgba(255,255,255,0.15)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <span style={{ fontSize: 40 }}>⚙️</span>
        </div>
      </div>

      {/* Date bar */}
      <div
        style={{
          background: "rgba(255,255,255,0.15)",
          borderRadius: 32,
          padding: "20px 32px",
          display: "flex",
          alignItems: "center",
          gap: 12,
        }}
      >
        <span style={{ fontSize: 28, color: "white" }}>📅</span>
        <span style={{ fontSize: 28, color: "white" }}>
          3월 30일 일요일
        </span>
        <span
          style={{ fontSize: 28, color: "rgba(255,255,255,0.7)", marginLeft: "auto" }}
        >
          5경기
        </span>
      </div>
    </div>

    {/* Quick Stats */}
    <div
      style={{
        display: "flex",
        gap: 24,
        padding: "0 48px",
        marginTop: -32,
      }}
    >
      {[
        { label: "최근 5경기", value: "4승", color: appColors.green500 },
        { label: "순위", value: "2위", color: appColors.yellow500 },
        { label: "승률", value: ".583", color: appColors.blue500 },
      ].map((stat) => (
        <div
          key={stat.label}
          style={{
            flex: 1,
            background: appColors.gray900,
            borderRadius: 24,
            padding: "28px 24px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 8,
          }}
        >
          <span style={{ fontSize: 40, fontWeight: 700, color: stat.color }}>
            {stat.value}
          </span>
          <span style={{ fontSize: 22, color: appColors.gray400 }}>
            {stat.label}
          </span>
        </div>
      ))}
    </div>

    {/* Games */}
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        padding: "32px 48px 0",
        gap: 24,
      }}
    >
      <span style={{ fontSize: 36, fontWeight: 700, color: "white" }}>
        오늘의 경기
      </span>
      {games.map((g, i) => (
        <GameCard key={i} game={g} />
      ))}
    </div>
  </div>
);
