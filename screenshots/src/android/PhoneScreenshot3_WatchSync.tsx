import React from "react";
import { appColors, myTeam, PHONE_WIDTH, PHONE_HEIGHT } from "./theme";
import { TeamLogo } from "../iphone/components/TeamLogo";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

/** Home 화면 위에 워치 동기화 다이얼로그 */
export const PhoneScreenshot3_WatchSync: React.FC = () => (
  <div
    style={{
      width: PHONE_WIDTH,
      height: PHONE_HEIGHT,
      position: "relative",
      fontFamily: font,
    }}
  >
    {/* Background */}
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
      <div style={{ height: 80 }} />

      <div
        style={{
          background: `linear-gradient(180deg, ${myTeam.primary} 0%, ${myTeam.primaryDark} 100%)`,
          padding: "36px 36px 60px",
          display: "flex",
          flexDirection: "column",
          gap: 20,
        }}
      >
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

      {/* Stats */}
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

      {/* Game card (partial) */}
      <div style={{ padding: "24px 36px 0", display: "flex", flexDirection: "column", gap: 16 }}>
        <span style={{ fontSize: 28, fontWeight: 700, color: "white" }}>오늘의 경기</span>
        <div
          style={{
            background: `${myTeam.primary}26`,
            borderRadius: 24,
            padding: "28px 32px",
            display: "flex",
            flexDirection: "column",
            gap: 18,
            border: `2px solid ${appColors.yellow500}80`,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ width: 12, height: 12, borderRadius: 6, background: appColors.red500 }} />
            <span style={{ fontSize: 22, fontWeight: 500, color: appColors.red500 }}>LIVE</span>
            <span style={{ fontSize: 22, color: "white", marginLeft: 6 }}>9회말</span>
            <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 4, background: appColors.yellow500, borderRadius: 32, padding: "4px 16px" }}>
              <span style={{ fontSize: 16, color: "white" }}>⭐</span>
              <span style={{ fontSize: 18, fontWeight: 700, color: "white" }}>응원팀</span>
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
              <TeamLogo logo="team_kia.png" bgColor="#EA0029" size={52} />
              <span style={{ fontSize: 26, fontWeight: 500, color: "white" }}>KIA</span>
            </div>
            <span style={{ fontSize: 38, fontWeight: 700, color: "white" }}>4</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
              <TeamLogo logo="team_ssg.png" bgColor="#CE0E2D" size={52} />
              <span style={{ fontSize: 30, fontWeight: 600, color: "white" }}>SSG</span>
            </div>
            <span style={{ fontSize: 44, fontWeight: 700, color: "white" }}>5</span>
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
      {/* Material Dialog */}
      <div
        style={{
          width: 560,
          background: "#2C2C2E",
          borderRadius: 28,
          overflow: "hidden",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
        }}
      >
        <div
          style={{
            padding: "40px 40px 28px",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 10,
          }}
        >
          <span style={{ fontSize: 28, fontWeight: 600, color: "white" }}>
            워치 동기화
          </span>
          <span style={{ fontSize: 22, color: appColors.gray300, textAlign: "center", lineHeight: 1.5 }}>
            이 경기를 Wear OS에서{"\n"}실시간으로 관람하겠습니까?
          </span>
        </div>

        <div style={{ width: "100%", height: 1, background: "rgba(255,255,255,0.15)" }} />

        <div style={{ display: "flex", width: "100%" }}>
          <div
            style={{
              flex: 1,
              padding: "22px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              borderRight: "1px solid rgba(255,255,255,0.15)",
            }}
          >
            <span style={{ fontSize: 28, color: appColors.blue400 }}>
              아니오
            </span>
          </div>
          <div
            style={{
              flex: 1,
              padding: "22px 0",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <span style={{ fontSize: 28, fontWeight: 600, color: appColors.blue400 }}>
              예
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
);
