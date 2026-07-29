# Tasks

## 1. DB 풀 여유 확보

- [x] 1.1 Railway env `BASEHAPTIC_DB_POOL_SIZE=2`, `BASEHAPTIC_DB_MAX_OVERFLOW=6` 적용 (2026-07-29 09:18 KST, 배포 2977dfdd SUCCESS)
- [x] 1.2 재배포 후 기동·WS·`/games` 200 확인

## 2. FCM httpTimeout 제한

- [x] 2.1 `config.py`에 `fcm_http_timeout_sec: int = 10` 추가
- [x] 2.2 `fcm.py` `initialize_app`에 `options={"httpTimeout": ...}` 전달
- [x] 2.3 firebase-admin 6.9.0이 httpTimeout 옵션을 존중하는지 소스 확인
- [x] 2.4 백엔드 테스트 97건 통과
- [x] 2.5 커밋 d6619912 → origin/staging 푸시 → 배포 7fd724b7 SUCCESS (f2416cc4 동반 배포, 사용자 승인)

## 3. 후속 검증 (잔존)

- [ ] 3.1 저녁 경기 시간대 SSL 에피소드 재발 시 httpTimeout 효과 로그 확인 (FCM batch 실패가 분 단위 → 초 단위 블로킹으로 줄었는지)
- [ ] 3.2 효과 부족 시 messaging 세션 Retry(total=2) 패치 검토 (사설 API, try/except 가드 필수)
- [ ] 3.3 Railway 지원팀에 저녁 시간대 egress SSL 단절 문의
- [ ] 3.4 f2416cc4 타석 카드(타순·당일 성적) 실경기 검증
