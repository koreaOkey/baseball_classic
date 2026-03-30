import React from "react";
import { WatchFrame } from "../components/WatchFrame";

/** 공통 레이아웃: 검은 배경 + 중앙 워치 프레임 */
export const ScreenshotWrapper: React.FC<{
  caption: string;
  children: React.ReactNode;
}> = ({ caption, children }) => (
  <div
    style={{
      width: "100%",
      height: "100%",
      background: "linear-gradient(180deg, #0a0a0b 0%, #111114 100%)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: 20,
    }}
  >
    <WatchFrame caption={caption}>{children}</WatchFrame>
  </div>
);
