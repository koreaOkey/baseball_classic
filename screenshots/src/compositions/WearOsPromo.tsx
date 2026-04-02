import React from "react";
import { staticFile } from "remotion";
import { WearOsFrame } from "../components/WearOsFrame";

const WATCH_SIZE = 280;

const images = [
  { src: staticFile("wearos_1.png"), label: "우승" },
  { src: staticFile("wearos_2.png"), label: "홈런" },
  { src: staticFile("wearos_3.png"), label: "세이프" },
];

export const WearOsPromo: React.FC = () => (
  <div
    style={{
      width: "100%",
      height: "100%",
      background: "linear-gradient(135deg, #0d0d10 0%, #1a1a2e 50%, #0d0d10 100%)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      gap: 40,
    }}
  >
    {images.map((img, i) => (
      <WearOsFrame key={i} size={WATCH_SIZE}>
        <img
          src={img.src}
          style={{
            width: WATCH_SIZE,
            height: WATCH_SIZE,
            objectFit: "cover",
          }}
        />
      </WearOsFrame>
    ))}
  </div>
);
