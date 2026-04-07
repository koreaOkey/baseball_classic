import React from "react";
import { Composition } from "remotion";
import { Screenshot1_LiveScore } from "./compositions/Screenshot1_LiveScore";
import { Screenshot2_HomeRun } from "./compositions/Screenshot2_HomeRun";
import { Screenshot3_Hit } from "./compositions/Screenshot3_Hit";
import { Screenshot4_Victory } from "./compositions/Screenshot4_Victory";
import { Screenshot5_SyncPrompt } from "./compositions/Screenshot5_SyncPrompt";
import { Screenshot6_Score } from "./compositions/Screenshot6_Score";
import { OnboardingScreen } from "./iphone/OnboardingScreen";
import { HomeScreen } from "./iphone/HomeScreen";
import { WatchSyncScreen } from "./iphone/WatchSyncScreen";
import { WearOsPromo } from "./compositions/WearOsPromo";
import { FeatureGraphic } from "./android/FeatureGraphic";
import { PhoneScreenshot1_Home } from "./android/PhoneScreenshot1_Home";
import { PhoneScreenshot2_Onboarding } from "./android/PhoneScreenshot2_Onboarding";
import { PhoneScreenshot3_WatchSync } from "./android/PhoneScreenshot3_WatchSync";
import { PhoneScreenshot4_WatchPreview } from "./android/PhoneScreenshot4_WatchPreview";
import { IosOnboardingScreen } from "./iphone/IosOnboardingScreen";
import { IosHomeScreen } from "./iphone/IosHomeScreen";
import { IosWatchSyncScreen } from "./iphone/IosWatchSyncScreen";
import { IpadOnboardingScreen } from "./iphone/IpadOnboardingScreen";
import { IpadHomeScreen } from "./iphone/IpadHomeScreen";
import { IpadWatchSyncScreen } from "./iphone/IpadWatchSyncScreen";

// Apple Watch Series 9 (App Store)
const W = 396;
const H = 484;

// iPhone 6.7" (iPhone 15 Pro Max)
const IW = 1290;
const IH = 2796;

// iPhone 6.5" (App Store - XS Max / 11 Pro Max)
const IOS65W = 1242;
const IOS65H = 2688;

// iPad Pro 13" (App Store)
const IPADW = 2064;
const IPADH = 2752;

// Android phone (9:16)
const PW = 1080;
const PH = 1920;

// Android tablets
const T7W = 1200;
const T7H = 1920;
const T10W = 1600;
const T10H = 2560;

// Feature graphic
const FW = 1024;
const FH = 500;

export const RemotionRoot: React.FC = () => (
  <>
    {/* Watch screenshots */}
    <Composition id="screenshot1-live-score" component={Screenshot1_LiveScore} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot2-homerun" component={Screenshot2_HomeRun} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot3-hit" component={Screenshot3_Hit} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot4-victory" component={Screenshot4_Victory} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot5-sync-prompt" component={Screenshot5_SyncPrompt} durationInFrames={1} fps={1} width={W} height={H} />
    <Composition id="screenshot6-score" component={Screenshot6_Score} durationInFrames={1} fps={1} width={W} height={H} />

    {/* iPhone screenshots */}
    <Composition id="iphone1-onboarding" component={OnboardingScreen} durationInFrames={1} fps={1} width={IW} height={IH} />
    <Composition id="iphone2-home" component={HomeScreen} durationInFrames={1} fps={1} width={IW} height={IH} />
    <Composition id="iphone3-watch-sync" component={WatchSyncScreen} durationInFrames={1} fps={1} width={IW} height={IH} />

    {/* iOS App Store - iPhone 6.5" (1242x2688) */}
    <Composition id="ios-iphone1-onboarding" component={IosOnboardingScreen} durationInFrames={1} fps={1} width={IOS65W} height={IOS65H} />
    <Composition id="ios-iphone2-home" component={IosHomeScreen} durationInFrames={1} fps={1} width={IOS65W} height={IOS65H} />
    <Composition id="ios-iphone3-watch-sync" component={IosWatchSyncScreen} durationInFrames={1} fps={1} width={IOS65W} height={IOS65H} />

    {/* iOS App Store - iPad Pro 13" (2064x2752) */}
    <Composition id="ios-ipad1-onboarding" component={IpadOnboardingScreen} durationInFrames={1} fps={1} width={IPADW} height={IPADH} />
    <Composition id="ios-ipad2-home" component={IpadHomeScreen} durationInFrames={1} fps={1} width={IPADW} height={IPADH} />
    <Composition id="ios-ipad3-watch-sync" component={IpadWatchSyncScreen} durationInFrames={1} fps={1} width={IPADW} height={IPADH} />

    {/* Wear OS promo */}
    <Composition id="wearos-promo" component={WearOsPromo} durationInFrames={1} fps={1} width={1024} height={500} />

    {/* Android - Feature Graphic */}
    <Composition id="android-feature-graphic" component={FeatureGraphic} durationInFrames={1} fps={1} width={FW} height={FH} />

    {/* Android - Phone Screenshots (1080x1920, 9:16) */}
    <Composition id="android-phone1-home" component={PhoneScreenshot1_Home} durationInFrames={1} fps={1} width={PW} height={PH} />
    <Composition id="android-phone2-onboarding" component={PhoneScreenshot2_Onboarding} durationInFrames={1} fps={1} width={PW} height={PH} />
    <Composition id="android-phone3-watch-sync" component={PhoneScreenshot3_WatchSync} durationInFrames={1} fps={1} width={PW} height={PH} />
    <Composition id="android-phone4-watch-preview" component={PhoneScreenshot4_WatchPreview} durationInFrames={1} fps={1} width={PW} height={PH} />

    {/* Android - 7" Tablet (same content, scaled to 1200x1920) */}
    <Composition id="android-tablet7-1" component={PhoneScreenshot1_Home} durationInFrames={1} fps={1} width={T7W} height={T7H} />
    <Composition id="android-tablet7-2" component={PhoneScreenshot2_Onboarding} durationInFrames={1} fps={1} width={T7W} height={T7H} />
    <Composition id="android-tablet7-3" component={PhoneScreenshot3_WatchSync} durationInFrames={1} fps={1} width={T7W} height={T7H} />
    <Composition id="android-tablet7-4" component={PhoneScreenshot4_WatchPreview} durationInFrames={1} fps={1} width={T7W} height={T7H} />

    {/* Android - 10" Tablet (same content, scaled to 1600x2560) */}
    <Composition id="android-tablet10-1" component={PhoneScreenshot1_Home} durationInFrames={1} fps={1} width={T10W} height={T10H} />
    <Composition id="android-tablet10-2" component={PhoneScreenshot2_Onboarding} durationInFrames={1} fps={1} width={T10W} height={T10H} />
    <Composition id="android-tablet10-3" component={PhoneScreenshot3_WatchSync} durationInFrames={1} fps={1} width={T10W} height={T10H} />
    <Composition id="android-tablet10-4" component={PhoneScreenshot4_WatchPreview} durationInFrames={1} fps={1} width={T10W} height={T10H} />
  </>
);
