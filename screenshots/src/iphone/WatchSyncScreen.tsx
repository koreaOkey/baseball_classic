import React from "react";
import { appColors, myTeam, IPHONE_WIDTH, IPHONE_HEIGHT } from "./theme";
import { TeamLogo } from "./components/TeamLogo";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

/** HomeScreen 위에 워치 동기화 다이얼로그가 떠 있는 화면 */
export const WatchSyncScreen: React.FC = () => (
  <div
    style={{
      width: IPHONE_WIDTH,
      height: IPHONE_HEIGHT,
      position: "relative",
      fontFamily: font,
    }}
  >
    {/* Background: simplified home screen (blurred feel) */}
    <div
      style={{
        width: "100%",
        height: "100%",
        background: appColors.gray950,
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {/* Status bar */}
      <div style={{ height: 110 }} />

      {/* Header */}
      <div
        style={{
          background: `linear-gradient(180deg, ${myTeam.primary} 0%, ${myTeam.primaryDark} 100%)`,
          padding: "48px 48px 80px",
          display: "flex",
          flexDirection: "column",
          gap: 24,
        }}
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
            background: "rgba(255,255,255,0.15)",
            borderRadius: 32,
            padding: "20px 32px",
            display: "flex",
            alignItems: "center",
            gap: 12,
          }}
        >
          <span style={{ fontSize: 28, color: "white" }}>📅</span>
          <span style={{ fontSize: 28, color: "white" }}>3월 30일 일요일</span>
          <span style={{ fontSize: 28, color: "rgba(255,255,255,0.7)", marginLeft: "auto" }}>
            5경기
          </span>
        </div>
      </div>

      {/* Stats */}
      <div style={{ display: "flex", gap: 24, padding: "0 48px", marginTop: -32 }}>
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

      {/* Game section label + first game card (partial) */}
      <div style={{ padding: "32px 48px 0", display: "flex", flexDirection: "column", gap: 24 }}>
        <span style={{ fontSize: 36, fontWeight: 700, color: "white" }}>오늘의 경기</span>
        {/* My team game card */}
        <div
          style={{
            background: `${myTeam.primary}26`,
            borderRadius: 32,
            padding: "36px 40px",
            display: "flex",
            flexDirection: "column",
            gap: 24,
            border: `2px solid ${appColors.yellow500}80`,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={{ width: 16, height: 16, borderRadius: 8, background: appColors.red500 }} />
            <span style={{ fontSize: 28, fontWeight: 500, color: appColors.red500 }}>LIVE</span>
            <span style={{ fontSize: 28, color: "white", marginLeft: 8 }}>9회말</span>
            <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6, background: appColors.yellow500, borderRadius: 40, padding: "6px 24px" }}>
              <span style={{ fontSize: 22, color: "white" }}>⭐</span>
              <span style={{ fontSize: 24, fontWeight: 700, color: "white" }}>응원팀</span>
            </div>
          </div>
          {/* KIA row */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
              <TeamLogo logo="team_kia.png" bgColor="#EA0029" size={64} />
              <span style={{ fontSize: 32, fontWeight: 500, color: "white" }}>KIA</span>
            </div>
            <span style={{ fontSize: 48, fontWeight: 700, color: "white" }}>4</span>
          </div>
          {/* SSG row */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
              <TeamLogo logo="team_ssg.png" bgColor="#CE0E2D" size={64} />
              <span style={{ fontSize: 36, fontWeight: 600, color: "white" }}>SSG</span>
            </div>
            <span style={{ fontSize: 56, fontWeight: 700, color: "white" }}>5</span>
          </div>
        </div>
        {/* Second game card (partially visible) */}
        <div
          style={{
            background: appColors.gray900,
            borderRadius: 32,
            padding: "36px 40px",
            display: "flex",
            flexDirection: "column",
            gap: 24,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={{ width: 16, height: 16, borderRadius: 8, background: appColors.red500 }} />
            <span style={{ fontSize: 28, fontWeight: 500, color: appColors.red500 }}>LIVE</span>
            <span style={{ fontSize: 28, color: appColors.gray400, marginLeft: 8 }}>7회초</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
              <TeamLogo logo="team_lg.png" bgColor="#C30452" size={64} />
              <span style={{ fontSize: 32, fontWeight: 500, color: "white" }}>LG</span>
            </div>
            <span style={{ fontSize: 48, fontWeight: 700, color: "white" }}>3</span>
          </div>
        </div>
      </div>
    </div>

    {/* Dimmed overlay */}
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        background: "rgba(0,0,0,0.55)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      {/* iOS Alert Dialog */}
      <div
        style={{
          width: 700,
          background: "rgba(44, 44, 46, 0.97)",
          borderRadius: 28,
          overflow: "hidden",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
        }}
      >
        {/* Content */}
        <div
          style={{
            padding: "48px 48px 32px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 12,
          }}
        >
          <span style={{ fontSize: 34, fontWeight: 600, color: "white" }}>
            워치 동기화
          </span>
          <span style={{ fontSize: 28, color: appColors.gray300, textAlign: "center" }}>
            이 경기를 Apple Watch에서{"\n"}실시간으로 관람하겠습니까?
          </span>
        </div>

        {/* Divider */}
        <div style={{ width: "100%", height: 1, background: "rgba(255,255,255,0.15)" }} />

        {/* Buttons */}
        <div style={{ display: "flex", width: "100%" }}>
          <div
            style={{
              flex: 1,
              padding: "28px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              borderRight: "1px solid rgba(255,255,255,0.15)",
            }}
          >
            <span style={{ fontSize: 34, color: appColors.blue400 }}>
              아니오
            </span>
          </div>
          <div
            style={{
              flex: 1,
              padding: "28px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <span style={{ fontSize: 34, fontWeight: 600, color: appColors.blue400 }}>
              예
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
);
