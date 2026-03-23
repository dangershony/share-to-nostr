# Add project specific ProGuard rules here.

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sharetonostr.**$$serializer { *; }
-keepclassmembers class com.sharetonostr.** {
    *** Companion;
}
-keepclasseswithmembers class com.sharetonostr.** {
    kotlinx.serialization.KSerializer serializer(...);
}
