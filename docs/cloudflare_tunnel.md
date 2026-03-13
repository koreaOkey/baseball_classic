# Cloudflare Tunnel Setup (External Phone/Watch Test)

This guide exposes local backend (`localhost:8080`) to external devices through HTTPS.

## 1) Prerequisites

- Cloudflare account
- `cloudflared` installed on Windows
  - `winget install Cloudflare.cloudflared`

## 2) Start local backend

```powershell
cd backend/api
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

## 3) Quick tunnel (fastest)

From repo root in another terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev\start_cloudflare_quick_tunnel.ps1 -Port 8080
```

`cloudflared` prints a URL like:

`https://<random>.trycloudflare.com`

Use that as mobile backend URL.

## 4) Build mobile app with tunnel URL

From repo root:

```powershell
.\gradlew.bat :apps:mobile:app:installDebug -PbackendBaseUrl=https://<random>.trycloudflare.com
```

## 5) Keep crawler/dispatcher local

For normal dev, crawler/dispatcher can still call local backend directly:

```powershell
python crawler/live_baseball_dispatcher.py --backend-base-url http://localhost:8080 --backend-api-key dev-crawler-key
```

## 6) Named tunnel (fixed domain, optional)

1. Login:
```powershell
cloudflared tunnel login
```

2. Create tunnel:
```powershell
cloudflared tunnel create basehaptic-backend
```

3. Create DNS route (example):
```powershell
cloudflared tunnel route dns basehaptic-backend api.your-domain.com
```

4. Copy config template and fill real values:
- `infra/cloudflared/config.example.yml` -> `infra/cloudflared/config.yml`

5. Run named tunnel:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev\start_cloudflare_named_tunnel.ps1
```

Then build mobile app with:

```powershell
.\gradlew.bat :apps:mobile:app:installDebug -PbackendBaseUrl=https://api.your-domain.com
```
