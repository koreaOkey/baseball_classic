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
    val eventDescription: String,
    // --- 서버 regret-top5 페이로드 소비용 (선택 화면 실명 표기 전용) ---
    /** 서버 항목 종류 (예: "batter" / "pitcher"). */
    val kind: String? = null,
    /** 팀 사이드 ("home" / "away"). 박스스코어 명단 선택에 사용. */
    val teamSide: String? = null,
    /** 타순 (타자 항목). 박스스코어 조인 키. */
    val battingOrder: Int? = null,
    /** 등판 순서 (투수 항목). 박스스코어 조인 키. */
    val appearanceOrder: Int? = null,
    /**
     * 박스스코어에서 해소된 실명 — 선택 화면 TargetRow에서만 노출한다.
     * 룸·완파 화면은 절대 이 값을 읽지 않는다([VentingTarget.roleLabel]/[eventDescription]가 익명 방화벽).
     */
    val playerName: String? = null,
)

/** 항상 마지막(6번째)에 고정되는 감독 선택지. */
data class VentingManagerOption(
    val eventDescription: String
) {
    val id: String = "venting_manager_fixed"
    val label: String = "감독"
}

enum class VentingGameResult {
    WIN, LOSS, DRAW, CANCELED, POSTPONED,

    /** 경기 진행 중 (라이브 진입 전용 — 홈카드 오픈 조건에는 해당 없음) */
    IN_PROGRESS
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
    val managerEventDescription: String,
    /** 라이브 진입 시 현재 이닝 라벨 (예: "7회말"). null이면 종료 경기("최종")로 표시. */
    val inningLabel: String? = null
)

/** 선택된 분풀이 대상 (선수 후보, 감독, 또는 직접 입력). */
sealed class VentingTarget {
    data class Player(val candidate: RegretCandidate) : VentingTarget()
    data class Manager(val option: VentingManagerOption) : VentingTarget()

    /**
     * 사용자가 이름을 직접 입력한 대상.
     * 입력값은 화면 표시 전용이며 어디에도 저장·전송하지 않는다(초상권 리스크 없음).
     */
    data class Custom(val name: String) : VentingTarget()

    val roleLabel: String
        get() = when (this) {
            is Player -> candidate.roleLabel
            is Manager -> option.label
            is Custom -> name
        }

    val eventDescription: String
        get() = when (this) {
            is Player -> candidate.eventDescription
            is Manager -> option.eventDescription
            is Custom -> "직접 지목한 분풀이 대상"
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
