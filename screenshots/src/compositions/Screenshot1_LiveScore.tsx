import React from "react";
import { LiveGameScreen } from "../components/LiveGameScreen";

export const Screenshot1_LiveScore: React.FC = () => (
  <LiveGameScreen
    data={{
      awayTeam: "타이거즈",
      homeTeam: "랜더스",
      awayScore: 4,
      homeScore: 5,
      inning: "9회말",
      ballCount: 3,
      strikeCount: 2,
      outCount: 2,
      bases: { first: true, second: false, third: true },
      pitcher: "양현종",
      batter: "최정",
    }}
  />
);
