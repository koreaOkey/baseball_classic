import React from "react";
import { colors, SCALE } from "../theme";

const s = SCALE;

export const SyncPromptScreen: React.FC<{
  awayTeam: string;
  homeTeam: string;
}> = ({ awayTeam, homeTeam }) => {
  const matchup = `${awayTeam} vs ${homeTeam}`;

  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        background: colors.gray950,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
      }}
    >
      {/* Dimmed background */}
      <div
        style={{
          position: "absolute",
          top: 0,
          left: 0,
          width: "100%",
          height: "100%",
          background: "rgba(0,0,0,0.78)",
        }}
      />

      {/* Dialog */}
      <div
        style={{
          position: "relative",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 0,
          background: "rgb(30, 30, 30)",
          borderRadius: 16 * s,
          padding: `${16 * s}px ${16 * s}px`,
          margin: `0 ${16 * s}px`,
        }}
      >
        <span
          style={{
            color: "white",
            fontSize: 14 * s,
            fontWeight: 600,
            textAlign: "center",
            fontFamily: '-apple-system, "SF Pro Display", sans-serif',
          }}
        >
          경기를 관람하겠습니까?
        </span>
        <span
          style={{
            color: "rgba(255,255,255,0.72)",
            fontSize: 12 * s,
            marginTop: 4 * s,
            fontFamily: '-apple-system, "SF Pro Display", sans-serif',
          }}
        >
          {matchup}
        </span>

        {/* Buttons */}
        <div
          style={{
            display: "flex",
            gap: 10 * s,
            marginTop: 12 * s,
          }}
        >
          <div
            style={{
              padding: `${7 * s}px ${24 * s}px`,
              borderRadius: 22 * s,
              background: "rgb(0, 122, 255)",
              color: "white",
              fontSize: 14 * s,
              fontWeight: 600,
              fontFamily: '-apple-system, "SF Pro Display", sans-serif',
            }}
          >
            예
          </div>
          <div
            style={{
              padding: `${7 * s}px ${18 * s}px`,
              borderRadius: 22 * s,
              background: "rgba(255,255,255,0.15)",
              color: "white",
              fontSize: 14 * s,
              fontWeight: 600,
              fontFamily: '-apple-system, "SF Pro Display", sans-serif',
            }}
          >
            아니오
          </div>
        </div>
      </div>
    </div>
  );
};
