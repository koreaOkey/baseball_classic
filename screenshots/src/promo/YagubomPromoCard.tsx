import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";

type Platform = "ios" | "android";

const platformMeta: Record<
  Platform,
  {
    label: string;
    eyebrow: string;
    qr: string;
    primaryShot: string;
    secondaryShot: string;
    accent: string;
    accent2: string;
    cta: string;
  }
> = {
  ios: {
    label: "iOS",
    eyebrow: "iPhone + Apple Watch",
    qr: "qr_ios.jpg",
    primaryShot: "promo_ios_home.png",
    secondaryShot: "promo_ios_sync.png",
    accent: "#3B82F6",
    accent2: "#FACC15",
    cta: "iOS용 QR로 만나보기",
  },
  android: {
    label: "Android",
    eyebrow: "Android + Wear OS",
    qr: "qr_android.jpg",
    primaryShot: "promo_android_home.png",
    secondaryShot: "promo_android_watch.png",
    accent: "#22C55E",
    accent2: "#F97316",
    cta: "Android용 QR로 만나보기",
  },
};

const bulletCopy = [
  "홈런·안타·득점 순간을 실시간 햅틱으로",
  "응원 팀 경기 시작과 함께 워치 동기화",
  "펭귄 캐릭터 애니메이션으로 더 크게 응원",
];

const card: React.CSSProperties = {
  width: 1080,
  height: 1350,
  position: "relative",
  overflow: "hidden",
  background: "#07080B",
  color: "white",
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "SF Pro Display", "Apple SD Gothic Neo", "Noto Sans KR", sans-serif',
};

const noise: React.CSSProperties = {
  position: "absolute",
  inset: 0,
  opacity: 0.16,
  backgroundImage:
    "radial-gradient(circle at 20% 20%, rgba(255,255,255,0.26) 0 1px, transparent 1px), radial-gradient(circle at 70% 60%, rgba(255,255,255,0.18) 0 1px, transparent 1px)",
  backgroundSize: "34px 34px, 46px 46px",
};

const phoneFrame = (
  src: string,
  style: React.CSSProperties,
  platform: Platform,
): React.ReactNode => (
  <div
    style={{
      position: "absolute",
      width: 338,
      height: 734,
      borderRadius: platform === "ios" ? 58 : 42,
      padding: 14,
      background:
        "linear-gradient(145deg, rgba(255,255,255,0.38), rgba(255,255,255,0.05) 36%, rgba(0,0,0,0.9))",
      boxShadow:
        "0 32px 80px rgba(0,0,0,0.62), inset 0 0 0 1px rgba(255,255,255,0.22)",
      ...style,
    }}
  >
    <div
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: platform === "ios" ? 44 : 30,
        background: "#111",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.08)",
      }}
    >
      <Img
        src={staticFile(src)}
        style={{
          width: "100%",
          height: "100%",
          objectFit: "cover",
          display: "block",
        }}
      />
    </div>
  </div>
);

