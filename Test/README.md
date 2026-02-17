# Test Results

이 폴더는 `crawler -> backend -> DB` 연결 테스트 산출물을 보관합니다.

## 포함 파일
- `backend_test_runtime.log`: 테스트 중 백엔드 표준 출력 로그
- `backend_test_runtime.err`: 테스트 중 백엔드 표준 에러 로그
- `mock_relay_test_runtime.log`: mock relay 서버 표준 출력 로그
- `mock_relay_test_runtime.err`: mock relay 서버 표준 에러 로그
- `backend_supabase_runtime.log`: Supabase 연결 검증 시 백엔드 표준 출력 로그
- `backend_supabase_runtime.err`: Supabase 연결 검증 시 백엔드 표준 에러 로그
- `mock_relay_supabase_runtime.log`: Supabase 연결 검증 시 mock relay 표준 출력 로그
- `mock_relay_supabase_runtime.err`: Supabase 연결 검증 시 mock relay 표준 에러 로그
- `basehaptic.db`: 테스트에서 생성된 SQLite DB 파일

## 참고
- `basehaptic.db`는 원래 `backend/api/basehaptic.db`에 있던 파일을 이동한 것입니다.
- 백엔드를 SQLite 기본 설정으로 다시 실행하면 `backend/api/basehaptic.db`가 새로 생성됩니다.

## Supabase 연결 및 결과 확인
1. `backend/api/.env`의 `BASEHAPTIC_DATABASE_URL`이 `postgresql+psycopg://...` 형식인지 확인합니다.
2. 백엔드 실행:
   - `cd backend/api`
   - `python -m uvicorn app.main:app --reload --port 8080`
3. crawler ingest를 실행한 뒤, Supabase SQL Editor에서 `Test/supabase_result_queries.sql` 내용을 실행해 결과를 확인합니다.
