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
          "linear-gradient(115deg, transparent 0 42%, rgba(250,204,21,0.12) 42% 43%, transparent 43% 100%), linear-gradient(65deg, transparent 0 54%, rgba(34,197,94,0.12) 54% 55%, transparent 55% 100%), repeating-linear-gradient(0deg, transparent 0 82px, rgba(255,255,255,0.035) 82px 84px)",
      }}
    />
  </>
);

const Header: React.FC<{ landscape?: boolean }> = ({ landscape }) => (
  <div
    style={{
      position: "absolute",
      left: landscape ? 64 : 58,
      top: landscape ? 46 : 58,
      right: landscape ? 64 : 58,
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
    }}
  >
    <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
      <Img
        src={staticFile("yagubom_app_icon.png")}
        style={{
          width: landscape ? 72 : 84,
          height: landscape ? 72 : 84,
          borderRadius: landscape ? 20 : 24,
          boxShadow: "0 16px 42px rgba(0,0,0,0.5)",
        }}
      />
      <div>
        <div
          style={{
            fontSize: landscape ? 38 : 44,
            fontWeight: 960,
            letterSpacing: 0,
          }}
        >
          야구봄
        </div>
        <div
          style={{
            marginTop: 4,
            color: "rgba(255,255,255,0.62)",
            fontSize: landscape ? 18 : 20,
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
        padding: landscape ? "11px 18px" : "14px 20px",
        borderRadius: 999,
        background: "rgba(239,68,68,0.16)",
        border: "1px solid rgba(239,68,68,0.38)",
        color: "#FF5A5F",
        fontSize: landscape ? 22 : 24,
        fontWeight: 950,
        letterSpacing: 0,
      }}
    >
      NEW
    </div>
  </div>
);

const CopyBlock: React.FC<{ landscape?: boolean }> = ({ landscape }) => (
  <div
    style={{
      position: "absolute",
      left: landscape ? 64 : 58,
      top: landscape ? 170 : 190,
      width: landscape ? 760 : 920,
    }}
  >
    <div
      style={{
        fontSize: landscape ? 66 : 72,
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
        marginTop: landscape ? 22 : 23,
        color: "rgba(255,255,255,0.74)",
        fontSize: landscape ? 25 : 27,
        lineHeight: 1.36,
        fontWeight: 760,
        letterSpacing: 0,
      }}
    >
      전체 상세 진행 상황부터 잠금화면, 실시간 알림 팝업까지
      <br />
      일상생활의 모든 곳에서 보고 싶은 경기를 볼 수 있어요.
    </div>
  </div>
);