const watchFrame = (
  src: string,
  style: React.CSSProperties,
): React.ReactNode => (
  <div
    style={{
      position: "absolute",
      width: 276,
      height: 338,
      borderRadius: 78,
      padding: 14,
      background:
        "linear-gradient(145deg, rgba(255,255,255,0.28), rgba(20,20,24,1) 44%, rgba(0,0,0,1))",
      boxShadow: "0 28px 70px rgba(0,0,0,0.58)",
      ...style,
    }}
  >
    <div
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: 64,
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

export const YagubomPromoCard: React.FC<{ platform: Platform }> = ({
  platform,
}) => {
  const meta = platformMeta[platform];
  const secondaryIsWatch = platform === "android";

  return (
    <AbsoluteFill style={card}>
      <div
        style={{
          position: "absolute",
          inset: 0,
          background: `radial-gradient(circle at 80% 16%, ${meta.accent}66 0, transparent 290px),
            radial-gradient(circle at 18% 90%, ${meta.accent2}52 0, transparent 330px),
            linear-gradient(145deg, #101114 0%, #07080B 48%, #111318 100%)`,
        }}
      />
      <div style={noise} />

      <div
        style={{
          position: "absolute",
          left: 62,
          top: 54,
          display: "flex",
          alignItems: "center",
          gap: 18,
        }}
      >
        <Img
          src={staticFile("yagubom_app_icon.png")}
          style={{
            width: 96,
            height: 96,
            borderRadius: 26,
            boxShadow: "0 16px 42px rgba(0,0,0,0.48)",
          }}
        />
        <div style={{ display: "flex", flexDirection: "column", gap: 9 }}>
          <div
            style={{
              color: "rgba(255,255,255,0.72)",
              fontSize: 25,
              fontWeight: 800,
              letterSpacing: 0,
            }}
          >
            {meta.eyebrow}
          </div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 14 }}>
            <div style={{ fontSize: 68, fontWeight: 900, letterSpacing: 0 }}>
              야구봄
            </div>
            <div
              style={{
                color: meta.accent2,
                fontSize: 23,
                fontWeight: 900,
                padding: "8px 14px",
                borderRadius: 999,
                background: "rgba(255,255,255,0.09)",
                border: "1px solid rgba(255,255,255,0.14)",
              }}
            >
              {meta.label}
            </div>
          </div>
        </div>
      </div>

      <div style={{ position: "absolute", left: 66, top: 210, width: 680 }}>
        <div
          style={{
            fontSize: 76,
            lineHeight: 1.04,
            fontWeight: 950,
            letterSpacing: 0,
          }}
        >
          못 보는 순간에도
          <br />
          경기는 손목에 온다
        </div>
        <div
          style={{
            marginTop: 24,
            color: "rgba(255,255,255,0.76)",
            fontSize: 31,
            lineHeight: 1.35,
            fontWeight: 700,
            letterSpacing: 0,
            width: 560,
          }}
        >
          야구의 결정적 장면을 햅틱과 캐릭터 애니메이션으로 바로 느껴보세요.
        </div>
      </div>

      <div
        style={{
          position: "absolute",
          left: 66,
          top: 532,
          display: "flex",
          flexDirection: "column",
          gap: 18,
        }}
      >
        {bulletCopy.map((copy) => (
          <div
            key={copy}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 15,
              fontSize: 27,
              fontWeight: 800,
              color: "rgba(255,255,255,0.9)",
              letterSpacing: 0,
            }}
          >
            <span
              style={{
                width: 14,
                height: 14,
                borderRadius: 999,
                background: meta.accent2,
                boxShadow: `0 0 28px ${meta.accent2}`,
                flexShrink: 0,
              }}
            />
            {copy}
          </div>
        ))}
      </div>

      {phoneFrame(meta.primaryShot, { right: 76, top: 338, transform: "rotate(4deg)" }, platform)}
      {secondaryIsWatch
        ? watchFrame(meta.secondaryShot, {
            right: 360,
            top: 730,
            transform: "rotate(-8deg)",
          })
        : phoneFrame(meta.secondaryShot, {
            right: 388,
            top: 708,
            transform: "rotate(-8deg) scale(0.66)",
            transformOrigin: "top left",
          }, platform)}

      <div
        style={{
          position: "absolute",
          left: 68,
          bottom: 250,
          display: "flex",
          gap: 22,
          alignItems: "center",
        }}
      >
        <Img
          src={staticFile("penguin_hr.jpg")}
          style={{
            width: 154,
            height: 154,
            borderRadius: 38,
            objectFit: "cover",
            boxShadow: "0 22px 52px rgba(0,0,0,0.45)",
            border: "1px solid rgba(255,255,255,0.18)",
          }}
        />
        <Img
          src={staticFile("penguin_hit.jpg")}
          style={{
            width: 154,
            height: 154,
            borderRadius: 38,
            objectFit: "cover",
            boxShadow: "0 22px 52px rgba(0,0,0,0.45)",
            border: "1px solid rgba(255,255,255,0.18)",
          }}
        />
      </div>

      <div
        style={{
          position: "absolute",
          left: 66,
          right: 66,
          bottom: 58,
          height: 158,
          borderRadius: 34,
          background: "rgba(255,255,255,0.1)",
          border: "1px solid rgba(255,255,255,0.16)",
          boxShadow: "0 24px 80px rgba(0,0,0,0.35)",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "22px 24px 22px 36px",
          backdropFilter: "blur(16px)",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: 9 }}>
          <div
            style={{
              fontSize: 35,
              fontWeight: 950,
              letterSpacing: 0,
              color: "white",
            }}
          >
            지금 바로 야구봄 시작하기
          </div>
          <div
            style={{
              fontSize: 23,
              fontWeight: 800,
              color: "rgba(255,255,255,0.7)",
              letterSpacing: 0,
            }}
          >
            {meta.cta}
          </div>
        </div>
        <div
          style={{
            width: 124,
            height: 124,
            borderRadius: 24,
            background: "white",
            padding: 8,
            boxShadow: `0 0 0 5px ${meta.accent}44`,
          }}
        >
          <Img
            src={staticFile(meta.qr)}
            style={{
              width: "100%",
              height: "100%",
              objectFit: "cover",
              display: "block",
              borderRadius: 12,
            }}
          />
        </div>
      </div>
    </AbsoluteFill>
  );
};
