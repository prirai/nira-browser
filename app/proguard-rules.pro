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

# Keep Mozilla Components interfaces and classes that must remain accessible
-keep class mozilla.components.** { *; }
-keep class org.mozilla.** { *; }

# Keep GeckoView classes
-keep class org.mozilla.geckoview.** { *; }

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
}

# Preserve line numbers for crash reports but hide source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}