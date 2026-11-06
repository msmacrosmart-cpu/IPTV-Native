# IPTV Native - ProGuard / R8 rules
# Minification is disabled by default for release builds, but these rules keep the
# project safe if it is enabled later.

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.iptv.**$$serializer { *; }
-keepclassmembers class com.example.iptv.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.iptv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Media3
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
