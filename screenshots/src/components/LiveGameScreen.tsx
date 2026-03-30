import React from "react";
import { colors, ui, SCALE } from "../theme";
import type { GameData } from "../types";

const s = SCALE;

function inningHalfIcon(inning: string): string {
  if (inning.includes("초") || inning.includes("TOP")) return "▲";
  if (inning.includes("말") || inning.includes("BOT")) return "▼";
  return "";
}

const ScoreSide: React.FC<{ team: string; score: number }> = ({
  team,
  score,
}) => (
  <div
    style={{
      flex: 1,
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 2 * s,
    }}
  >
    <span
      style={{
        fontSize: ui.scoreValueSize * s,
        fontWeight: 900,
        color: "white",
        lineHeight: 1.1,
        fontFamily: '-apple-system, "SF Pro Display", sans-serif',
      }}
    >
      {score}
    </span>
    <span
      style={{
        fontSize: ui.teamNameSize * s,
        fontWeight: 700,
        color: "rgba(255,255,255,0.76)",
        fontFamily: '-apple-system, "SF Pro Display", sans-serif',
      }}
    >
      {team.toUpperCase()}
    </span>
  </div>
);

const CountDot: React.FC<{ active: boolean; color: string }> = ({
  active,
  color,
}) => (
  <div
    style={{
      width: ui.countDotSize * s,
      height: ui.countDotSize * s,
      borderRadius: "50%",
      backgroundColor: active
        ? color
        : color.replace("rgb", "rgba").replace(")", ",0.2)"),
    }}
  />
);

const CountRow: React.FC<{
  label: string;
  current: number;
  max: number;
  color: string;
}> = ({ label, current, max, color }) => (
  <div style={{ display: "flex", alignItems: "center", gap: 4 * s }}>
    <span
      style={{
        fontSize: ui.countLabelSize * s,
        fontWeight: 900,
        color: "rgba(255,255,255,0.35)",
        width: ui.countLabelWidth * s,
        textAlign: "right",
        fontFamily: '-apple-system, "SF Pro Display", sans-serif',
      }}
    >
      {label}
    </span>
    <div style={{ display: "flex", gap: 4 * s }}>
      {Array.from({ length: max }).map((_, i) => (
        <CountDot key={i} active={i < current} color={color} />
      ))}
    </div>
  </div>
);

const BaseCell: React.FC<{ occupied: boolean; isHome?: boolean }> = ({
  occupied,
  isHome,
}) => (
  <div
    style={{
      width: ui.baseCellSize * s,
      height: ui.baseCellSize * s,
      borderRadius: 2 * s,
      backgroundColor: occupied
        ? colors.yellow400
        : isHome
          ? "rgba(255,255,255,0.08)"
          : colors.gray800,
    }}
  />
);

const BaseDiamond: React.FC<{
  bases: { first: boolean; second: boolean; third: boolean };
}> = ({ bases }) => (
  <div
    style={{
      width: ui.baseDiamondFrame * s,
      height: ui.baseDiamondFrame * s,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
    }}
  >
    <div style={{ transform: "rotate(45deg)" }}>
      <div style={{ display: "flex", gap: 2 * s, marginBottom: 2 * s }}>
        <BaseCell occupied={bases.second} />
        <BaseCell occupied={bases.first} />
      </div>
      <div style={{ display: "flex", gap: 2 * s }}>
        <BaseCell occupied={bases.third} />
        <BaseCell occupied={false} isHome />
      </div>
    </div>
  </div>
);

export const LiveGameScreen: React.FC<{ data: GameData }> = ({ data }) => {
  const half = inningHalfIcon(data.inning);

  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        background: colors.gray950,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "space-evenly",
        padding: `${30 * s}px 0`,
      }}
    >
      {/* Score Card */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          width: "100%",
          padding: `0 ${ui.horizontalPadding * s}px`,
        }}
      >
        <ScoreSide team={data.awayTeam} score={data.awayScore} />

        {/* Inning */}
        <div
          style={{
            minWidth: 50 * s,
            minHeight: 40 * s,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            background: "rgba(255,255,255,0.05)",
            borderRadius: 10 * s,
            padding: `${4 * s}px ${8 * s}px`,
          }}
        >
          <span
            style={{
              fontSize: ui.inningSize * s,
              fontWeight: 700,
              color: colors.orange500,
              fontFamily: '-apple-system, "SF Pro Display", sans-serif',
            }}
          >
            {data.inning}
          </span>
          {half && (
            <span
              style={{
                fontSize: ui.inningHalfSize * s,
                color: colors.orange500,
                lineHeight: 1,
              }}
            >
              {half}
            </span>
          )}
        </div>

        <ScoreSide team={data.homeTeam} score={data.homeScore} />
      </div>

      {/* BSO + Base Diamond */}
      <div style={{ display: "flex", alignItems: "center", gap: 12 * s }}>
        <BaseDiamond bases={data.bases} />

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 5 * s,
          }}
        >
          <CountRow
            label="B"
            current={data.ballCount}
            max={3}
            color={colors.green500}
          />
          <CountRow
            label="S"
            current={data.strikeCount}
            max={2}
            color={colors.orange500}
          />
          <CountRow
            label="O"
            current={data.outCount}
            max={2}
            color={colors.red500}
          />
        </div>
      </div>

      {/* Player info */}
      <span
        style={{
          fontSize: ui.playerInfoSize * s,
          color: "rgba(255,255,255,0.62)",
          fontFamily: '-apple-system, "SF Pro Display", sans-serif',
        }}
      >
        P {data.pitcher}  B {data.batter}
      </span>
    </div>
  );
};
