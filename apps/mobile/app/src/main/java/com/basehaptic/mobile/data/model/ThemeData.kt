package com.basehaptic.mobile.data.model

import androidx.compose.ui.graphics.Color

enum class ThemeCategory {
    FREE,       // 기본형 (처음부터 잠금 해제)
    AD_REWARD,  // 광고 시청 후 잠금 해제
    PREMIUM     // 인앱 결제
}

data class ThemeData(
    val id: String,
    val name: String,
    val category: ThemeCategory,
    val colors: ThemeColors,
    val backgroundImage: String? = null,
    val previewImage: String? = null,
    val price: String = ""
)

data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val accent: Color
)

object ThemeStore {
    val allThemes: List<ThemeData> = listOf(
        // 기본형 (무료)
        ThemeData(
            id = "default",
            name = "기본형",
            category = ThemeCategory.FREE,
            colors = ThemeColors(
                primary = Color(0xFF3B82F6),
                secondary = Color(0xFF2563EB),
                accent = Color(0xFF60A5FA)
            ),
            previewImage = "theme_preview_default"
        ),
        // 광고 시청 무료 테마
        ThemeData(
            id = "baseball_love",
            name = "야구가 좋아",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFDC141E),
                secondary = Color(0xFFAA0A14),
                accent = Color(0xFFFF96AA)
            ),
            backgroundImage = "theme_baseball_love",
            previewImage = "theme_preview_baseball_love"
        ),
        ThemeData(
            id = "midnight_indigo",
            name = "미드나이트 인디고",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF131230),
                secondary = Color(0xFF1E1C4B),
                accent = Color(0xFF60A5FA)
            ),
            previewImage = "theme_preview_midnight_indigo"
        ),
        ThemeData(
            id = "cherry_rose",
            name = "체리 로즈",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFC30452),
                secondary = Color(0xFF8E023B),
                accent = Color(0xFFF472B6)
            ),
            previewImage = "theme_preview_cherry_rose"
        ),
        ThemeData(
            id = "burgundy_gold",
            name = "버건디 골드",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF820024),
                secondary = Color(0xFF5C001A),
                accent = Color(0xFFFCA5A5)
            ),
            previewImage = "theme_preview_burgundy_gold"
        ),
        ThemeData(
            id = "royal_blue",
            name = "로열 블루",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF074CA1),
                secondary = Color(0xFF053678),
                accent = Color(0xFF93C5FD)
            ),
            previewImage = "theme_preview_royal_blue"
        ),
        ThemeData(
            id = "deep_navy",
            name = "딥 네이비",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF041E42),
                secondary = Color(0xFF021230),
                accent = Color(0xFF93C5FD)
            ),
            previewImage = "theme_preview_deep_navy"
        ),
        ThemeData(
            id = "crimson_red",
            name = "크림슨 레드",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFCE0E2D),
                secondary = Color(0xFF960A20),
                accent = Color(0xFFFCA5A5)
            ),
            previewImage = "theme_preview_crimson_red"
        ),
        ThemeData(
            id = "charcoal_black",
            name = "차콜 블랙",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF1A1A1A),
                secondary = Color(0xFF000000),
                accent = Color(0xFFA3A3A3)
            ),
            previewImage = "theme_preview_charcoal_black"
        ),
        ThemeData(
            id = "sunset_orange",
            name = "선셋 오렌지",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFFF6600),
                secondary = Color(0xFFCC5200),
                accent = Color(0xFFFDBA74)
            ),
            previewImage = "theme_preview_sunset_orange"
        ),
        ThemeData(
            id = "fire_red",
            name = "파이어 레드",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFFEA0029),
                secondary = Color(0xFFB5001F),
                accent = Color(0xFFFCA5A5)
            ),
            previewImage = "theme_preview_fire_red"
        ),
        ThemeData(
            id = "slate_blue",
            name = "슬레이트 블루",
            category = ThemeCategory.AD_REWARD,
            colors = ThemeColors(
                primary = Color(0xFF315288),
                secondary = Color(0xFF213A61),
                accent = Color(0xFF93C5FD)
            ),
            previewImage = "theme_preview_slate_blue"
        ),
        // 프리미엄 유료 테마 (아직 기능 미구현)
//        ThemeData(
//            id = "puppy",
//            name = "멍멍이",
//            category = ThemeCategory.PREMIUM,
//            colors = ThemeColors(
//                primary = Color(0xFFDC2626),
//                secondary = Color(0xFFA01414),
//                accent = Color(0xFFFFB6C1)
//            ),
//            backgroundImage = "theme_puppy",
//            price = "₩1,200"
//        ),
//        ThemeData(
//            id = "puppy2",
//            name = "멍멍이2",
//            category = ThemeCategory.PREMIUM,
//            colors = ThemeColors(
//                primary = Color(0xFFDC2626),
//                secondary = Color(0xFFA01414),
//                accent = Color(0xFFFFB6C1)
//            ),
//            backgroundImage = "theme_puppy2",
//            price = "₩1,200"
//        ),
    )
}
