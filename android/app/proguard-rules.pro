# ─── Koto ProGuard / R8 rules ─────────────────────────────────────────────────
#
# Strategy:
#   1. Aggressive obfuscation of package names (repackageclasses)
#   2. Strip ALL Log.d/Log.v/Log.i calls in release builds
#   3. Keep only Retrofit/Gson/Hilt reflection surfaces
#   4. No hardcoded secrets — all are injected at build time from env
#   5. Certificate pinning enforced at OkHttp level (see NetworkModule)
#
# ──────────────────────────────────────────────────────────────────────────────

# Aggressive optimization & obfuscation
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-overloadaggressively
-dontusemixedcaseclassnames

# Strip debug logs in release — hide internal behavior from reverse engineers
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** println(...);
}
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static *** println(...);
    public static *** print(...);
}

# Strip toString() debug helpers
-assumenosideeffects class java.lang.StringBuilder {
    public java.lang.StringBuilder append(java.lang.String);
}

# ─── Reflection surfaces (keep) ───────────────────────────────────────────────

# Hilt entry points
-keep class dagger.hilt.** { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Retrofit interfaces and DTOs — field names used by Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keep class retrofit2.** { *; }
-keep interface run.koto.data.remote.api.** { *; }
-keep class run.koto.data.remote.api.** { *; }

# Room entities
-keep class run.koto.data.local.entity.** { *; }

# Domain models serialized via Gson — keep field names
-keepclassmembers class run.koto.domain.model.** {
    <fields>;
}

# BouncyCastle crypto
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Gson — keep @SerializedName fields
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Compose
-dontwarn androidx.compose.**

# ─── Android framework ────────────────────────────────────────────────────────

-keepclassmembers class * extends android.app.Activity { public void *(android.view.View); }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native library loading (Tor binary + JNA)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class net.java.dev.jna.** { *; }
-dontwarn java.awt.**

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ─── Hide stack traces in production ──────────────────────────────────────────
# Removes source file names and line numbers from stack traces so crashes
# leak less information. Combined with mapping.txt we can still de-obfuscate.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
