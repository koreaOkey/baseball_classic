import React from "react";
import { staticFile } from "remotion";

/** 펭귄 홈런 애니메이션 프레임 (frame 50/101) */
export const HomeRunScreen: React.FC = () => (
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
      src={staticFile("penguin_hr.jpg")}
      style={{
        width: "100%",
        height: "100%",
        objectFit: "cover",
        marginLeft: -20,
      }}
    />
  </div>
);
