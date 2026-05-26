# Keep kotlinx-serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.juanigsrz.driverhelper.**$$serializer { *; }
-keepclassmembers class com.juanigsrz.driverhelper.** {
    *** Companion;
}
-keepclasseswithmembers class com.juanigsrz.driverhelper.** {
    kotlinx.serialization.KSerializer serializer(...);
}
