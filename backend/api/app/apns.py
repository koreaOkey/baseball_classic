"""APNs (Apple Push Notification service) HTTP/2 전송 모듈"""
import base64
import json
import logging
import time
from typing import Any

import httpx
import jwt

from .config import get_settings

logger = logging.getLogger(__name__)

APNS_PRODUCTION_URL = "https://api.push.apple.com"
APNS_SANDBOX_URL = "https://api.sandbox.push.apple.com"

# JWT 토큰 캐싱 (최대 50분, APNs는 1시간 만료)
_cached_jwt: str | None = None
_cached_jwt_expires: float = 0
JWT_LIFETIME_SECONDS = 50 * 60


def _get_apns_key() -> str | None:
    """환경 변수에서 .p8 키 내용을 base64 디코딩하여 반환"""
    settings = get_settings()
    if not settings.apns_key_base64:
        return None
    return base64.b64decode(settings.apns_key_base64).decode("utf-8")


def _create_jwt_token() -> str | None:
    """APNs 인증용 JWT 토큰 생성 (ES256)"""
    global _cached_jwt, _cached_jwt_expires

    now = time.time()
    if _cached_jwt and now < _cached_jwt_expires:
        return _cached_jwt

    settings = get_settings()
    key_content = _get_apns_key()
    if not key_content or not settings.apns_key_id or not settings.apns_team_id:
        logger.warning("[APNs] Missing APNs configuration (key, key_id, or team_id)")
        return None

    token = jwt.encode(
        {"iss": settings.apns_team_id, "iat": int(now)},
        key_content,
        algorithm="ES256",
        headers={"kid": settings.apns_key_id},
    )

    _cached_jwt = token
    _cached_jwt_expires = now + JWT_LIFETIME_SECONDS
    return token


async def send_push(
    device_token: str,
    payload: dict[str, Any],
    *,
    use_sandbox: bool | None = None,
    platform: str = "ios",
) -> bool:
    """단일 디바이스에 silent push 전송"""
    settings = get_settings()
    jwt_token = _create_jwt_token()
    if jwt_token is None:
        return False

    sandbox = use_sandbox if use_sandbox is not None else settings.apns_use_sandbox
    base_url = APNS_SANDBOX_URL if sandbox else APNS_PRODUCTION_URL
    url = f"{base_url}/3/device/{device_token}"

    # watchOS는 별도 번들 ID 사용
    topic = f"{settings.apns_bundle_id}.watchkitapp" if platform == "watchos" else settings.apns_bundle_id

    headers = {
        "authorization": f"bearer {jwt_token}",
        "apns-topic": topic,
        "apns-push-type": "background",
        "apns-priority": "5",
    }

    apns_payload = {
        "aps": {"content-available": 1},
        **payload,
    }

    try:
        async with httpx.AsyncClient(http2=True) as client:
            response = await client.post(
                url,
                content=json.dumps(apns_payload),
                headers={**headers, "content-type": "application/json"},
                timeout=10.0,
            )

        if response.status_code == 200:
            return True

        body = response.text
        logger.warning("[APNs] Push failed: status=%s body=%s token=%s... sandbox=%s topic=%s", response.status_code, body, device_token[:16], sandbox, topic)

        # 410 Gone = 토큰 만료 → 호출자가 삭제 처리
        return False

    except Exception:
        logger.exception("[APNs] Push request error: token=%s...", device_token[:16])
        return False


async def send_live_activity_push(
    push_token: str,
    content_state: dict[str, Any],
    *,
    event_type: str = "update",  # "update" or "end"
    timestamp: int | None = None,
) -> bool:
    """ActivityKit Live Activity push 전송"""
    settings = get_settings()
    jwt_token = _create_jwt_token()
    if jwt_token is None:
        return False

    base_url = APNS_SANDBOX_URL if settings.apns_use_sandbox else APNS_PRODUCTION_URL
    url = f"{base_url}/3/device/{push_token}"

    ts = timestamp or int(time.time())

    headers = {
        "authorization": f"bearer {jwt_token}",
        "apns-topic": f"{settings.apns_bundle_id}.push-type.liveactivity",
        "apns-push-type": "liveactivity",
        "apns-priority": "10",
    }

    apns_payload = {
        "aps": {
            "timestamp": ts,
            "event": event_type,
            "content-state": content_state,
        },
    }

    try:
        async with httpx.AsyncClient(http2=True) as client:
            response = await client.post(
                url,
                content=json.dumps(apns_payload),
                headers={**headers, "content-type": "application/json"},
                timeout=10.0,
            )

        if response.status_code == 200:
            return True

        logger.warning(
            "[APNs-LA] Push failed: status=%s body=%s token=%s...",
            response.status_code, response.text, push_token[:16],
        )
        return False

    except Exception:
        logger.exception("[APNs-LA] Push request error: token=%s...", push_token[:16])
        return False


async def send_push_to_tokens(
    tokens: list[str],
    payload: dict[str, Any],
) -> list[str]:
    """여러 디바이스에 push 전송, 실패한 토큰 목록 반환"""
    failed_tokens: list[str] = []
    for token in tokens:
        success = await send_push(token, payload)
        if not success:
            failed_tokens.append(token)
    return failed_tokens
