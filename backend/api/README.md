# backend/api

BaseHaptic MVP 백엔드 API 서버입니다.

## Stack
- Python 3.11+
- FastAPI
- SQLAlchemy
- SQLite (dev 기본값)

## Quick Start
```bash
cd backend/api
python -m venv .venv
. .venv/Scripts/Activate.ps1
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8080
```

- API 문서: `http://localhost:8080/docs`
- 상세 구조/연동 가이드는 `../README.md` 참고

