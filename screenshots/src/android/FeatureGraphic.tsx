import React from "react";
import { staticFile } from "remotion";
import { appColors, myTeam, FEATURE_WIDTH, FEATURE_HEIGHT } from "./theme";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

/** Android Play Store Feature Graphic - 1024x500 */
export const FeatureGraphic: React.FC = () => (
  <div
    style={{
      width: FEATURE_WIDTH,
      height: FEATURE_HEIGHT,
      background: `linear-gradient(135deg, #0a0a12 0%, #111128 40%, #0a0a12 100%)`,
      fontFamily: font,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      position: "relative",
      overflow: "hidden",
    }}
  >
    {/* Subtle radial glow */}
    <div
      style={{
        position: "absolute",
        width: 600,
        height: 600,
        borderRadius: "50%",
        background: "radial-gradient(circle, rgba(59,130,246,0.15) 0%, transparent 70%)",
        top: -100,
        right: -50,
      }}
    />

    {/* Left side: App info */}
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: 16,
        paddingLeft: 80,
        flex: 1,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
        <img
          src={staticFile("ic_launcher.png")}
          style={{
            width: 72,
            height: 72,
            borderRadius: 16,
          }}
        />
        <span style={{ fontSize: 20, color: appColors.gray400, fontWeight: 500 }}>
          BaseHaptic
        </span>
      </div>
      <span
        style={{
          fontSize: 48,
          fontWeight: 800,
          color: "white",
          lineHeight: 1.2,
        }}
      >
        야구봄
      </span>
      <span
        style={{
          fontSize: 18,
          color: appColors.gray400,
          lineHeight: 1.5,
          maxWidth: 340,
        }}
      >
        워치에서 느끼는 실시간 야구 중계
        {"\n"}터치 한 번으로 경기 시작
      </span>
    </div>

    {/* Right side: Watch mockups */}
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 20,
        paddingRight: 60,
      }}
    >
      {[
        { src: "wearos_1.png", size: 160, y: 20 },
        { src: "wearos_2.png", size: 200, y: -10 },
        { src: "wearos_3.png", size: 160, y: 30 },
      ].map((img, i) => (
        <div
          key={i}
          style={{
            width: img.size,
            height: img.size,
            borderRadius: "50%",
            overflow: "hidden",
            border: "3px solid rgba(255,255,255,0.12)",
            boxShadow: "0 8px 24px rgba(0,0,0,0.5)",
            marginTop: img.y,
          }}
        >
          <img
            src={staticFile(img.src)}
            style={{
              width: img.size,
              height: img.size,
              objectFit: "cover",
            }}
          />
        </div>
      ))}
    </div>
  </div>
);
