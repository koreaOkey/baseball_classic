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
                firebase_admin.initialize_app(
                    cred,
                    options={"httpTimeout": max(1, settings.fcm_http_timeout_sec)},
                )
            _initialized = True
            _tune_connection_pool(settings.fcm_http_pool_size)
            logger.info("[FCM] firebase_admin initialized project=%s", cred_dict.get("project_id"))
            return True
        except Exception as exc:
            _init_error = f"init_failed:{exc}"
            logger.exception("[FCM] init failed")
            return False


def _tune_connection_pool(pool_size: int) -> None:
    """firebase-admin 이 쓰는 requests 세션의 연결 풀을 확대한다.

    기본 풀(pool_maxsize=10)로는 send_each 가 토큰마다 스레드를 띄워 동시 발송할 때
    남는 연결이 없어 매번 새 TLS 핸드셰이크를 치른다 — 경기 시작 푸시 폭주 시 CPU 가 튀고
    "Connection pool is full, discarding connection" 경고가 쏟아진다. 발송 규모만큼 풀을 키우면
    연결을 재사용해 폭주 비용이 사라진다. 병렬·실시간 발송 방식은 그대로 유지된다.

    firebase-admin 의 내부(_get_messaging_service/_client/session)에 의존하므로, SDK 구조가
    바뀌어도 발송 자체는 계속되도록 실패를 삼키고 튜닝만 건너뛴다.
    """
    if pool_size <= 0:
        return
    try:
        import firebase_admin
        import requests.adapters
        from firebase_admin import messaging

        service = messaging._get_messaging_service(firebase_admin.get_app())
        session = service._client.session
        # 기존 재시도 설정(DEFAULT_RETRY_CONFIG: 500/503 최대 4회)을 보존한 채 풀만 키운다.
        try:
            max_retries = session.get_adapter("https://fcm.googleapis.com").max_retries
        except Exception:
            max_retries = None
        adapter_kwargs: dict[str, Any] = {
            "pool_connections": pool_size,
            "pool_maxsize": pool_size,
        }
        if max_retries is not None:
            adapter_kwargs["max_retries"] = max_retries
        adapter = requests.adapters.HTTPAdapter(**adapter_kwargs)
        session.mount("https://", adapter)
        session.mount("http://", adapter)
        logger.info("[FCM] connection pool tuned pool_maxsize=%d", pool_size)
    except Exception:
        logger.warning("[FCM] connection pool tuning skipped", exc_info=True)


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
