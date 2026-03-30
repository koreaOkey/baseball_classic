# Add project specific ProGuard rules here.

# Keep Wear OS classes
-keep class androidx.wear.** { *; }
-keep class com.google.android.gms.wearable.** { *; }

# Keep data classes
-keep class com.basehaptic.watch.data.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Tiles / ProtoLayout
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.protolayout.** { *; }

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

