import React from "react";
import { appColors, teams, PHONE_WIDTH, PHONE_HEIGHT } from "./theme";
import { TeamLogo } from "../iphone/components/TeamLogo";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

export const PhoneScreenshot2_Onboarding: React.FC = () => {
  const selectedTeam = teams.find((t) => t.name === "SSG")!;

  return (
    <div
      style={{
        width: PHONE_WIDTH,
        height: PHONE_HEIGHT,
        background: `linear-gradient(180deg, ${appColors.gray950} 0%, #0F172A 50%, ${appColors.gray950} 100%)`,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: "120px 36px 60px",
        fontFamily: font,
      }}
    >
      {/* Header */}
      <span style={{ fontSize: 96 }}>⚾</span>
      <span
        style={{
          fontSize: 56,
          fontWeight: 800,
          color: "white",
          marginTop: 20,
        }}
      >
        야구봄
      </span>
      <span
        style={{
          fontSize: 22,
          color: appColors.gray400,
          marginTop: 12,
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
          marginTop: 48,
          background: `${appColors.gray900}cc`,
          borderRadius: 24,
          padding: 36,
          display: "flex",
          flexDirection: "column",
          gap: 12,
        }}
      >
        <span style={{ fontSize: 30, fontWeight: 700, color: "white" }}>
          응원하는 팀을 선택하세요
        </span>

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 12,
            marginTop: 12,
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
                  gap: 18,
                  padding: "18px 24px",
                  borderRadius: 18,
                  background: isSelected
                    ? team.primary
                    : `${appColors.gray800}80`,
                  border: isSelected
                    ? `2px solid ${team.secondary}`
                    : "2px solid transparent",
                }}
              >
                <TeamLogo logo={team.logo} bgColor={team.primary} size={60} />
                <span
                  style={{
                    fontSize: 24,
                    fontWeight: 500,
                    color: "white",
                    flex: 1,
                  }}
                >
                  {team.nameKo}
                </span>
                {isSelected && (
                  <span style={{ fontSize: 24, color: "white" }}>✓</span>
                )}
              </div>
            );
          })}
        </div>

        <div
          style={{
            marginTop: 28,
            padding: "22px 0",
            background: appColors.blue600,
            borderRadius: 18,
            textAlign: "center",
          }}
        >
          <span style={{ fontSize: 26, fontWeight: 600, color: "white" }}>
            다음
          </span>
        </div>
      </div>
    </div>
  );
};
