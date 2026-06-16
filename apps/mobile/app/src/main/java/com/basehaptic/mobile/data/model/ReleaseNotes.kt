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
            version = "1.1.4",
            subtitle = "6/14일 배포",
            bullets = listOf(
                "잠금화면 토글: LIVE 경기를 휴대폰 잠금화면에서 볼 수 있어요",
                "Watch 토글: LIVE 경기를 스마트워치에서 볼 수 있어요",
                "전체 순위 보기: 아이콘을 누르면 전체 순위를 바로 확인할 수 있어요",
                "전체 일정 보기: 응원팀 시즌 일정을 달력으로 한눈에 확인할 수 있어요",
                "점수 보기: 경기 카드에서 최신 점수와 진행 상황을 더 쉽게 볼 수 있어요",
            ),
        ),
    )

    fun notes(version: String): ReleaseNote? = all.firstOrNull { it.version == version }

    fun latest(): ReleaseNote? = all.firstOrNull()
}
