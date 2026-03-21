# Watch Screen QA (3 Device Profiles)

Updated: 2026-03-21

## Target Profiles
- `Small Round` (`id:wearos_small_round`)
- `Large Round` (`id:wearos_large_round`)
- `Square` (`id:wearos_square`)

## Quick Check in Compose Preview
1. Open:
   - `app/src/main/java/com/basehaptic/watch/ui/components/LiveGameScreen.kt`
   - `app/src/main/java/com/basehaptic/watch/ui/components/NoGameScreen.kt`
   - `app/src/main/java/com/basehaptic/watch/MainActivity.kt` (prompt previews)
2. Verify each preview variant is visible and not clipped.
3. Confirm text/button touch areas remain readable in all 3 profiles.

## Runtime Checkpoints (Emulator/Device)
1. `NoGameScreen`
- Main text is centered and not cut off.
- Secondary helper text is fully readable.

2. `LiveGameScreen`
- Inning, score, BSO, and bases stay within safe area.
- Team logos do not overlap score numbers.
- Pitcher/batter line is visible and not outside the viewport.

3. `Watch Sync Prompt`
- Question text (`Would you like to watch this game?`) is fully visible.
- Matchup line (`AWAY vs HOME`) does not overlap buttons.
- `Yes/No` buttons are fully tappable with no clipping.

## Optional Screenshot Capture
After launching each target emulator/device:

```bash
adb devices
adb -s <deviceId> shell screencap -p /sdcard/watch_ui.png
adb -s <deviceId> pull /sdcard/watch_ui.png ./artifacts/watch_ui_<profile>.png
```

Use one image per state (`NoGame`, `LiveGame`, `Prompt`) and per profile.
