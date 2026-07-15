# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# data.model 클래스는 org.json 수동 파싱만 사용 (Gson/Moshi 등 리플렉션 직렬화 없음) → keep 불필요.
# Compose 는 R8 기본 규칙 + 라이브러리 consumer rules 로 충분 → 전체 keep 제거 (R8 최적화 복원).

# OkHttp / WebSocket — 라이브러리 consumer rules 로 충분, 전체 keep 불필요
-dontwarn okhttp3.**
-dontwarn okio.**

# Supabase / Ktor
-dontwarn io.ktor.**
-keep class io.github.jan.supabase.** { *; }

# Wearable Data Layer
-keep class com.google.android.gms.wearable.** { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

