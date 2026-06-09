## 1. Backend and Crawler

- [x] 1.1 Add backend support for `/games` date range query with `from` and `to` parameters while preserving existing `date` behavior.
- [x] 1.2 Add dispatcher options for schedule import until a specific date and refresh until a specific date after a configured start date.
- [x] 1.3 Add backend/crawler tests for range query and schedule import date range construction.

## 2. Android Mobile

- [x] 2.1 Add an Android repository function for my-team schedule range loading with local cache fallback.
- [x] 2.2 Replace the Android schedule list sheet with a calendar sheet.
- [x] 2.3 Add Android month navigation, date selection, schedule indicators, and row tap behavior.

## 3. iOS Mobile

- [x] 3.1 Add an iOS repository function for my-team schedule range loading with local cache fallback.
- [x] 3.2 Replace the iOS schedule list sheet with a calendar sheet.
- [x] 3.3 Add iOS month navigation, date selection, schedule indicators, and row tap behavior.

## 4. Platform Impact and Verification

- [x] 4.1 Confirm no DB migration is required.
- [x] 4.2 Confirm Android/iOS mobile impacts and Android/iOS watch impacts.
- [ ] 4.3 Run backend tests for changed API/crawler logic.
- [x] 4.4 Run Android compile verification.
- [x] 4.5 Run iOS build verification.
- [x] 4.6 Review OpenSpec status and completed task checkboxes.
