import React from "react";
import { staticFile } from "remotion";
import { appColors, teams, IPHONE_WIDTH, IPHONE_HEIGHT } from "./theme";
import { TeamLogo } from "./components/TeamLogo";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

export const OnboardingScreen: React.FC = () => {
  const selectedTeam = teams.find((t) => t.name === "SSG")!;

  return (
    <div
      style={{
        width: IPHONE_WIDTH,
        height: IPHONE_HEIGHT,
        background: `linear-gradient(180deg, ${appColors.gray950} 0%, #0F172A 50%, ${appColors.gray950} 100%)`,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: "160px 48px 80px",
        fontFamily: font,
      }}
    >
      {/* Header */}
      <span style={{ fontSize: 128 }}>⚾</span>
      <span
        style={{
          fontSize: 72,
          fontWeight: 800,
          color: "white",
          marginTop: 24,
        }}
      >
        야구봄
      </span>
      <span
        style={{
          fontSize: 28,
          color: appColors.gray400,
          marginTop: 16,
          textAlign: "center",
          lineHeight: 1.5,
        }}
      >
        응원 팀을 선택하고 워치로 실시간 중계를 확인하세요.
      </span>

      {/* Team Selection Card */}
      <div
        style={{
          width: "100%",
          marginTop: 64,
          background: `${appColors.gray900}cc`,
          borderRadius: 32,
          padding: 48,
          display: "flex",
          flexDirection: "column",
          gap: 16,
        }}
      >
        <span
          style={{ fontSize: 40, fontWeight: 700, color: "white" }}
        >
          응원하는 팀을 선택하세요
        </span>

        {/* Team list */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 16,
            marginTop: 16,
          }}
        >
          {teams.map((team) => {
            const isSelected = team.name === selectedTeam.name;
            return (
              <div
                key={team.name}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 24,
                  padding: "24px 32px",
                  borderRadius: 24,
                  background: isSelected
                    ? team.primary
                    : `${appColors.gray800}80`,
                  border: isSelected
                    ? `3px solid ${team.secondary}`
                    : "3px solid transparent",
                }}
              >
                <TeamLogo logo={team.logo} bgColor={team.primary} size={80} />
                <span
                  style={{
                    fontSize: 32,
                    fontWeight: 500,
                    color: "white",
                    flex: 1,
                  }}
                >
                  {team.nameKo}
                </span>
                {isSelected && (
                  <span style={{ fontSize: 32, color: "white" }}>✓</span>
                )}
              </div>
            );
          })}
        </div>

        {/* Continue Button */}
        <div
          style={{
            marginTop: 40,
            padding: "28px 0",
            background: appColors.blue600,
            borderRadius: 24,
            textAlign: "center",
          }}
        >
          <span
            style={{
              fontSize: 32,
              fontWeight: 600,
              color: "white",
            }}
          >
            다음
          </span>
        </div>
      </div>
    </div>
  );
};
