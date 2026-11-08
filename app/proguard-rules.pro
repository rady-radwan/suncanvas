# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class app.suncanvas.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.suncanvas.**$$serializer { *; }
-keepclassmembers class app.suncanvas.** { *** Companion; }
