import React from "react";
import { LiveGameWithOverlay } from "../components/LiveGameWithOverlay";

/** 경기 화면 + SCORE 득점 오버레이 배지 */
export const Screenshot2_HomeRun: React.FC = () => (
  <LiveGameWithOverlay
    data={{
      awayTeam: "베어스",
      homeTeam: "트윈스",
      awayScore: 3,
      homeScore: 6,
      inning: "7회말",
      ballCount: 1,
      strikeCount: 0,
      outCount: 1,
      bases: { first: false, second: true, third: false },
      pitcher: "곽빈",
      batter: "오스틴",
    }}
    eventType="SCORE"
  />
);
