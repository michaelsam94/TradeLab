# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.michael.tradelab.**$$serializer { *; }
-keepclassmembers class com.michael.tradelab.** { *** Companion; }
-keepclasseswithmembers class com.michael.tradelab.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions*
-dontwarn okhttp3.**
-dontwarn okio.**

# Play Billing
-keep class com.android.vending.billing.** { *; }
