## Context

테마 잠금해제/적용 상태가 UserDefaults(로컬)에만 저장되어 기기 변경·재설치 시 유실됨. Supabase에 이미 `themes`, `user_theme_purchases`, `user_theme_settings` 테이블과 RLS 정책이 준비되어 있으나 앱에서 연동하지 않고 있었음.

## Goals / Non-Goals

**Goals:**
- 테마 잠금해제/적용 이력을 계정 기반으로 Supabase에 저장
- 로그인 시 서버에서 복원하여 기기 간 동기화
- "야구가 좋아" 테마 추가 및 테마 상점 UI 정리
- themes 테이블에 platform 구분 컬럼 추가 (watch/phone/both)

**Non-Goals:**
- 폰 앱 테마 색상 적용 (추후 별도 작업)
- Android/Wear OS 테마 동기화 (이번 scope는 iOS만)
- 오프라인 시 큐잉/재시도 로직 (네트워크 실패 시 로컬만 반영, 다음 로그인 시 서버 기준 복원)

## Decisions

### 1. DB를 원본, UserDefaults를 캐시로 운용
- 잠금해제/적용 시 UserDefaults + Supabase 동시 저장
- 로그인 시 서버에서 fetch → 로컬과 union하여 복원
- **이유**: 로그아웃 상태에서도 로컬 캐시로 즉시 표시 가능, 서버 장애 시에도 앱 동작

### 2. DB 저장은 fire-and-forget (try? await)
- 테마 적용/잠금해제의 UX를 DB 응답에 블로킹하지 않음
- **이유**: 테마 변경은 즉각 반응이 중요, DB 저장 실패해도 로컬에는 반영됨. 다음 로그인 시 서버 기준으로 재동기화

### 3. platform 컬럼으로 워치/폰 구분
- `watch`, `phone`, `both` 3종 enum check
- **대안**: 별도 테이블 → 불필요한 복잡도. 컬럼 하나로 충분

### 4. 테마 상점 섹션 통합
- 기본 + 광고 시청 → "베이직 테마" 단일 섹션
- 프리미엄 숨김 (InApp Purchase 준비 전까지)
- **이유**: 사용자 입장에서 무료 테마 간 구분이 불필요

## Risks / Trade-offs

- [로컬-서버 불일치] 네트워크 실패 시 로컬에만 저장됨 → 다음 로그인 시 서버 기준으로 union 복원하므로 데이터 유실 없음
- [중복 insert] 같은 테마를 여러 번 잠금해제하면 user_theme_purchases에 중복 행 → order_id가 timestamp 포함하여 unique, 복원 시 theme_id 기준 Set으로 처리
- [배터리] DB 호출은 테마 변경 시에만 발생 (경기 중 0건), 배터리 영향 무시 가능
