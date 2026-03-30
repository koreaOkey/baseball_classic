import React from "react";
import { Composition } from "remotion";
import { Screenshot1_LiveScore } from "./compositions/Screenshot1_LiveScore";
import { Screenshot2_HomeRun } from "./compositions/Screenshot2_HomeRun";
import { Screenshot3_EventOverlay } from "./compositions/Screenshot3_EventOverlay";
import { Screenshot4_Victory } from "./compositions/Screenshot4_Victory";
import { Screenshot5_SyncPrompt } from "./compositions/Screenshot5_SyncPrompt";
import { OnboardingScreen } from "./iphone/OnboardingScreen";
import { HomeScreen } from "./iphone/HomeScreen";
import { WatchSyncScreen } from "./iphone/WatchSyncScreen";

// Apple Watch 45mm
const W = 396;
const H = 484;

// iPhone 6.7" (iPhone 15 Pro Max)
const IW = 1290;
const IH = 2796;

export const RemotionRoot: React.FC = () => (
  <>
    {/* Watch screenshots */}
    <Composition id="screenshot1-live-score" component={Screenshot1_LiveScore} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot2-homerun" component={Screenshot2_HomeRun} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot3-event-overlay" component={Screenshot3_EventOverlay} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot4-victory" component={Screenshot4_Victory} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot5-sync-prompt" component={Screenshot5_SyncPrompt} durationInFrames={1} fps={1} width={W} height={H} />

    {/* iPhone screenshots */}
    <Composition id="iphone1-onboarding" component={OnboardingScreen} durationInFrames={1} fps={1} width={IW} height={IH} />
    <Composition id="iphone2-home" component={HomeScreen} durationInFrames={1} fps={1} width={IW} height={IH} />
    <Composition id="iphone3-watch-sync" component={WatchSyncScreen} durationInFrames={1} fps={1} width={IW} height={IH} />
  </>
);
