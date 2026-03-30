import React from "react";
import { staticFile } from "remotion";

/** 실제 홈런 애니메이션 프레임 */
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
      src={staticFile("homerun_new.jpg")}
      style={{
        width: "100%",
        height: "100%",
        objectFit: "cover",
      }}
    />
  </div>
);
