# ============================================================================
# FocusGuard ProGuard / R8 rules
# ============================================================================
# FocusGuard is a Device Owner app that uses reflection-sensitive Android
# entry points (AccessibilityService, DeviceAdminReceiver, foreground services,
# BroadcastReceiver, PackageInstaller session callbacks). R8 must NOT strip
# or rename these — Android instantiates them by their full class name from
# the manifest, and renaming breaks the manifest binding at runtime.
#
# Everything ELSE (utility objects, the keyword/package lists inside
# GuardAccessibilityService, internal helper methods) gets obfuscated by
# default — that's the whole point of enabling minification.
# ============================================================================

# --- Standard Android hygiene: keep line numbers in stack traces ---
# Critical for debugging crash logs from a real device (you can't attach a
# debugger once FocusGuard is the Device Owner and USB debugging is blocked).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Keep all manifest-referenced classes ---
# Activities, Services, BroadcastReceivers, and DeviceAdminReceivers are
# instantiated by the Android framework via Class.forName using the exact
# class name from AndroidManifest.xml. R8 must not rename them.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.admin.DeviceAdminReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService

# --- Keep our specific entry-point classes (belt-and-braces) ---
# These are referenced by name from AndroidManifest.xml — keep them + their
# public/protected members intact so the framework can call onCreate,
# onReceive, onAccessibilityEvent, etc.
-keep class com.focus.guard.MainActivity { *; }
-keep class com.focus.guard.AppPickerActivity { *; }
-keep class com.focus.guard.WebsiteBlockActivity { *; }
-keep class com.focus.guard.StudyLogActivity { *; }
-keep class com.focus.guard.NotesActivity { *; }
-keep class com.focus.guard.AlarmListActivity { *; }
-keep class com.focus.guard.AlarmRingActivity { *; }
-keep class com.focus.guard.StudyBuddyActivity { *; }
-keep class com.focus.guard.GuardAccessibilityService { *; }
-keep class com.focus.guard.GuardForegroundService { *; }
-keep class com.focus.guard.DnsFilterService { *; }
-keep class com.focus.guard.GuardDeviceAdminReceiver { *; }
-keep class com.focus.guard.PackageInstallReceiver { *; }
-keep class com.focus.guard.InstallResultReceiver { *; }
-keep class com.focus.guard.BootReceiver { *; }
-keep class com.focus.guard.AlarmReceiver { *; }

# --- Keep companion-object static fields referenced from manifest XML ---
# device_admin.xml references the DeviceAdminReceiver by name; the
# accessibility_config.xml references the service. Already covered by the
# rule above, but be explicit about companion objects since R8 sometimes
# strips them as "unused" when only Kotlin reflection touches them.
-keepclassmembers class com.focus.guard.GuardDeviceAdminReceiver$* { *; }
-keepclassmembers class com.focus.guard.GuardAccessibilityService$* { *; }

# --- Keep enums used by reflection (ApplyResult in SystemDnsEnforcer) ---
-keepclassmembers enum com.focus.guard.SystemDnsEnforcer$ApplyResult {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Keep Parcelable creators (used by AlarmScheduler / Intent extras) ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# --- Keep classes referenced by Class.forName in the codebase ---
# SelfUpdater uses PendingIntent.getBroadcast targeting InstallResultReceiver
# by class — already covered by the BroadcastReceiver rule, but be explicit.
-keep class com.focus.guard.SelfUpdater { *; }
-keep class com.focus.guard.InstallResultReceiver { *; }

# --- SharedPreferences key names: do not inline / rename accessor methods ---
# Blocklist, StreakManager, CreditManager, and the policy prefs all use
# string-literal keys; the methods that read/write them are referenced
# across files. R8 will inline small methods, which is fine — but the
# SharedPreferences keys themselves are runtime string constants and
# survive inlining. No special rule needed; this comment exists so a
# future reader knows we considered it.

# --- Kotlin metadata: strip (smaller APK) but keep signatures ---
# Default Android rule already does this; we just enable it explicitly.
-assumenosideeffects class kotlin.Metadata { *; }

# --- Disable aggressive optimization that can break accessibility services ---
# R8's "rearrange" optimization has been known to move code paths in ways
# that confuse Android's accessibility framework on some OEMs (ColorOS
# included). Keep -allowaccessmodification for size, but turn off the
# aggressive over-optimization that can remove "unused" empty catch blocks
# (we rely on empty catches for safe-policy-call semantics).
-dontoptimize
-allowaccessmodification

# --- WebView / JS interfaces (none used, but be explicit) ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Remove logging from release builds ---
# Optional but recommended: strips Log.d / Log.v calls from the release APK,
# making it harder to reverse-engineer the bypass-detection logic via logcat
# on a borrowed device. Log.i / Log.w / Log.e are kept — they're useful for
# post-mortem crash analysis and the user's own debugging via adb logcat
# during maintenance mode.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static java.lang.String getStackTraceString(...);
}
