# Danbron ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.danbron.app.data.models.** { *; }
-keep class com.danbron.app.api.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
