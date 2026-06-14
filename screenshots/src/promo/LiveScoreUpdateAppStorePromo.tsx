import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";

type Orientation = "portrait" | "landscape";

const asset = (name: string) => staticFile(`live-score-update/${name}`);

const fontFamily =
  '-apple-system, BlinkMacSystemFont, "SF Pro Display", "Apple SD Gothic Neo", "Noto Sans KR", sans-serif';

const Background: React.FC = () => (
  <>
    <div
      style={{
        position: "absolute",
        inset: 0,
        background:
          "linear-gradient(128deg, #0A0B0F 0%, #101217 45%, #07080B 100%)",
      }}
    />
    <div
      style={{
        position: "absolute",
        inset: 0,
        opacity: 0.42,
        backgroundImage:
          "linear-gradient(115deg, transparent 0 42%, rgba(250,204,21,0.12) 42% 43%, transparent 43% 100%), linear-gradient(65deg, transparent 0 54%, rgba(34,197,94,0.12) 54% 55%, transparent 55% 100%), repeating-linear-gradient(0deg, transparent 0 96px, rgba(255,255,255,0.035) 96px 98px)",
      }}
    />
  </>
);

const Header: React.FC<{ landscape?: boolean }> = ({ landscape }) => (
  <div
    style={{
      position: "absolute",
      left: landscape ? 94 : 76,
      top: landscape ? 68 : 88,
      right: landscape ? 94 : 76,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
    }}
  >
    <div style={{ display: "flex", alignItems: "center", gap: 22 }}>
      <Img
        src={staticFile("yagubom_app_icon.png")}
        style={{
          width: landscape ? 96 : 118,
          height: landscape ? 96 : 118,
          borderRadius: landscape ? 27 : 33,
          boxShadow: "0 20px 50px rgba(0,0,0,0.5)",
        }}
      />
      <div>
        <div
          style={{
            fontSize: landscape ? 50 : 60,
            fontWeight: 960,
            letterSpacing: 0,
          }}
        >
          야구봄
        </div>
        <div
          style={{
            marginTop: 5,
            color: "rgba(255,255,255,0.62)",
            fontSize: landscape ? 23 : 28,
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
        padding: landscape ? "15px 24px" : "18px 28px",
        borderRadius: 999,
        background: "rgba(239,68,68,0.16)",
        border: "1px solid rgba(239,68,68,0.38)",
        color: "#FF5A5F",
        fontSize: landscape ? 28 : 32,
        fontWeight: 950,
        letterSpacing: 0,
      }}
    >
      NEW
    </div>
  </div>
);

const PhoneFrame = ({
  src,
  style,
  objectPosition = "50% 50%",
  radius = 74,
  innerRadius = 56,
}: {
  src: string;
  style: React.CSSProperties;
  objectPosition?: string;
  radius?: number;
  innerRadius?: number;
}) => (
  <div
    style={{
      position: "absolute",
      borderRadius: radius,
      padding: 16,
      background:
        "linear-gradient(145deg, rgba(255,255,255,0.42), rgba(255,255,255,0.06) 34%, rgba(0,0,0,0.95))",
      boxShadow:
        "0 40px 96px rgba(0,0,0,0.62), inset 0 0 0 1px rgba(255,255,255,0.2)",
      ...style,
    }}
  >
    <div
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        borderRadius: innerRadius,
        background: "#111217",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.08)",
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

