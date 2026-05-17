# Danbron ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions

# ===== GSON =====
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

# ===== RETROFIT =====
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# ===== OKHTTP =====
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== DANBRON API CLASSES (Gson deserializes these) =====
-keep class com.danbron.app.api.** { *; }
-keepclassmembers class com.danbron.app.api.** {
    <fields>;
    <init>(...);
}

# ===== DANBRON DATA MODELS =====
-keep class com.danbron.app.data.** { *; }
-keep class com.danbron.app.data.models.** { *; }
-keepclassmembers class com.danbron.app.data.models.** {
    <fields>;
    <init>(...);
}

# ===== Keep all data classes used by Gson/Retrofit =====
-keep class com.danbron.app.api.GroqMessage { *; }
-keep class com.danbron.app.api.GroqRequest { *; }
-keep class com.danbron.app.api.GroqResponse { *; }
-keep class com.danbron.app.api.GroqChoice { *; }
-keep class com.danbron.app.api.GroqTranscriptionResponse { *; }
-keep class com.danbron.app.api.OpenRouterRequest { *; }
-keep class com.danbron.app.api.OpenRouterResponse { *; }
-keep class com.danbron.app.api.AnthropicRequest { *; }
-keep class com.danbron.app.api.AnthropicResponse { *; }
-keep class com.danbron.app.api.AnthropicContent { *; }
-keep class com.danbron.app.api.ApiMessage { *; }
-keep class com.danbron.app.api.BackendChatRequest { *; }
-keep class com.danbron.app.api.BackendChatResponse { *; }
-keep class com.danbron.app.sync.** { *; }
