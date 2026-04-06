import React from "react";
import { LiveGameScreen } from "./LiveGameScreen";
import { EventOverlayBadge } from "./EventOverlayBadge";
import type { GameData } from "../types";

export const LiveGameWithOverlay: React.FC<{
  data: GameData;
  eventType: string;
}> = ({ data, eventType }) => (
  <div style={{ width: "100%", height: "100%", position: "relative" }}>
    <LiveGameScreen data={data} />
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <EventOverlayBadge eventType={eventType} />
    </div>
  </div>
);
