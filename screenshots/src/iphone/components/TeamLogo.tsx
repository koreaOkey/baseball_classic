import React from "react";
import { staticFile } from "remotion";

export const TeamLogo: React.FC<{
  logo: string;
  bgColor: string;
  size?: number;
}> = ({ logo, bgColor, size = 80 }) => (
  <div
    style={{
      width: size,
      height: size,
      borderRadius: size / 2,
      background: bgColor,
      overflow: "hidden",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      flexShrink: 0,
    }}
  >
    <img
      src={staticFile(logo)}
      style={{ width: size * 0.7, height: size * 0.7, objectFit: "contain" }}
    />
  </div>
);
