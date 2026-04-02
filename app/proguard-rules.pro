# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Security hardening rules
# -obfuscationdictionary dictionary.txt
# -classobfuscationdictionary dictionary.txt
# -packageobfuscationdictionary dictionary.txt

# Preserve package names to prevent Package.getName() from returning null
-keeppackagenames org.mozilla.**
-keeppackagenames com.prirai.android.nira.**

# Keep GeckoView and Mozilla Components (CRITICAL)
# Many Mozilla components use reflection or JNI that breaks with obfuscation
-keep class org.mozilla.** { *; }
-keep interface org.mozilla.** { *; }

# Keep application classes that are used by reflection
-keep public class com.prirai.android.nira.** extends androidx.fragment.app.Fragment
-keep public class com.prirai.android.nira.** extends androidx.appcompat.app.AppCompatActivity
-keep class com.prirai.android.nira.** { *; }

# Keep browser state and store (core architecture)
-keep class org.mozilla.components.browser.state.** { *; }
-keep class org.mozilla.components.lib.state.** { *; }

# Keep web extension support (required for addons)
-keep class org.mozilla.components.concept.engine.webextension.** { *; }
-keep class org.mozilla.components.feature.addons.** { *; }
-keep class org.mozilla.components.support.webextensions.** { *; }

# Keep feature interfaces and public APIs
-keep public class * extends org.mozilla.components.support.base.feature.LifecycleAwareFeature { *; }
-keep public class * extends org.mozilla.components.support.base.feature.UserInteractionHandler { *; }
-keep class org.mozilla.components.concept.** { *; }

# Keep UI components that use reflection or databinding
-keep class org.mozilla.components.browser.toolbar.** { *; }
-keep class org.mozilla.components.browser.menu.** { *; }
-keep class org.mozilla.components.ui.** { *; }

# Keep storage implementations
-keep class org.mozilla.components.browser.storage.sync.** { *; }
-keep class org.mozilla.components.browser.session.storage.** { *; }
-keep class org.mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage { *; }

# Keep search components
-keep class org.mozilla.components.feature.search.** { *; }
-keep class org.mozilla.components.browser.state.search.** { *; }

# Keep support utilities that may use reflection
-keep class org.mozilla.components.support.base.** { *; }
-keep class org.mozilla.components.support.ktx.** { *; }
-keep class org.mozilla.components.support.utils.** { *; }
-keep class org.mozilla.components.support.locale.** { *; }

# Keep service components
-keep class org.mozilla.components.service.location.** { *; }

# Allow shrinking of unused feature implementations
-dontwarn org.mozilla.components.feature.webcompat.**
-dontwarn org.mozilla.components.feature.webnotifications.**

# Fix R8 missing Kotlin annotation classes
-dontwarn kotlin.annotations.jvm.MigrationStatus
-dontwarn kotlin.annotations.jvm.UnderMigration
-keep class kotlin.annotations.jvm.** { *; }

# Remove debugging information in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Remove Mozilla Logger debug calls
-assumenosideeffects class org.mozilla.components.support.base.log.logger.Logger {
    public *** debug(...);
}

# Remove println and System.out debugging
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Optimization: Remove assertions in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(...);
    static void checkNotNullParameter(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkNotNullExpressionValue(...);
}

# Preserve attributes needed for reflection and crash reports
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Additional optimization flags
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Merge classes and interfaces aggressively
-mergeinterfacesaggressively

# Repackaging can break package-based lookups
# -repackageclasses ''
