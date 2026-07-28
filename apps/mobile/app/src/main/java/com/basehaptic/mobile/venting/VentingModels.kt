package com.basehaptic.mobile.venting

import com.basehaptic.mobile.R

// 분풀이 모드 Phase 1 목업 (iOS VentingMode/Models 포팅).
// 실명·등번호·실제 외형 정보를 포함하지 않으며, 사건 문구만 노출한다.

/** 분풀이 모드의 선수 후보 1명. */
data class RegretCandidate(
    val id: String,
    /** 역할·포지션 레이블 (예: "3번 타자") — 실명·등번호 금지 */
    val roleLabel: String,
    /** 사건 문구 (예: "8회 2사 만루 삼진") */
    val eventDescription: String
)

/** 항상 마지막(6번째)에 고정되는 감독 선택지. */
data class VentingManagerOption(
    val eventDescription: String
) {
    val id: String = "venting_manager_fixed"
    val label: String = "감독"
}

enum class VentingGameResult {
    WIN, LOSS, DRAW, CANCELED, POSTPONED
}

/**
 * 분풀이 모드 오픈 판정에 사용하는 경기 입력 데이터.
 * Phase 1: 목업 값 주입. Phase 2: 백엔드 API 응답으로 교체.
 */
data class VentingGameContext(
    val gameId: String,
    /** 경기 날짜 (KST, "yyyy-MM-dd") */
    val gameDate: String,
    val gameResult: VentingGameResult,
    /** 마이팀 코드 (Android는 Team.name — 예: "HANWHA") */
    val myTeamId: String,
    val myScore: Int,
    val opponentScore: Int,
    /** 선수 후보 목록 (최대 5명, 부분 표시 허용) */
    val candidates: List<RegretCandidate>,
    /** 감독 선택지 사건 문구 */
    val managerEventDescription: String
)

/** 선택된 분풀이 대상 (선수 후보 또는 감독). */
sealed class VentingTarget {
    data class Player(val candidate: RegretCandidate) : VentingTarget()
    data class Manager(val option: VentingManagerOption) : VentingTarget()

    val roleLabel: String
        get() = when (this) {
            is Player -> candidate.roleLabel
            is Manager -> option.label
        }

    val eventDescription: String
        get() = when (this) {
            is Player -> candidate.eventDescription
            is Manager -> option.eventDescription
        }
}

/**
 * 분풀이 도구. 룸 화면 하단 트레이에 표시되며,
 * 선택된 도구가 인형 탭 시 타격 연출(도구 내려치기 + 히트 이펙트)에 사용된다.
 */
enum class VentingTool(
    val label: String,
    val drawableRes: Int,
    /**
     * 타격 연출 시 스프라이트에 적용하는 기본 회전(도).
     * 스프라이트 원본 방향이 도구마다 달라서, 타격면이 좌하단의 인형을 향하도록 보정한다.
     * (iOS VentingTool.strikeBaseRotation과 동일 값)
     */
    val strikeBaseRotation: Float,
    /** 타격 연출 시 좌우 반전 여부 (슬리퍼: 바닥면이 인형 쪽을 향하도록). */
    val strikeFlipsHorizontally: Boolean = false,
    /** 타격 연출 시 상하 반전 여부 (프라이팬: 내려칠 때 바닥이 보이도록). */
    val strikeFlipsVertically: Boolean = false
) {
    FIST("맨손", R.drawable.venting_tool_fist, 135f),
    HAMMER("뿅망치", R.drawable.venting_tool_hammer, 150f),
    BAT("야구방망이", R.drawable.venting_tool_bat, 120f),
    SLIPPER("슬리퍼", R.drawable.venting_tool_slipper, -45f, strikeFlipsHorizontally = true),
    FRYPAN("프라이팬", R.drawable.venting_tool_frypan, -195f, strikeFlipsVertically = true);

    companion object {
        /** 타격 히트 이펙트 스프라이트 (별·충격파, 투명 배경). */
        val hitEffectRes: Int = R.drawable.venting_hit_effect
    }
}
