import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import path from "path";

const compositionIds = [
  // Watch
  "screenshot1-live-score",
  "screenshot2-homerun",
  "screenshot3-hit",
  "screenshot4-victory",
  "screenshot5-sync-prompt",
  "screenshot6-score",
  // iPhone (6.7")
  "iphone1-onboarding",
  "iphone2-home",
  "iphone3-watch-sync",
  // iOS App Store - Watch (396x484, same as watch screenshots)
  // (reusing screenshot1~5 above)
  // iOS App Store - iPhone 6.5" (1242x2688)
  "ios-iphone1-onboarding",
  "ios-iphone2-home",
  "ios-iphone3-watch-sync",
  // iOS App Store - iPad Pro 13" (2064x2752)
  "ios-ipad1-onboarding",
  "ios-ipad2-home",
  "ios-ipad3-watch-sync",
  // Wear OS
  "wearos-promo",
  // Android
  "android-feature-graphic",
  "android-phone1-home",
  "android-phone2-onboarding",
  "android-phone3-watch-sync",
  "android-phone4-watch-preview",
  "android-tablet7-1",
  "android-tablet7-2",
  "android-tablet7-3",
  "android-tablet7-4",
  "android-tablet10-1",
  "android-tablet10-2",
  "android-tablet10-3",
  "android-tablet10-4",
];

async function main() {
  console.log("📦 Bundling...");
  const bundled = await bundle({
    entryPoint: path.resolve("./src/index.ts"),
  });

  for (const id of compositionIds) {
    console.log(`🖼  Rendering ${id}...`);
    const composition = await selectComposition({
      serveUrl: bundled,
      id,
    });

    await renderStill({
      composition,
      serveUrl: bundled,
      output: path.resolve(`./out/${id}.png`),
      imageFormat: "png",
    });

    console.log(`   ✅ out/${id}.png`);
  }

  console.log("\n🎉 All screenshots rendered to ./out/");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
