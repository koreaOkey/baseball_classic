## 1. 사전 준비 · 외부 의존성 등록

- [ ] 1.1 카카오 개발자 앱 등록 및 REST 키 발급 (iOS·Android 번들 ID 등록)
- [ ] 1.2 Apple Developer에서 Sign In with Apple 활성화 및 키 발급
- [ ] 1.3 Supabase Auth에 카카오·Apple 공급자 설정
- [ ] 1.4 AI 모더레이션 공급자 선정 (OpenAI Moderation vs Google Perspective, 한국어 정확도 비교 테스트)
- [ ] 1.5 이용약관·개인정보처리방침·커뮤니티 가이드라인 초안 작성 및 법무 검토
- [ ] 1.6 파일럿 KPI 정의 (DAU, 메시지 수, 신고율 임계치)

## 2. 데이터베이스 스키마 마이그레이션

- [ ] 2.1 `user_profiles` 테이블 생성 (user_id, 닉네임, 응원팀, 프로필 이미지 URL, 약관 동의 버전, 생성 시각)
- [ ] 2.2 `rooms` 테이블 생성 (id, team_code, name, is_active, created_at)
- [ ] 2.3 `articles` 테이블 생성 (id, source, title, summary, thumbnail_url, original_url, team_code, published_at)
- [ ] 2.4 `messages` 테이블 생성 (id, room_id, user_id, content, reply_to_id, is_anonymous, is_blinded, ai_score, created_at)
- [ ] 2.5 `article_pins` 테이블 생성 (room_id, article_id, pinned_at)
- [ ] 2.6 `reports` 테이블 생성 (id, message_id, reporter_id, reason, memo, status, created_at)
- [ ] 2.7 `banned_words` 테이블 생성 (id, pattern, severity, created_at)
- [ ] 2.8 `hot_articles_view` materialized view 생성 (가중치 공식 + 시간감쇠)
- [ ] 2.9 모든 테이블에 RLS 정책 적용 (읽기·쓰기 권한 분리, 타팀 쓰기 차단)
- [ ] 2.10 파일럿 seed 데이터 투입 (LG·두산 rooms, 주요 선수명 금칙어 초기값)

## 3. 백엔드 API 구현

- [ ] 3.1 `POST /auth/kakao` 카카오 OAuth 콜백 처리
- [ ] 3.2 `POST /auth/apple` Apple Sign In 콜백 처리
- [ ] 3.3 `GET /me` / `PATCH /me` 프로필 조회·수정 (응원팀 선택 포함)
- [ ] 3.4 `POST /agreements` 약관 동의 기록
- [ ] 3.5 `GET /rooms` 팀 방 목록 (활성/비활성 구분)
- [ ] 3.6 `GET /rooms/{id}/messages` 메시지 페이지네이션
- [ ] 3.7 `POST /rooms/{id}/messages` 메시지 전송 (reply_to_id, is_anonymous 지원, AI 모더레이션 호출)
- [ ] 3.8 `GET /articles/hot` 핫 기사 랭킹 (전 구단 통합)
- [ ] 3.9 `GET /articles/{id}/thread` 특정 기사에 연결된 답장 스레드
- [ ] 3.10 `POST /reports` 신고 접수
- [ ] 3.11 Supabase Realtime 채널 설정 (room_id 기반 브로드캐스트)
- [ ] 3.12 모더레이션 파이프라인 (금칙어 → AI → 저장 → 신고 누적 → 자동 블라인드)

## 4. 크롤러 구현

- [ ] 4.1 네이버 스포츠 RSS/API 수집 모듈
- [ ] 4.2 팀 태깅 로직 (제목·요약 기반)
- [ ] 4.3 원문 URL 기반 중복 제거
- [ ] 4.4 새 기사 저장 후 파일럿 팀방에 자동 핀 포스팅
- [ ] 4.5 Railway 크론 스케줄 설정 및 재시도 정책

## 5. iOS 모바일 앱 (SwiftUI)

