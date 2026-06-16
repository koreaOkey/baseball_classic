import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import path from "path";

const compositionIds = [
  "promo-live-score-update",
  "promo-live-score-update-1242x2688",
  "promo-live-score-update-2688x1242",
  "promo-live-score-update-1284x2778",
  "promo-live-score-update-2778x1284",
  "promo-live-score-update-android-1242x2688",
  "promo-live-score-update-android-2688x1242",
  "promo-live-score-update-android-1284x2778",
  "promo-live-score-update-android-2778x1284",
  "promo-live-score-update-android-9x16-1080x1920",
  "promo-live-score-update-android-16x9-1920x1080",
];

async function main() {
  const bundled = await bundle({
    entryPoint: path.resolve("./src/index.ts"),
  });

  for (const id of compositionIds) {
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
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
