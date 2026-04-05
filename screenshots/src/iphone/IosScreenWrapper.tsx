import React from "react";
import { IPHONE_WIDTH, IPHONE_HEIGHT } from "./theme";

/**
 * Wraps existing 1290x2796 iPhone screens and scales them
 * to fit 1242x2688 (iPhone 6.5" App Store requirement).
 */
const IOS65_WIDTH = 1242;
const IOS65_HEIGHT = 2688;

const SCALE_X = IOS65_WIDTH / IPHONE_WIDTH; // 1242/1290 ≈ 0.963
const SCALE_Y = IOS65_HEIGHT / IPHONE_HEIGHT; // 2688/2796 ≈ 0.961
const SCALE = Math.min(SCALE_X, SCALE_Y);

export const IosScreenWrapper: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => (
  <div
    style={{
      width: IOS65_WIDTH,
      height: IOS65_HEIGHT,
      overflow: "hidden",
      background: "#0A0A0B",
    }}
  >
    <div
      style={{
        transform: `scale(${SCALE})`,
        transformOrigin: "top left",
        width: IPHONE_WIDTH,
        height: IPHONE_HEIGHT,
      }}
    >
      {children}
    </div>
  </div>
);
