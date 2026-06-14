import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";

const asset = (name: string) => staticFile(`live-score-update/${name}`);

const base: React.CSSProperties = {
  width: 1080,
  height: 1350,
  position: "relative",
  overflow: "hidden",
  background: "#07080B",
  color: "white",
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "SF Pro Display", "Apple SD Gothic Neo", "Noto Sans KR", sans-serif',
};

const bg: React.CSSProperties = {
  position: "absolute",
  inset: 0,
  background:
    "linear-gradient(128deg, #0A0B0F 0%, #101217 45%, #07080B 100%)",
};

const fieldLines: React.CSSProperties = {
  position: "absolute",
  inset: 0,
  opacity: 0.42,
  backgroundImage:
    "linear-gradient(115deg, transparent 0 42%, rgba(250,204,21,0.12) 42% 43%, transparent 43% 100%), linear-gradient(65deg, transparent 0 54%, rgba(34,197,94,0.12) 54% 55%, transparent 55% 100%), repeating-linear-gradient(0deg, transparent 0 80px, rgba(255,255,255,0.035) 80px 82px)",
};

const topBar: React.CSSProperties = {
  position: "absolute",
  left: 62,
  top: 56,
  right: 62,
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
};

const titleBlock: React.CSSProperties = {
  position: "absolute",
  left: 62,
  top: 184,
  width: 790,
};

const phoneFrame = (
  src: string,
  style: React.CSSProperties,
  options?: {
    radius?: number;
    innerRadius?: number;
    objectPosition?: string;
    objectFit?: React.CSSProperties["objectFit"];
  },
) => (
  <div
    style={{
      position: "absolute",
      borderRadius: options?.radius ?? 58,
      padding: 13,
      background:
        "linear-gradient(145deg, rgba(255,255,255,0.42), rgba(255,255,255,0.06) 34%, rgba(0,0,0,0.95))",
      boxShadow:
        "0 34px 82px rgba(0,0,0,0.62), inset 0 0 0 1px rgba(255,255,255,0.2)",
      ...style,
    }}
  >
    <div
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: options?.innerRadius ?? 45,
        background: "#111217",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.08)",
      }}
    >
      <Img
        src={asset(src)}
        style={{
          width: "100%",
          height: "100%",
          objectFit: options?.objectFit ?? "cover",
          objectPosition: options?.objectPosition ?? "50% 50%",
          display: "block",
        }}
      />
    </div>
  </div>
);

const captureCard = ({
  title,
  label,
  src,
  style,
  objectPosition,
}: {
  title: string;
  label: string;
  src: string;
  style: React.CSSProperties;
  objectPosition: string;
}) => (
  <div
    style={{
      position: "absolute",
      width: 398,
      borderRadius: 36,
      padding: 13,
      background: "rgba(255,255,255,0.09)",
      border: "1px solid rgba(255,255,255,0.16)",
      boxShadow: "0 26px 70px rgba(0,0,0,0.45)",
      ...style,
    }}
  >
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: 12,
      }}
    >
      <div style={{ fontSize: 27, fontWeight: 930, letterSpacing: 0 }}>
        {title}
      </div>
      <div
        style={{
          padding: "8px 13px",
          borderRadius: 999,
          background: "rgba(250,204,21,0.16)",
          border: "1px solid rgba(250,204,21,0.34)",
          color: "#FACC15",
          fontSize: 18,
          fontWeight: 900,
          letterSpacing: 0,
        }}
      >
        {label}
      </div>
    </div>
    <div
      style={{
        width: "100%",
        height: 236,
        overflow: "hidden",
        borderRadius: 24,
        background: "#111",
      }}
    >
      <Img
        src={asset(src)}
        style={{
          width: "100%",
          height: "100%",
          objectFit: "cover",
          objectPosition,
          display: "block",
        }}
      />
    </div>
  </div>
);

const feature = (text: string, accent: string) => (
  <div
    key={text}
    style={{
      display: "flex",
      alignItems: "center",
      gap: 13,
      fontSize: 24,
      fontWeight: 850,
      color: "rgba(255,255,255,0.88)",
      letterSpacing: 0,
    }}
  >
    <span
      style={{
        width: 15,
        height: 15,
        borderRadius: 999,
        background: accent,
        boxShadow: `0 0 22px ${accent}80`,
        flexShrink: 0,
      }}
    />
    {text}
  </div>
);

