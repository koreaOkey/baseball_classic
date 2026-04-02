import React from "react";
import { staticFile } from "remotion";
import { appColors, PHONE_WIDTH, PHONE_HEIGHT } from "./theme";
import { WearOsFrame } from "../components/WearOsFrame";

const font =
  '-apple-system, "SF Pro Display", "Helvetica Neue", sans-serif';

/** Wear OS 워치 화면 미리보기 - 3개 화면 showcase */
export const PhoneScreenshot4_WatchPreview: React.FC = () => (
  <div
    style={{
      width: PHONE_WIDTH,
      height: PHONE_HEIGHT,
      background: `linear-gradient(180deg, #0a0a12 0%, #111128 50%, #0a0a12 100%)`,
      fontFamily: font,
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      gap: 60,
      overflow: "hidden",
    }}
  >
    {/* Title */}
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 12,
      }}
    >
      <span style={{ fontSize: 44, fontWeight: 800, color: "white" }}>
        워치에서 실시간 중계
      </span>
      <span style={{ fontSize: 22, color: appColors.gray400, textAlign: "center", lineHeight: 1.5 }}>
        Wear OS에서 경기 상황을 한눈에 확인하고{"\n"}
        진동으로 주요 이벤트를 놓치지 마세요
      </span>
    </div>

    {/* Center watch - large */}
    <WearOsFrame size={380}>
      <img
        src={staticFile("wearos_2.png")}
        style={{ width: 380, height: 380, objectFit: "cover" }}
      />
    </WearOsFrame>

    {/* Bottom row - two smaller watches */}
    <div style={{ display: "flex", gap: 40, alignItems: "center" }}>
      <WearOsFrame size={240}>
        <img
          src={staticFile("wearos_1.png")}
          style={{ width: 240, height: 240, objectFit: "cover" }}
        />
      </WearOsFrame>
      <WearOsFrame size={240}>
        <img
          src={staticFile("wearos_3.png")}
          style={{ width: 240, height: 240, objectFit: "cover" }}
        />
      </WearOsFrame>
    </div>

    {/* Feature badges */}
    <div style={{ display: "flex", gap: 16 }}>
      {["실시간 스코어", "햅틱 알림", "이벤트 오버레이"].map((label) => (
        <div
          key={label}
          style={{
            background: "rgba(59,130,246,0.15)",
            border: "1px solid rgba(59,130,246,0.3)",
            borderRadius: 40,
            padding: "12px 24px",
          }}
        >
          <span style={{ fontSize: 18, color: appColors.blue400, fontWeight: 500 }}>
            {label}
          </span>
        </div>
      ))}
    </div>
  </div>
);
