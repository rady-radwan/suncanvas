# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.suncanvas.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.suncanvas.**$$serializer { *; }
-keepclassmembers class com.suncanvas.** { *** Companion; }
