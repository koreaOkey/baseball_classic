import React from "react";
import { colors, SCALE } from "../theme";

const s = SCALE;

type EventConfig = {
  label: string;
  icon: string;
  color: string;
};

const eventMap: Record<string, EventConfig> = {
  HIT: { label: "HIT", icon: "⚡", color: colors.green500 },
  WALK: { label: "WALK", icon: "⚡", color: colors.green400 },
  STEAL: { label: "STEAL", icon: "⚡", color: "rgb(6, 182, 212)" },
  SCORE: { label: "SCORE", icon: "🏆", color: colors.yellow500 },
  HOMERUN: { label: "HOMERUN", icon: "🏆", color: colors.yellow500 },
  OUT: { label: "OUT", icon: "✕", color: colors.red500 },
  DOUBLE_PLAY: { label: "DOUBLE PLAY", icon: "✕", color: colors.orange500 },
  TRIPLE_PLAY: { label: "TRIPLE PLAY", icon: "✕", color: colors.red600 },
};

export const EventOverlayBadge: React.FC<{ eventType: string }> = ({
  eventType,
}) => {
  const config = eventMap[eventType.toUpperCase()];
  if (!config) return null;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 6 * s,
        padding: `${14 * s}px ${24 * s}px`,
        background: "rgba(0,0,0,0.85)",
        borderRadius: 16 * s,
      }}
    >
      <span style={{ fontSize: 28 * s, color: config.color }}>{config.icon}</span>
      <span
        style={{
          fontSize: 14 * s,
          fontWeight: 500,
          color: "white",
          fontFamily: '-apple-system, "SF Pro Display", sans-serif',
        }}
      >
        {config.label}
      </span>
    </div>
  );
};
