## 1. Backend

- [x] 1.1 Add a service function that returns team records by category and season ordered by ranking.
- [x] 1.2 Add a public backend API for full KBO team standings using the existing `team_record` table.
- [x] 1.3 Add backend tests for populated and empty standings responses.

## 2. Android Mobile

- [x] 2.1 Add the generated standings icon as an Android drawable resource.
- [x] 2.2 Add repository models and fetch logic for the full team standings API.
- [x] 2.3 Replace the home-screen bolt icon with the standings icon and make it open a standings popup.
- [x] 2.4 Show loading, success, empty, and error states in the standings popup.

## 3. iOS Mobile

- [x] 3.1 Add the generated standings icon to iOS assets.
- [x] 3.2 Add repository models and fetch logic for the full team standings API.
- [x] 3.3 Replace the home-screen bolt icon with the standings icon and make it open a standings popup.
- [x] 3.4 Show loading, success, empty, and error states in the standings popup.

## 4. Platform Impact and Verification

- [x] 4.1 Confirm no DB migration is required because the existing `team_record` table and rank index are reused.
- [x] 4.2 Confirm Android/iOS mobile behavior and watch Android/iOS impact.
- [x] 4.3 Run backend tests for team-record APIs.
- [x] 4.4 Run available Android/iOS build or compile checks for touched mobile code.
