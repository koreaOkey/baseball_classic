import { bundle } from "@remotion/bundler";
import { renderStill, selectComposition } from "@remotion/renderer";
import path from "path";

const compositionIds = [
  // Watch
  "screenshot1-live-score",
  "screenshot2-homerun",
  "screenshot3-event-overlay",
  "screenshot4-victory",
  "screenshot5-sync-prompt",
  // iPhone
  "iphone1-onboarding",
  "iphone2-home",
  "iphone3-watch-sync",
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
