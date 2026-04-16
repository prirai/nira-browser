# ProGuard rules for Nira Browser

# 1. Global package preservation to prevent Package.getName() NullPointerExceptions
# This is critical for libraries that use reflection to look up package information.
-keeppackagenames org.mozilla.**
-keeppackagenames mozilla.**
-keeppackagenames com.prirai.**
-keeppackagenames org.yaml.snakeyaml.**

# 2. Prevent R8 from moving classes to the default package or stripping package info
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable

# 3. Comprehensive Keep rules for GeckoView (CRITICAL)
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep interface org.mozilla.geckoview.** { *; }
-keep interface org.mozilla.gecko.** { *; }

# 4. Comprehensive Keep rules for Mozilla Components
-keep class mozilla.components.** { *; }
-keep interface mozilla.components.** { *; }

# 5. Keep SnakeYAML (Used by GeckoView DebugConfig)
# The crash in TypeDescription.<clinit> is due to Package.getName() returning null.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# 6. Keep all BuildConfig classes
-keep class **.BuildConfig { *; }

# 7. Keep application classes
-keep class com.prirai.android.nira.** { *; }
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application

# 8. Specific fixes for known GeckoView/Mozilla issues
-dontwarn org.mozilla.**
-dontwarn mozilla.**
-keep class **.package-info { *; }

# 9. Optimization settings
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 10. Support for Kotlin and Compose
-keep class kotlin.annotations.jvm.** { *; }
-dontwarn kotlin.annotations.jvm.**

# 11. Removal of logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
