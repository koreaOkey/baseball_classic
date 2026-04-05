import React from "react";
import { staticFile } from "remotion";

/** 펭귄 득점 애니메이션 프레임 (frame 40/81) */
export const ScoreScreen: React.FC = () => (
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
      src={staticFile("penguin_score.jpg")}
      style={{
        width: "100%",
        height: "100%",
        objectFit: "cover",
      }}
    />
  </div>
);