const PhoneFrame = ({
  src,
  style,
}: {
  src: string;
  style: React.CSSProperties;
}) => (
  <div
    style={{
      position: "absolute",
      borderRadius: 58,
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
        borderRadius: 44,
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
  titleSize = 27,
}: {
  title: string;
  label: string;
  src: string;
  style: React.CSSProperties;
  objectPosition: string;
  width: number;
  height: number;
  titleSize?: number;
}) => (
  <div
    style={{
      position: "absolute",
      width,
      borderRadius: 34,
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
      <div style={{ fontSize: titleSize, fontWeight: 930, letterSpacing: 0 }}>
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
        height,
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
      gap: landscape ? 12 : 13,
      fontSize: landscape ? 25 : 26,
      fontWeight: 850,
      color: "rgba(255,255,255,0.9)",
      letterSpacing: 0,
    }}
  >
    <span
      style={{
        width: landscape ? 15 : 16,
        height: landscape ? 15 : 16,
        borderRadius: 999,
        background: accent,
        boxShadow: `0 0 22px ${accent}80`,
        flexShrink: 0,
      }}
    />
    {text}
  </div>
);

const PortraitLayout: React.FC = () => (
  <>
    <Header />
    <CopyBlock />
    <PhoneFrame
      src="detail-events.png"
      style={{
        width: 230,
        height: 499,
        left: 54,
        top: 818,
        opacity: 0.72,
        transform: "rotate(-5deg)",
      }}
    />
    <PhoneFrame
      src="detail-field.png"
      style={{
        width: 374,
        height: 811,
        left: 150,
        top: 564,
        transform: "rotate(-1deg)",
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 202,
        top: 532,
        padding: "12px 20px",
        borderRadius: 999,
        background: "#FACC15",
        color: "#111113",
        fontSize: 24,
        fontWeight: 950,
        boxShadow: "0 18px 40px rgba(250,204,21,0.28)",
        letterSpacing: 0,
      }}
    >
      경기 전체 상세
    </div>
    <CaptureCard
      title="잠금화면"
      label="LIVE"
      src="android-lock-screen.png"
      objectPosition="50% 52%"
      width={424}
      height={248}
      style={{ right: 54, top: 770 }}
    />
    <CaptureCard
      title="실시간 알림 팝업"
      label="Android"
      src="android-popup.png"
      objectPosition="50% 0%"
      width={424}
      height={248}
      style={{ right: 54, top: 1090 }}
      titleSize={25}
    />
    <div
      style={{
        position: "absolute",
        left: 58,
        right: 58,
        bottom: 58,
        minHeight: 195,
        borderRadius: 36,
        padding: "31px 35px",
        background: "rgba(255,255,255,0.08)",
        border: "1px solid rgba(255,255,255,0.14)",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: 17 }}>
        <Feature text="타석별 실시간 진행 상황" accent="#22C55E" />
        <Feature text="잠금화면 라이브 스코어" accent="#FACC15" />
        <Feature text="실시간 알림 팝업 점수 확인" accent="#FF5A5F" />
      </div>
      <div
        style={{
          width: 270,
          textAlign: "right",
          color: "rgba(255,255,255,0.72)",
          fontSize: 26,
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
        left: 64,
        bottom: 62,
        width: 690,
        minHeight: 182,
        borderRadius: 34,
        padding: "31px 35px",
        background: "rgba(255,255,255,0.08)",
        border: "1px solid rgba(255,255,255,0.14)",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        gap: 18,
      }}
    >
      <Feature text="타석별 실시간 진행 상황" accent="#22C55E" landscape />
      <Feature text="잠금화면 라이브 스코어" accent="#FACC15" landscape />
      <Feature text="실시간 알림 팝업 점수 확인" accent="#FF5A5F" landscape />
    </div>
    <PhoneFrame
      src="detail-events.png"
      style={{
        width: 236,
        height: 512,
        left: 780,
        top: 338,
        opacity: 0.74,
        transform: "rotate(-5deg)",
      }}
    />
    <PhoneFrame
      src="detail-field.png"
      style={{
        width: 324,
        height: 703,
        left: 982,
        top: 170,
        transform: "rotate(-1deg)",
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 1042,
        top: 138,
        padding: "11px 18px",
        borderRadius: 999,
        background: "#FACC15",
        color: "#111113",
        fontSize: 23,
        fontWeight: 950,
        boxShadow: "0 18px 40px rgba(250,204,21,0.28)",
        letterSpacing: 0,
      }}
    >
      경기 전체 상세
    </div>
    <CaptureCard
      title="잠금화면"
      label="LIVE"
      src="android-lock-screen.png"
      objectPosition="50% 52%"
      width={450}
      height={236}
      style={{ right: 64, top: 160 }}
      titleSize={26}
    />
    <CaptureCard
      title="실시간 알림 팝업"
      label="Android"
      src="android-popup.png"
      objectPosition="50% 0%"
      width={450}
      height={236}
      style={{ right: 64, top: 480 }}
      titleSize={25}
    />
    <div
      style={{
        position: "absolute",
        right: 72,
        bottom: 55,
        width: 410,
        textAlign: "right",
        color: "rgba(255,255,255,0.74)",
        fontSize: 29,
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

export const LiveScoreUpdateAndroidRatioPromo: React.FC<{
  orientation: Orientation;
}> = ({ orientation }) => (
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
    {orientation === "landscape" ? <LandscapeLayout /> : <PortraitLayout />}
  </AbsoluteFill>
);
