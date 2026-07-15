"""FCM (Firebase Cloud Messaging) HTTP v1 전송 모듈.

APNs 와 동일한 인터페이스 (send_visible_push) 를 제공하여
main.py 에서 platform 분기로 호출한다.
"""
from __future__ import annotations

import asyncio
import json
import logging
import threading
from typing import Any

from .config import get_settings

logger = logging.getLogger(__name__)

_app_lock = threading.Lock()
_initialized = False
_init_error: str | None = None


def _ensure_initialized() -> bool:
    """firebase_admin 앱을 1회 초기화. 환경변수 미설정 시 False 반환."""
    global _initialized, _init_error
    if _initialized:
        return True
    if _init_error is not None:
        return False

    with _app_lock:
        if _initialized:
            return True

        settings = get_settings()
        raw = settings.fcm_service_account_json
        if not raw:
            _init_error = "missing_service_account"
            logger.warning("[FCM] service account JSON not configured; FCM disabled")
            return False

        try:
            import firebase_admin
            from firebase_admin import credentials

            cred_dict = json.loads(raw)
            cred = credentials.Certificate(cred_dict)
            if not firebase_admin._apps:
                firebase_admin.initialize_app(cred)
            _initialized = True
            logger.info("[FCM] firebase_admin initialized project=%s", cred_dict.get("project_id"))
            return True
        except Exception as exc:
            _init_error = f"init_failed:{exc}"
            logger.exception("[FCM] init failed")
            return False


async def send_visible_push(
    fcm_token: str,
    *,
    title: str,
    body: str,
    data: dict[str, str] | None = None,
) -> bool:
    """단일 Android 디바이스에 visible 푸시 전송."""
    if not _ensure_initialized():
        return False
    return await asyncio.to_thread(_send_blocking, fcm_token, title, body, data or {})


def _send_blocking(token: str, title: str, body: str, data: dict[str, str]) -> bool:
    try:
        from firebase_admin import messaging

        message = messaging.Message(
            token=token,
            notification=messaging.Notification(title=title, body=body),
            data={k: str(v) for k, v in data.items()},
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="game_alerts",
                    default_sound=True,
                ),
            ),
        )
        message_id = messaging.send(message)
        logger.info("[FCM] sent message_id=%s token=%s...", message_id, token[:16])
        return True
    except Exception as exc:
        logger.warning("[FCM] send failed token=%s... error=%s", token[:16], exc)
        return False


# send_each 배치 최대 크기 (FCM 권장 상한 500)
FCM_SEND_EACH_BATCH_SIZE = 500


def _send_each_blocking(
    tokens: list[str], title: str, body: str, data: dict[str, str],
) -> tuple[list[str], list[str]]:
    """send_each 배치 전송. (실패 토큰, 영구 실패=Unregistered 토큰) 반환."""
    from firebase_admin import messaging

    failed: list[str] = []
    unregistered: list[str] = []
    for start in range(0, len(tokens), FCM_SEND_EACH_BATCH_SIZE):
        batch = tokens[start:start + FCM_SEND_EACH_BATCH_SIZE]
        messages = [
            messaging.Message(
                token=token,
                notification=messaging.Notification(title=title, body=body),
                data={k: str(v) for k, v in data.items()},
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        channel_id="game_alerts",
                        default_sound=True,
                    ),
                ),
            )
            for token in batch
        ]
        try:
            batch_response = messaging.send_each(messages)
        except Exception as exc:
            logger.warning("[FCM] send_each batch failed size=%d error=%s", len(batch), exc)
            failed.extend(batch)
            continue

        for token, response in zip(batch, batch_response.responses):
            if response.success:
                continue
            failed.append(token)
            if isinstance(response.exception, messaging.UnregisteredError):
                unregistered.append(token)
            logger.warning(
                "[FCM] send failed token=%s... error=%s", token[:16], response.exception,
            )
    return failed, unregistered


async def send_visible_push_to_tokens_detailed(
    tokens: list[str],
    *,
    title: str,
    body: str,
    data: dict[str, str] | None = None,
) -> tuple[list[str], list[str]]:
    """여러 Android 디바이스에 visible push 배치 전송.

    (실패 토큰, 영구 실패=Unregistered 토큰) 을 반환한다. 영구 실패 토큰은
    호출부에서 DB 정리(prune) 대상으로 쓴다.
    """
    if not tokens:
        return [], []
    if not _ensure_initialized():
        return list(tokens), []
    return await asyncio.to_thread(_send_each_blocking, tokens, title, body, data or {})


async def send_visible_push_to_tokens(
    tokens: list[str],
    *,
    title: str,
    body: str,
    data: dict[str, str] | None = None,
) -> list[str]:
    """여러 Android 디바이스에 visible push 배치 전송. 실패 토큰 반환."""
    failed, _ = await send_visible_push_to_tokens_detailed(
        tokens, title=title, body=body, data=data,
    )
    return failed
