# kotlinx.serialization: le classi del database si leggono per riflessione sui nomi dei campi
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class it.backline.app.data.**$$serializer { *; }
-keepclassmembers class it.backline.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class it.backline.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
