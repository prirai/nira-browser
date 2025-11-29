# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Security hardening rules
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# OPTIMIZED: Keep only essential Mozilla Components classes instead of everything
# Old rule was too broad: -keep class mozilla.components.** { *; }

# Keep GeckoView engine (critical for browser functionality)
-keep class org.mozilla.geckoview.** { *; }
-keep class mozilla.components.browser.engine.gecko.** { *; }

# Keep browser state and store (core architecture)
-keep class mozilla.components.browser.state.** { *; }
-keep class mozilla.components.lib.state.** { *; }

# Keep web extension support (required for addons)
-keep class mozilla.components.concept.engine.webextension.** { *; }
-keep class mozilla.components.feature.addons.** { *; }
-keep class mozilla.components.support.webextensions.** { *; }

# Keep feature interfaces and public APIs
-keep public class * extends mozilla.components.support.base.feature.LifecycleAwareFeature { *; }
-keep public class * extends mozilla.components.support.base.feature.UserInteractionHandler { *; }
-keep class mozilla.components.concept.** { *; }

# Keep UI components that use reflection or databinding
-keep class mozilla.components.browser.toolbar.** { *; }
-keep class mozilla.components.browser.menu.** { *; }
-keep class mozilla.components.ui.** { *; }

# Keep storage implementations
-keep class mozilla.components.browser.storage.sync.** { *; }
-keep class mozilla.components.browser.session.storage.** { *; }
-keep class mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage { *; }

# Keep search components
-keep class mozilla.components.feature.search.** { *; }
-keep class mozilla.components.browser.state.search.** { *; }

# Keep support utilities that may use reflection
-keep class mozilla.components.support.base.** { *; }
-keep class mozilla.components.support.ktx.** { *; }
-keep class mozilla.components.support.utils.** { *; }
-keep class mozilla.components.support.locale.** { *; }

# Keep service components
-keep class mozilla.components.service.location.** { *; }

# Allow shrinking of unused feature implementations
-dontwarn mozilla.components.feature.webcompat.**
-dontwarn mozilla.components.feature.webnotifications.**

# Fix R8 missing Kotlin annotation classes
-dontwarn kotlin.annotations.jvm.MigrationStatus
-dontwarn kotlin.annotations.jvm.UnderMigration
-keep class kotlin.annotations.jvm.** { *; }

# Keep application classes that are used by reflection
-keep public class com.prirai.android.nira.** extends androidx.fragment.app.Fragment
-keep public class com.prirai.android.nira.** extends androidx.appcompat.app.AppCompatActivity

# Remove debugging information in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Remove Mozilla Logger debug calls
-assumenosideeffects class mozilla.components.support.base.log.logger.Logger {
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

# Preserve line numbers for crash reports but hide source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Additional optimization flags
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Merge classes and interfaces aggressively
-mergeinterfacesaggressively

# Remove unused code more aggressively
-repackageclasses ''

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}