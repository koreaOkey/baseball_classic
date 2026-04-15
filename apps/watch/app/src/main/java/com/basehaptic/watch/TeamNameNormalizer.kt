package com.basehaptic.watch

/**
 * 팀 코드("DOOSAN"), 백엔드 전체명("두산 베어스"), 마스코트("베어스") 등
 * 어느 형식이든 canonical 마스코트로 변환. 멱등.
 *
 * 비교(isMyTeamHome/Away 판정)에 사용하며, iOS watchOS의
 * WatchConnectivityManager.displayTeamName 과 동일 매핑을 유지해야 한다.
 */
internal fun displayTeamName(name: String): String {
    val n = name.trim().lowercase()
    if (n.isEmpty()) return name
    return when {
        "doosan" in n || "두산" in n || "베어스" in n -> "베어스"
        "lg" in n || "엘지" in n || "트윈스" in n -> "트윈스"
        "kiwoom" in n || "키움" in n || "히어로즈" in n || "넥센" in n -> "히어로즈"
        "samsung" in n || "삼성" in n || "라이온즈" in n -> "라이온즈"
        "lotte" in n || "롯데" in n || "자이언츠" in n -> "자이언츠"
        "ssg" in n || "lander" in n || "에스에스지" in n || "랜더스" in n -> "랜더스"
        "kt" in n || "wiz" in n || "케이티" in n || "위즈" in n -> "위즈"
        "hanwha" in n || "한화" in n || "이글스" in n -> "이글스"
        "kia" in n || "기아" in n || "타이거즈" in n -> "타이거즈"
        "nc" in n || "dinos" in n || "엔씨" in n || "다이노스" in n -> "다이노스"
        else -> name
    }
}