const CaptureCard = ({
  title,
  label,
  src,
  style,
  objectPosition,
  width,
  height,
}: {
  title: string;
  label: string;
  src: string;
  style: React.CSSProperties;
  objectPosition: string;
  width: number;
  height: number;
}) => (
  <div
    style={{
      position: "absolute",
      width,
      borderRadius: 40,
      padding: 16,
      background: "rgba(255,255,255,0.09)",
      border: "1px solid rgba(255,255,255,0.16)",
      boxShadow: "0 32px 80px rgba(0,0,0,0.45)",
      ...style,
    }}
  >
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: 16,
      }}
    >
      <div style={{ fontSize: 34, fontWeight: 930, letterSpacing: 0 }}>
        {title}
      </div>
      <div
        style={{
          padding: "10px 16px",
          borderRadius: 999,
          background: "rgba(250,204,21,0.16)",
          border: "1px solid rgba(250,204,21,0.34)",
          color: "#FACC15",
          fontSize: 22,
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
        height,
        overflow: "hidden",
        borderRadius: 28,
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

const Feature = ({
  text,
  accent,
  landscape,
}: {
  text: string;
  accent: string;
  landscape?: boolean;
}) => (
  <div
    style={{
      display: "flex",
      alignItems: "center",
      gap: landscape ? 15 : 18,
      fontSize: landscape ? 30 : 34,
      fontWeight: 850,
      color: "rgba(255,255,255,0.9)",
      letterSpacing: 0,
    }}
  >
    <span
      style={{
        width: landscape ? 18 : 20,
        height: landscape ? 18 : 20,
        borderRadius: 999,
        background: accent,
        boxShadow: `0 0 26px ${accent}80`,
        flexShrink: 0,
      }}
    />
    {text}
  </div>
);

const CopyBlock: React.FC<{ landscape?: boolean }> = ({ landscape }) => (
  <div
    style={{
      position: "absolute",
      left: landscape ? 94 : 76,
      top: landscape ? 235 : 300,
      width: landscape ? 1040 : 1080,
    }}
  >
    <div
      style={{
        fontSize: landscape ? 90 : 100,
        lineHeight: 1.06,
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
        marginTop: landscape ? 28 : 34,
        width: landscape ? 1280 : 1080,
        color: "rgba(255,255,255,0.74)",
        fontSize: landscape ? 34 : 36,
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
);

const PortraitLayout: React.FC = () => (
  <>
    <Header />
    <CopyBlock />

    <PhoneFrame
      src="detail-events.png"
      style={{
        width: 300,
        height: 650,
        left: 68,
        top: 1095,
        opacity: 0.76,
        transform: "rotate(-5deg)",
      }}
    />
    <PhoneFrame
      src="detail-field.png"
      style={{
        width: 462,
        height: 1002,
        left: 214,
        top: 805,
        transform: "rotate(-1deg)",
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 278,
        top: 767,
        padding: "17px 28px",
        borderRadius: 999,
        background: "#FACC15",
        color: "#111113",
        fontSize: 33,
        fontWeight: 950,
        boxShadow: "0 22px 52px rgba(250,204,21,0.28)",
        letterSpacing: 0,
      }}
    >
      경기 전체 상세
    </div>

    <CaptureCard
      title="잠금화면"
      label="LIVE"
      src="lock-screen.png"
      objectPosition="50% 100%"
      width={465}
      height={286}
      style={{ right: 76, top: 1115 }}
    />
    <CaptureCard
      title="다이나믹 아일랜드"
      label="iOS"
      src="dynamic-island.png"
      objectPosition="50% 0%"
      width={465}
      height={286}
      style={{ right: 76, top: 1490 }}
    />

    <div
      style={{
        position: "absolute",
        left: 76,
        right: 76,
        bottom: 92,
        minHeight: 260,
        borderRadius: 48,
        padding: "45px 50px",
        background: "rgba(255,255,255,0.08)",
        border: "1px solid rgba(255,255,255,0.14)",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
        <Feature text="타석별 실시간 진행 상황" accent="#22C55E" />
        <Feature text="잠금화면 라이브 스코어" accent="#FACC15" />
        <Feature text="다이나믹 아일랜드 점수 확인" accent="#FF5A5F" />
      </div>
      <div
        style={{
          width: 345,
          textAlign: "right",
          color: "rgba(255,255,255,0.72)",
          fontSize: 34,
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
  </>
);

const LandscapeLayout: React.FC = () => (
  <>
    <Header landscape />
    <CopyBlock landscape />

    <div
      style={{
        position: "absolute",
        left: 94,
        bottom: 86,
        width: 955,
        minHeight: 210,
        borderRadius: 44,
        padding: "38px 44px",
        background: "rgba(255,255,255,0.08)",
        border: "1px solid rgba(255,255,255,0.14)",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        gap: 22,
      }}
    >
      <Feature text="타석별 실시간 진행 상황" accent="#22C55E" landscape />
      <Feature text="잠금화면 라이브 스코어" accent="#FACC15" landscape />
      <Feature text="다이나믹 아일랜드 점수 확인" accent="#FF5A5F" landscape />
    </div>

    <PhoneFrame
      src="detail-events.png"
      style={{
        width: 312,
        height: 676,
        left: 1120,
        top: 394,
        opacity: 0.74,
        transform: "rotate(-5deg)",
      }}
    />
    <PhoneFrame
      src="detail-field.png"
      style={{
        width: 430,
        height: 932,
        left: 1392,
        top: 192,
        transform: "rotate(-1deg)",
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 1476,
        top: 154,
        padding: "15px 24px",
        borderRadius: 999,
        background: "#FACC15",
        color: "#111113",
        fontSize: 30,
        fontWeight: 950,
        boxShadow: "0 22px 52px rgba(250,204,21,0.28)",
        letterSpacing: 0,
      }}
    >
      경기 전체 상세
    </div>

    <CaptureCard
      title="잠금화면"
      label="LIVE"
      src="lock-screen.png"
      objectPosition="50% 100%"
      width={615}
      height={330}
      style={{ right: 94, top: 222 }}
    />
    <CaptureCard
      title="다이나믹 아일랜드"
      label="iOS"
      src="dynamic-island.png"
      objectPosition="50% 0%"
      width={615}
      height={330}
      style={{ right: 94, top: 660 }}
    />

    <div
      style={{
        position: "absolute",
        right: 102,
        bottom: 70,
        width: 570,
        textAlign: "right",
        color: "rgba(255,255,255,0.74)",
        fontSize: 38,
        lineHeight: 1.35,
        fontWeight: 850,
        letterSpacing: 0,
      }}
    >
      폰을 열지 않아도
      <br />
      지금 경기를 바로 확인
    </div>
  </>
);

export const LiveScoreUpdateAppStorePromo: React.FC<{
  orientation: Orientation;
}> = ({ orientation }) => {
  const landscape = orientation === "landscape";

  return (
    <AbsoluteFill
      style={{
        position: "relative",
        overflow: "hidden",
        background: "#07080B",
        color: "white",
        fontFamily,
      }}
    >
      <Background />
      {landscape ? <LandscapeLayout /> : <PortraitLayout />}
    </AbsoluteFill>
  );
};
