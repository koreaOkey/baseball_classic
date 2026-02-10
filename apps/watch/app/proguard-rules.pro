# Add project specific ProGuard rules here.

# Keep Wear OS classes
-keep class androidx.wear.** { *; }
-keep class com.google.android.gms.wearable.** { *; }

# Keep data classes
-keep class com.basehaptic.watch.data.** { *; }

