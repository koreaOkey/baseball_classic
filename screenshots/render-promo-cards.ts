import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import path from "path";

const compositionIds = [
  "promo-yagubom-ios",
  "promo-yagubom-android",
  "promo-yagubom-feature-1-live",
  "promo-yagubom-feature-2-events",
  "promo-yagubom-feature-3-themes",
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
