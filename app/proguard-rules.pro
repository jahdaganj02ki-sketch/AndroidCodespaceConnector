# Kotlinx-Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.codespacemanager.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.codespacemanager.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.codespacemanager.app.**$$serializer { *; }
-keepclassmembers class com.codespacemanager.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.codespacemanager.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
