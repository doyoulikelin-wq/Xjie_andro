# Retrofit / OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.xjie.app.**$$serializer { *; }
-keepclassmembers class com.xjie.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.xjie.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

# Compose
-keep class androidx.compose.runtime.** { *; }
