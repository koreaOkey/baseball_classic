import React from "react";
import { staticFile } from "remotion";

/** 펭귄 승리 애니메이션 프레임 (frame 40/81) */
export const VictoryScreen: React.FC = () => (
  <div
    style={{
      width: "100%",
      height: "100%",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      background: "black",
      overflow: "hidden",
    }}
  >
    <img
      src={staticFile("penguin_victory.jpg")}
      style={{
        width: "100%",
        height: "100%",
        objectFit: "cover",
      }}
    />
  </div>
);
