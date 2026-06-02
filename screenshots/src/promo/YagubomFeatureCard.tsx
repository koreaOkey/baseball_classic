import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";

type Feature = "live" | "events" | "themes";

const features: Record<
  Feature,
  {
    index: string;
    label: string;
    title: string;
    body: string;
    accent: string;
    accent2: string;
  }
> = {
  live: {
    index: "01",
    label: "실시간 경기",
    title: "휴대폰과 워치로\n경기 흐름을 바로 확인",
    body: "점수, 이닝, BSO 카운트와 오늘의 경기 진행 상황을 한눈에 볼 수 있어요.",
    accent: "#3B82F6",
    accent2: "#FACC15",
  },
  events: {
    index: "02",
    label: "순간 포착",
    title: "홈런·안타 순간엔\n펭귄이 함께 응원",
    body: "신나는 이벤트가 나오면 햅틱과 캐릭터 애니메이션으로 현장감을 더해요.",
    accent: "#F97316",
    accent2: "#FACC15",
  },
  themes: {
    index: "03",
    label: "테마 선택",
    title: "입맛에 맞는 테마로\n내 야구봄을 꾸미기",
    body: "응원 스타일에 맞는 캐릭터와 분위기를 고르고 폰과 워치에서 함께 즐겨요.",
    accent: "#22C55E",
    accent2: "#60A5FA",
  },
};

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

const header = (meta: (typeof features)[Feature]) => (
  <div
    style={{
      position: "absolute",
      left: 66,
      top: 58,
      right: 66,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
    }}
  >
    <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
      <Img
        src={staticFile("yagubom_app_icon.png")}
        style={{
          width: 84,
          height: 84,
          borderRadius: 24,
          boxShadow: "0 16px 40px rgba(0,0,0,0.44)",
        }}
      />
      <div>
        <div style={{ fontSize: 46, fontWeight: 950, letterSpacing: 0 }}>
          야구봄
        </div>
        <div
          style={{
            color: "rgba(255,255,255,0.62)",
            fontSize: 20,
            fontWeight: 800,
            marginTop: 2,
            letterSpacing: 0,
          }}
        >
          손목으로 느끼는 실시간 야구
        </div>
      </div>
    </div>
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "12px 18px",
        borderRadius: 999,
        background: "rgba(255,255,255,0.09)",
        border: "1px solid rgba(255,255,255,0.15)",
        color: meta.accent2,
        fontSize: 23,
        fontWeight: 950,
      }}
    >
      <span style={{ color: "rgba(255,255,255,0.56)" }}>{meta.index}</span>
      {meta.label}
    </div>
  </div>
);

const textBlock = (meta: (typeof features)[Feature]) => (
  <div style={{ position: "absolute", left: 66, top: 186, width: 742 }}>
    <div
      style={{
        whiteSpace: "pre-line",
        fontSize: 76,
        lineHeight: 1.06,
        fontWeight: 950,
        letterSpacing: 0,
      }}
    >
      {meta.title}
    </div>
    <div
      style={{
        marginTop: 26,
        width: 650,
        color: "rgba(255,255,255,0.76)",
        fontSize: 30,
        lineHeight: 1.38,
        fontWeight: 760,
        letterSpacing: 0,
      }}
    >
      {meta.body}
    </div>
  </div>
);

const phoneMock = (
  src: string,
  style: React.CSSProperties,
): React.ReactNode => (
  <div
    style={{
      position: "absolute",
      width: 348,
      height: 754,
      borderRadius: 58,
      padding: 14,
      background:
        "linear-gradient(145deg, rgba(255,255,255,0.36), rgba(255,255,255,0.05) 36%, rgba(0,0,0,0.94))",
      boxShadow: "0 30px 78px rgba(0,0,0,0.58)",
      ...style,
    }}
  >
    <div
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: 44,
        background: "#111",
      }}
    >
      <Img
        src={staticFile(src)}
        style={{ width: "100%", height: "100%", objectFit: "cover" }}
      />
    </div>
  </div>
);

const watchMock = (
  src: string,
  style: React.CSSProperties,
): React.ReactNode => (
  <div
    style={{
      position: "absolute",
      width: 298,
      height: 364,
      borderRadius: 82,
      padding: 14,
      background:
        "linear-gradient(145deg, rgba(255,255,255,0.26), rgba(19,19,23,1) 44%, rgba(0,0,0,1))",
      boxShadow: "0 28px 70px rgba(0,0,0,0.56)",
      ...style,
    }}
  >
    <div
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: 68,
        background: "#050505",
      }}
    >
      <Img
        src={staticFile(src)}
        style={{ width: "100%", height: "100%", objectFit: "cover" }}
      />
    </div>
  </div>
);

