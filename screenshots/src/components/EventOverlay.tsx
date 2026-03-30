import React from "react";
import { SCALE } from "../theme";

const s = SCALE;

const eventConfigs: Record<
  string,
  { label: string; icon: string; color: string }
> = {
  HIT: { label: "HIT", icon: "⚡", color: "rgb(34, 197, 94)" },
  WALK: { label: "WALK", icon: "⚡", color: "rgb(74, 222, 128)" },
  STEAL: { label: "STEAL", icon: "⚡", color: "rgb(6, 182, 212)" },
  SCORE: { label: "SCORE", icon: "🏆", color: "rgb(234, 179, 8)" },
  HOMERUN: { label: "HOMERUN", icon: "🏆", color: "rgb(234, 179, 8)" },
  OUT: { label: "OUT", icon: "✕", color: "rgb(239, 68, 68)" },
  DOUBLE_PLAY: {
    label: "DOUBLE PLAY",
    icon: "✕",
    color: "rgb(249, 115, 22)",
  },
  TRIPLE_PLAY: {
    label: "TRIPLE PLAY",
    icon: "✕",
    color: "rgb(220, 38, 38)",
  },
};

export const EventOverlay: React.FC<{ eventType: string }> = ({
  eventType,
}) => {
  const config = eventConfigs[eventType.toUpperCase()];
  if (!config) return null;

  return (
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        pointerEvents: "none",
      }}
    >
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 6 * s,
          padding: `${12 * s}px ${20 * s}px`,
          background: "rgba(0,0,0,0.82)",
          borderRadius: 16 * s,
        }}
      >
        <span style={{ fontSize: 24 * s }}>{config.icon}</span>
        <span
          style={{
            fontSize: 14 * s,
            fontWeight: 600,
            color: "white",
            fontFamily: '-apple-system, "SF Pro Display", sans-serif',
          }}
        >
          {config.label}
        </span>
      </div>
    </div>
  );
};
