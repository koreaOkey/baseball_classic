# Add project specific ProGuard rules here.

# Keep Wear OS classes
-keep class androidx.wear.** { *; }
-keep class com.google.android.gms.wearable.** { *; }

# data 클래스는 SharedPreferences/DataMap 수동 매핑만 사용 (리플렉션 직렬화 없음) → keep 불필요.
# Compose 는 R8 기본 규칙 + 라이브러리 consumer rules 로 충분 → 전체 keep 제거 (R8 최적화 복원).

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

