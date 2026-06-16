## 1. OpenSpec and Data Model

- [x] 1.1 Add OpenSpec proposal, design, and delta specs for mobile, watch, and ingest behavior.
- [x] 1.2 Add DB migration for display-name style columns with safe defaults.
- [x] 1.3 Extend backend request schemas and SQLAlchemy models for display-name style.

## 2. Backend Push Behavior

- [x] 2.1 Add team-name and mascot-name resolution helpers.
- [x] 2.2 Store display-name style on device token and team subscription registration.
- [x] 2.3 Generate game-start visible push title/body per display style group.
- [x] 2.4 Add backend tests for TEAM, MASCOT, and missing-style fallback.

## 3. Android Mobile and Wear OS

- [x] 3.1 Add Android display-name style model, persistence, and display helper.
- [x] 3.2 Add Android onboarding/existing-user display choice popup with team logos.
- [x] 3.3 Add Android settings entry to change display-name style.
- [x] 3.4 Apply Android display helper to primary mobile team labels.
- [x] 3.5 Sync display-name style to Wear OS and update Wear OS score labels.
- [x] 3.6 Include display-name style in Android push subscription/device-token registration.

## 4. iOS Mobile and watchOS

- [x] 4.1 Add iOS display-name style model, persistence, and display helper.
- [x] 4.2 Add iOS onboarding/existing-user display choice popup with team logos.
- [x] 4.3 Add iOS settings entry to change display-name style.
- [x] 4.4 Apply iOS display helper to primary mobile team labels.
- [x] 4.5 Sync display-name style to watchOS and update watchOS score labels.
- [x] 4.6 Include display-name style in iOS push subscription/device-token registration.

## 5. Verification

- [x] 5.1 Validate OpenSpec change.
- [x] 5.2 Run backend targeted tests.
- [x] 5.3 Run Android mobile/watch compile checks.
- [x] 5.4 Run iOS build check.
- [x] 5.5 Confirm Android/iOS and mobile/watch impact notes.
