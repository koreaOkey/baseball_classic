import React from "react";
import { WATCH_WIDTH, WATCH_HEIGHT, SCALE } from "../theme";

/** Apple Watch 45mm 외형 프레임 */
export const WatchFrame: React.FC<{
  children: React.ReactNode;
  caption?: string;
}> = ({ children, caption }) => {
  const w = WATCH_WIDTH * SCALE;
  const h = WATCH_HEIGHT * SCALE;
  const bezel = 12 * SCALE;
  const radius = 80 * SCALE;

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 24 * SCALE,
      }}
    >
      {/* Caption */}
      {caption && (
        <div
          style={{
            color: "white",
            fontSize: 15 * SCALE,
            fontWeight: 800,
            textAlign: "center",
            lineHeight: 1.3,
            fontFamily:
              '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif',
          }}
        >
          {caption}
        </div>
      )}

      {/* Watch body */}
      <div
        style={{
          width: w + bezel * 2,
          height: h + bezel * 2,
          borderRadius: radius,
          background: "#222",
          padding: bezel,
          boxShadow: `0 ${6 * SCALE}px ${30 * SCALE}px rgba(0,0,0,0.7)`,
          border: `${1 * SCALE}px solid rgba(255,255,255,0.08)`,
        }}
      >
        <div
          style={{
            width: w,
            height: h,
            borderRadius: radius - bezel,
            overflow: "hidden",
            position: "relative",
          }}
        >
          {children}
        </div>
      </div>
    </div>
  );
};
