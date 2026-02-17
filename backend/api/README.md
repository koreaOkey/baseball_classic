# backend/api

BaseHaptic MVP 백엔드 API 서버입니다.

## Stack
- Python 3.11+
- FastAPI
- SQLAlchemy
- SQLite (dev 기본값)

## Quick Start
```bash
cd <repo-root>
python -m venv .venv
. .venv/Scripts/Activate.ps1
cd backend/api
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8080
```

- API 문서: `http://localhost:8080/docs`
- 상세 구조/연동 가이드는 `../README.md` 참고

## Supabase 전환
- `BASEHAPTIC_DATABASE_URL`을 Supabase Postgres DSN으로 설정하면 됩니다.
- 예: `postgresql+psycopg://<user>:<password>@db.<project-ref>.supabase.co:5432/postgres`