const themeTile = (
  src: string,
  name: string,
  style: React.CSSProperties,
): React.ReactNode => (
  <div
    style={{
      position: "absolute",
      width: 296,
      height: 368,
      borderRadius: 36,
      padding: 12,
      background: "rgba(255,255,255,0.1)",
      border: "1px solid rgba(255,255,255,0.14)",
      boxShadow: "0 30px 72px rgba(0,0,0,0.48)",
      ...style,
    }}
  >
    <Img
      src={staticFile(src)}
      style={{
        width: "100%",
        height: 250,
        objectFit: "cover",
        borderRadius: 28,
      }}
    />
    <div
      style={{
        marginTop: 16,
        fontSize: 27,
        fontWeight: 920,
        letterSpacing: 0,
      }}
    >
      {name}
    </div>
    <div
      style={{
        marginTop: 8,
        fontSize: 19,
        color: "rgba(255,255,255,0.58)",
        fontWeight: 760,
      }}
    >
      워치 테마
    </div>
  </div>
);

const bottomStrip = (meta: (typeof features)[Feature]) => (
  <div
    style={{
      position: "absolute",
      left: 66,
      right: 66,
      bottom: 58,
      height: 118,
      borderRadius: 32,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "0 34px",
      background: "rgba(255,255,255,0.1)",
      border: "1px solid rgba(255,255,255,0.15)",
      color: "rgba(255,255,255,0.86)",
      fontSize: 28,
      fontWeight: 900,
      letterSpacing: 0,
    }}
  >
    <span>야구 팬을 위한 실시간 응원 앱</span>
    <span style={{ color: meta.accent2 }}>야구봄</span>
  </div>
);

export const YagubomFeatureCard: React.FC<{ feature: Feature }> = ({
  feature,
}) => {
  const meta = features[feature];

  return (
    <AbsoluteFill style={base}>
      <div
        style={{
          position: "absolute",
          inset: 0,
          background: `radial-gradient(circle at 80% 18%, ${meta.accent}66 0, transparent 300px),
            radial-gradient(circle at 18% 88%, ${meta.accent2}44 0, transparent 330px),
            linear-gradient(145deg, #101114 0%, #07080B 50%, #111318 100%)`,
        }}
      />
      <div
        style={{
          position: "absolute",
          inset: 0,
          opacity: 0.14,
          backgroundImage:
            "radial-gradient(circle at 20% 20%, rgba(255,255,255,0.26) 0 1px, transparent 1px), radial-gradient(circle at 70% 60%, rgba(255,255,255,0.18) 0 1px, transparent 1px)",
          backgroundSize: "34px 34px, 46px 46px",
        }}
      />
      {header(meta)}
      {textBlock(meta)}

      {feature === "live" && (
        <>
          {phoneMock("promo_ios_home.png", {
            right: 112,
            top: 522,
            transform: "rotate(4deg)",
          })}
          {watchMock("promo_watch_live.png", {
            left: 118,
            top: 728,
            transform: "rotate(-8deg)",
          })}
        </>
      )}

      {feature === "events" && (
        <>
          <Img
            src={staticFile("penguin_hr.jpg")}
            style={{
              position: "absolute",
              left: 76,
              top: 622,
              width: 396,
              height: 396,
              borderRadius: 58,
              objectFit: "cover",
              boxShadow: "0 36px 90px rgba(0,0,0,0.55)",
              border: "1px solid rgba(255,255,255,0.18)",
            }}
          />
          <Img
            src={staticFile("penguin_hit.jpg")}
            style={{
              position: "absolute",
              right: 84,
              top: 716,
              width: 326,
              height: 326,
              borderRadius: 50,
              objectFit: "cover",
              boxShadow: "0 30px 74px rgba(0,0,0,0.5)",
              border: "1px solid rgba(255,255,255,0.18)",
            }}
          />
          {watchMock("promo_watch_homerun.png", {
            right: 122,
            top: 498,
            transform: "rotate(6deg) scale(0.78)",
            transformOrigin: "top right",
          })}
          {watchMock("promo_watch_hit.png", {
            left: 386,
            top: 902,
            transform: "rotate(-5deg) scale(0.64)",
            transformOrigin: "top left",
          })}
        </>
      )}

      {feature === "themes" && (
        <>
          {themeTile("theme_baseball_love.png", "야구가 좋아", {
            left: 92,
            top: 610,
            transform: "rotate(-5deg)",
          })}
          {themeTile("theme_puppy.png", "멍멍이", {
            left: 388,
            top: 560,
            transform: "rotate(3deg)",
          })}
          {themeTile("theme_puppy2.png", "멍멍이 2", {
            right: 86,
            top: 654,
            transform: "rotate(6deg)",
          })}
          <div
            style={{
              position: "absolute",
              left: 390,
              top: 970,
              padding: "16px 24px",
              borderRadius: 999,
              background: `${meta.accent}33`,
              border: `2px solid ${meta.accent}`,
              color: "white",
              fontSize: 25,
              fontWeight: 920,
              letterSpacing: 0,
              boxShadow: `0 0 42px ${meta.accent}55`,
            }}
          >
            적용 중
          </div>
        </>
      )}

      {bottomStrip(meta)}
    </AbsoluteFill>
  );
};
