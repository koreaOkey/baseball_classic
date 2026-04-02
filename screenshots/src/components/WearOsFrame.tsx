import React from "react";

/** Wear OS 원형 워치 프레임 */
export const WearOsFrame: React.FC<{
  children: React.ReactNode;
  size?: number;
}> = ({ children, size = 280 }) => {
  const bezel = 10;
  const outerSize = size + bezel * 2;

  return (
    <div
      style={{
        width: outerSize,
        height: outerSize,
        borderRadius: "50%",
        background: "#1a1a1a",
        padding: bezel,
        boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
        border: "1.5px solid rgba(255,255,255,0.1)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        style={{
          width: size,
          height: size,
          borderRadius: "50%",
          overflow: "hidden",
          position: "relative",
        }}
      >
        {children}
      </div>
    </div>
  );
};
