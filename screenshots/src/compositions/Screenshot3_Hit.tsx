import React from "react";
import { LiveGameWithOverlay } from "../components/LiveGameWithOverlay";

/** 경기 화면 + OUT 아웃 오버레이 배지 */
export const Screenshot3_Hit: React.FC = () => (
  <LiveGameWithOverlay
    data={{
      awayTeam: "라이온즈",
      homeTeam: "타이거즈",
      awayScore: 2,
      homeScore: 3,
      inning: "5회초",
      ballCount: 2,
      strikeCount: 2,
      outCount: 2,
      bases: { first: true, second: false, third: false },
      pitcher: "양현종",
      batter: "구자욱",
    }}
    eventType="OUT"
  />
);
