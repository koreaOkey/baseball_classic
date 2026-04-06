import React from "react";
import { LiveGameScreen } from "../components/LiveGameScreen";

/** 다른 팀/이닝의 경기 화면 */
export const Screenshot6_Score: React.FC = () => (
  <LiveGameScreen
    data={{
      awayTeam: "다이노스",
      homeTeam: "자이언츠",
      awayScore: 1,
      homeScore: 0,
      inning: "3회초",
      ballCount: 0,
      strikeCount: 1,
      outCount: 0,
      bases: { first: false, second: false, third: false },
      pitcher: "박세웅",
      batter: "손아섭",
    }}
  />
);