export const LiveScoreUpdatePromo: React.FC = () => {
  return (
    <AbsoluteFill style={base}>
      <div style={bg} />
      <div style={fieldLines} />

      <div style={topBar}>
        <div style={{ display: "flex", alignItems: "center", gap: 17 }}>
          <Img
            src={staticFile("yagubom_app_icon.png")}
            style={{
              width: 86,
              height: 86,
              borderRadius: 24,
              boxShadow: "0 18px 44px rgba(0,0,0,0.5)",
            }}
          />
          <div>
            <div style={{ fontSize: 44, fontWeight: 960, letterSpacing: 0 }}>
              야구봄
            </div>
            <div
              style={{
                marginTop: 4,
                color: "rgba(255,255,255,0.62)",
                fontSize: 20,
                fontWeight: 800,
                letterSpacing: 0,
              }}
            >
              실시간 야구 중계 업데이트
            </div>
          </div>
        </div>
        <div
          style={{
            padding: "14px 20px",
            borderRadius: 999,
            background: "rgba(239,68,68,0.16)",
            border: "1px solid rgba(239,68,68,0.38)",
            color: "#FF5A5F",
            fontSize: 24,
            fontWeight: 950,
            letterSpacing: 0,
          }}
        >
          NEW
        </div>
      </div>

      <div style={titleBlock}>
        <div
          style={{
            fontSize: 72,
            lineHeight: 1.05,
            fontWeight: 980,
            letterSpacing: 0,
          }}
        >
          이제부터 경기를
          <br />
          놓치지 마세요
        </div>
        <div
          style={{
            marginTop: 21,
            width: 920,
            color: "rgba(255,255,255,0.74)",
            fontSize: 27,
            lineHeight: 1.36,
            fontWeight: 760,
            letterSpacing: 0,
          }}
        >
          전체 상세 진행 상황부터 잠금화면, 다이나믹 아일랜드까지
          <br />
          일상생활의 모든 곳에서 보고 싶은 경기를 볼 수 있어요.
        </div>
      </div>

      {phoneFrame("detail-events.png", {
        width: 238,
        height: 516,
        left: 70,
        top: 606,
        opacity: 0.76,
        transform: "rotate(-5deg)",
      })}

      {phoneFrame("detail-field.png", {
        width: 314,
        height: 681,
        left: 236,
        top: 488,
        transform: "rotate(-1deg)",
      })}

      <div
        style={{
          position: "absolute",
          left: 276,
          top: 458,
          padding: "11px 17px",
          borderRadius: 999,
          background: "#FACC15",
          color: "#111113",
          fontSize: 22,
          fontWeight: 950,
          boxShadow: "0 18px 40px rgba(250,204,21,0.28)",
          letterSpacing: 0,
        }}
      >
        경기 전체 상세
      </div>

      {captureCard({
        title: "잠금화면",
        label: "LIVE",
        src: "lock-screen.png",
        objectPosition: "50% 100%",
        style: { right: 62, top: 504 },
      })}

      {captureCard({
        title: "다이나믹 아일랜드",
        label: "iOS",
        src: "dynamic-island.png",
        objectPosition: "50% 0%",
        style: { right: 62, top: 830 },
      })}

      <div
        style={{
          position: "absolute",
          left: 62,
          right: 62,
          bottom: 64,
          minHeight: 132,
          borderRadius: 36,
          padding: "30px 34px",
          background: "rgba(255,255,255,0.08)",
          border: "1px solid rgba(255,255,255,0.14)",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 28,
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: 15 }}>
          {feature("타석별 실시간 진행 상황", "#22C55E")}
          {feature("잠금화면 라이브 스코어", "#FACC15")}
          {feature("다이나믹 아일랜드 점수 확인", "#FF5A5F")}
        </div>
        <div
          style={{
            width: 238,
            textAlign: "right",
            color: "rgba(255,255,255,0.72)",
            fontSize: 24,
            lineHeight: 1.34,
            fontWeight: 820,
            letterSpacing: 0,
          }}
        >
          폰을 열지 않아도
          <br />
          지금 경기를 바로 확인
        </div>
      </div>
    </AbsoluteFill>
  );
};
