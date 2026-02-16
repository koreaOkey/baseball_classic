# Figma Import Guide (Watch UI)

## What was generated
- `figma-handoff/design-tokens.json`
  - Colors, typography, spacing, radius, team themes (DEFAULT + KBO 10 teams)
- `figma-handoff/screens/live-game-screen.spec.json`
  - Layout spec for `LiveGameScreen`
- `figma-handoff/screens/no-game-screen.spec.json`
  - Layout spec for `NoGameScreen`
- `figma-handoff/assets/`
  - `reference.png`
  - team logo PNG assets (`dosan.png`, `lg.png`, ...)

## Recommended Figma structure
1. Create pages:
- `01 Tokens`
- `02 Components`
- `03 Screens`

2. Build variables/styles from `design-tokens.json`:
- Color variables: `color/*`
- Text styles: `typography/*`
- Number variables: `radius/*`, `spacing/*`, `size/*`
- Team modes/collections: `themes/*`

3. Build components in `02 Components`:
- `ScoreSide`
- `CountIndicator`
- `BaseDiamond`

4. Build final frames in `03 Screens`:
- `Live Game / Round` from `live-game-screen.spec.json`
- `No Game / Round` from `no-game-screen.spec.json`

## Figma MCP usage prompt (copy/paste)
Use this prompt in your Figma MCP-enabled agent:

```text
Read these files from my workspace and recreate the watch UI in Figma.

- apps/watch/figma-handoff/design-tokens.json
- apps/watch/figma-handoff/screens/live-game-screen.spec.json
- apps/watch/figma-handoff/screens/no-game-screen.spec.json
- apps/watch/figma-handoff/assets/reference.png
- apps/watch/figma-handoff/assets/*.png

Requirements:
1) Create pages: 01 Tokens, 02 Components, 03 Screens.
2) Convert token JSON into Figma variables/styles.
3) Build reusable components: ScoreSide, CountIndicator, BaseDiamond.
4) Create two round watch frames (192x192): Live Game / Round, No Game / Round.
5) Bind layer fills/text styles to variables where possible.
6) Use team logo assets from assets/*.png for away/home logos.
7) Keep spacing/radius/font sizes from the spec JSON exactly.
```

## Notes
- The Compose preview device is `wearos_small_round`; this handoff uses `192x192` as base frame.
- `LiveGameScreen` colors are dynamic by `teamName` (`themes/*` in tokens).
- If your Figma MCP cannot ingest JSON directly, keep this order:
  - Import `assets/reference.png` to match visual baseline.
  - Create token variables manually from `design-tokens.json`.
  - Rebuild layouts from `screens/*.spec.json`.
