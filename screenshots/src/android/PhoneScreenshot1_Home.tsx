import React from "react";
import { staticFile } from "remotion";
import { appColors, myTeam, PHONE_WIDTH, PHONE_HEIGHT } from "./theme";
import { TeamLogo } from "../iphone/components/TeamLogo";

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
];

const TeamScoreRow: React.FC<{
  team: GameInfo["away"];
  isMyTeam: boolean;
  showScore: boolean;
}> = ({ team, isMyTeam, showScore }) => (
  <div
    style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      width: "100%",
    }}
  >
    <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
      <TeamLogo logo={team.logo} bgColor={team.color} size={52} />
      <span
        style={{
          fontSize: isMyTeam ? 30 : 26,
          fontWeight: isMyTeam ? 600 : 500,
          color: "white",
        }}
      >
        {team.nameKo}
      </span>
    </div>
    <span
      style={{
        fontSize: isMyTeam ? 44 : 38,
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
        borderRadius: 24,
        padding: "28px 32px",
        display: "flex",
        flexDirection: "column",
        gap: 18,
        border: game.isSynced
          ? `3px solid ${appColors.yellow500}`
          : game.isMyTeam
            ? `2px solid ${appColors.yellow500}80`
            : "2px solid transparent",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          {isLive && (
            <>
              <div
                style={{
                  width: 12,
                  height: 12,
                  borderRadius: 6,
                  background: appColors.red500,
                }}
              />
              <span style={{ fontSize: 22, fontWeight: 500, color: appColors.red500 }}>
                LIVE
              </span>
              <span
                style={{
                  fontSize: 22,
                  color: game.isMyTeam ? "white" : appColors.gray400,
                  marginLeft: 6,
                }}
              >
                {game.inning}
              </span>
              {game.isSynced && (
                <span
                  style={{
                    fontSize: 18,
                    fontWeight: 500,
                    color: appColors.yellow500,
                    marginLeft: 6,
                  }}
                >
                  (워치에서 중계중)
                </span>
              )}
            </>
          )}
          {game.status === "scheduled" && (
            <>
              <span style={{ fontSize: 22, color: appColors.gray400 }}>🕐</span>
              <span style={{ fontSize: 22, color: appColors.gray400 }}>
                경기 시작 {game.time}
              </span>
            </>
          )}
        </div>
        {game.isMyTeam && (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 4,
              background: appColors.yellow500,
              borderRadius: 32,
              padding: "4px 16px",
            }}
          >
            <span style={{ fontSize: 16, color: "white" }}>⭐</span>
            <span style={{ fontSize: 18, fontWeight: 700, color: "white" }}>
              응원팀
            </span>
          </div>
        )}
      </div>

      <TeamScoreRow
        team={game.away}
        isMyTeam={game.isMyTeam && game.away.name === myTeam.name}
        showScore={showScore}
      />
      <TeamScoreRow
        team={game.home}
        isMyTeam={game.isMyTeam && game.home.name === myTeam.name}
        showScore={showScore}
      />
    </div>
  );
};

export const PhoneScreenshot1_Home: React.FC = () => (
  <div
    style={{
      width: PHONE_WIDTH,
      height: PHONE_HEIGHT,
      background: appColors.gray950,
      fontFamily: font,
      display: "flex",
      flexDirection: "column",
      overflow: "hidden",
    }}
  >
    {/* Status bar spacer */}
    <div style={{ height: 80 }} />

    {/* Header */}
    <div
      style={{
        background: `linear-gradient(180deg, ${myTeam.primary} 0%, ${myTeam.primaryDark} 100%)`,
        padding: "36px 36px 60px",
        display: "flex",
        flexDirection: "column",
        gap: 20,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <TeamLogo logo={myTeam.logo} bgColor={myTeam.primary} size={88} />
          <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <span style={{ fontSize: 18, color: "rgba(255,255,255,0.7)" }}>
              BaseHaptic Live
            </span>
            <span style={{ fontSize: 38, fontWeight: 700, color: "white" }}>
              {myTeam.nameKo}
            </span>
          </div>
        </div>
        <div
          style={{
            width: 72,
            height: 72,
            borderRadius: 36,
            background: "rgba(255,255,255,0.15)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <span style={{ fontSize: 32 }}>⚙️</span>
        </div>
      </div>

      <div
        style={{
          background: "rgba(255,255,255,0.15)",
          borderRadius: 24,
          padding: "16px 24px",
          display: "flex",
          alignItems: "center",
          gap: 10,
        }}
      >
        <span style={{ fontSize: 22, color: "white" }}>📅</span>
        <span style={{ fontSize: 22, color: "white" }}>3월 30일 일요일</span>
        <span style={{ fontSize: 22, color: "rgba(255,255,255,0.7)", marginLeft: "auto" }}>
          5경기
        </span>
      </div>
    </div>

    {/* Quick Stats */}
    <div style={{ display: "flex", gap: 16, padding: "0 36px", marginTop: -24 }}>
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
            borderRadius: 20,
            padding: "22px 16px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 6,
          }}
        >
          <span style={{ fontSize: 32, fontWeight: 700, color: stat.color }}>
            {stat.value}
          </span>
          <span style={{ fontSize: 16, color: appColors.gray400 }}>
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
        padding: "24px 36px 0",
        gap: 16,
      }}
    >
      <span style={{ fontSize: 28, fontWeight: 700, color: "white" }}>
        오늘의 경기
      </span>
      {games.map((g, i) => (
        <GameCard key={i} game={g} />
      ))}
    </div>
  </div>
);
