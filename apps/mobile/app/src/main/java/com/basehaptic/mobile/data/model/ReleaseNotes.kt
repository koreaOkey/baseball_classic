package com.basehaptic.mobile.data.model

data class ReleaseNote(
    val version: String,
    val subtitle: String,
    val bullets: List<String>,
)

object ReleaseNotes {
    // 새 버전 출시 시 entry 1개를 추가한다.
    // version 은 BuildConfig.VERSION_NAME 과 정확히 일치해야 한다.
    // 일치하는 entry 가 없으면 모달이 노출되지 않는다.
    val all: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "1.0.3",
            subtitle = "더 좋아진 야구봄",
            bullets = listOf(
                "워치 앱 연동 가이드 추가",
                "워치로 투구수 확인 기능 추가",
            ),
        ),
    )

    fun notes(version: String): ReleaseNote? = all.firstOrNull { it.version == version }
}
