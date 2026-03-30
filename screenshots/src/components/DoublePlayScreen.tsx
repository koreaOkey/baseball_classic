import React from "react";
import { staticFile } from "remotion";

/** 실제 병살 아웃 애니메이션 프레임 */
export const DoublePlayScreen: React.FC = () => (
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
      src={staticFile("doubleplay.jpg")}
      style={{
        width: "100%",
        height: "100%",
        objectFit: "cover",
      }}
    />
  </div>
);
