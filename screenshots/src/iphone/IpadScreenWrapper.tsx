import React from "react";
import { IPHONE_WIDTH, IPHONE_HEIGHT } from "./theme";

/**
 * Wraps existing 1290x2796 iPhone screens and scales them
 * to fit 2064x2752 (iPad Pro 13" App Store requirement).
 * Centers the content both horizontally and vertically.
 */
const IPAD_WIDTH = 2064;
const IPAD_HEIGHT = 2752;

const SCALE = Math.min(
  IPAD_WIDTH / IPHONE_WIDTH,
  IPAD_HEIGHT / IPHONE_HEIGHT
);

const scaledWidth = IPHONE_WIDTH * SCALE;
const scaledHeight = IPHONE_HEIGHT * SCALE;
const offsetX = (IPAD_WIDTH - scaledWidth) / 2;
const offsetY = (IPAD_HEIGHT - scaledHeight) / 2;

export const IpadScreenWrapper: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => (
  <div
    style={{
      width: IPAD_WIDTH,
      height: IPAD_HEIGHT,
      overflow: "hidden",
      background: "#0A0A0B",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
    }}
  >
    <div
      style={{
        transform: `scale(${SCALE})`,
        transformOrigin: "center center",
        width: IPHONE_WIDTH,
        height: IPHONE_HEIGHT,
      }}
    >
      {children}
    </div>
  </div>
);