- [ ] 5.1 하단 탭바에 COMMUNITY 탭 추가
- [ ] 5.2 Supabase Auth SDK 연동 (카카오·Apple)
- [ ] 5.3 로그인 화면 구현 (Pencil 화면 4)
- [ ] 5.4 응원팀 선택 온보딩 플로우
- [ ] 5.5 커뮤니티 홈 화면 구현 (Pencil 화면 1)
- [ ] 5.6 팀 채팅방 화면 구현 (Pencil 화면 2, Realtime 연동)
- [ ] 5.7 메시지 버블 컴포넌트 (일반·익명·내 메시지·기사 인용)
- [ ] 5.8 익명 토글 입력바
- [ ] 5.9 기사 스레드 상세 화면 구현 (Pencil 화면 3, 탭 필터)
- [ ] 5.10 신고 바텀시트 구현 (Pencil 화면 5)
- [ ] 5.11 커뮤니티 전용 디자인 토큰 추가 (다크 전용)
- [ ] 5.12 커뮤니티 탭 feature flag 연동

## 6. Android 모바일 앱 (Compose)

- [ ] 6.1 하단 탭바에 COMMUNITY 탭 추가
- [ ] 6.2 Supabase Auth SDK 연동 (카카오·Apple)
- [ ] 6.3 로그인 화면 구현 (iOS 대칭)
- [ ] 6.4 응원팀 선택 온보딩 플로우
- [ ] 6.5 커뮤니티 홈 화면 구현
- [ ] 6.6 팀 채팅방 화면 구현 (Realtime 연동)
- [ ] 6.7 메시지 버블 컴포저블
- [ ] 6.8 익명 토글 입력바
- [ ] 6.9 기사 스레드 상세 화면 구현
- [ ] 6.10 신고 바텀시트 구현
- [ ] 6.11 커뮤니티 전용 디자인 토큰 추가 (iOS와 대칭, 다크 전용)
- [ ] 6.12 커뮤니티 탭 feature flag 연동

## 7. 운영자 도구

- [ ] 7.1 신고 검토 대시보드 (24시간 SLA 타이머 포함)
- [ ] 7.2 금칙어 관리 UI (추가·제거·심각도)
- [ ] 7.3 사용자 경고·제재 기록 UI

## 8. 테스트 · 검증

- [ ] 8.1 백엔드 API 단위·통합 테스트
- [ ] 8.2 모더레이션 파이프라인 시뮬레이션 테스트 (금칙어·AI·신고 누적 각 케이스)
- [ ] 8.3 iOS 화면 5종 수동 QA (다크 모드 전용)
- [ ] 8.4 Android 화면 5종 수동 QA (다크 모드 전용)
- [ ] 8.5 실시간 동시 접속 부하 테스트 (팀방 100명 기준)
- [ ] 8.6 접근성 검토 (VoiceOver·TalkBack, 최소 터치 영역)

## 9. 배포 · 파일럿 오픈

- [ ] 9.1 백엔드·크롤러·DB 마이그레이션 스테이징 배포
- [ ] 9.2 iOS·Android 커뮤니티 탭 feature flag OFF 상태로 앱 배포
- [ ] 9.3 내부 테스트 (운영자·개발팀)
- [ ] 9.4 LG·두산 파일럿 오픈 (feature flag ON)
- [ ] 9.5 KPI 모니터링 (첫 2주)
- [ ] 9.6 파일럿 회고 및 후속 차수 결정 (타팀 확장·참여 정책·라이트 모드)

## 10. 문서 · 법무

- [ ] 10.1 이용약관·개인정보처리방침·커뮤니티 가이드라인 최종본 앱 내 노출
- [ ] 10.2 법적 요청 대응 프로세스 내부 문서화
- [ ] 10.3 개인정보 보관 기간·파기 절차 문서화
- [ ] 10.4 운영자 매뉴얼 (신고 검토 기준·제재 단계)
