# AutoFlow release obfuscation rules.
#
# The app intentionally persists several public-field models with Gson. R8 may rename the
# classes, but those JSON field names are part of project files and SharedPreferences and must
# remain stable across Debug/Release and future versions.

# AGP 9 only ships the optimizing default rules. Disable behavior-changing optimization for the
# first hardened release while retaining shrinking and name obfuscation.
-dontoptimize

# Keep enough metadata for Gson TypeToken and useful retraced crash reports without exposing
# original source file names in the APK.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Rhino contains optional desktop-only java.beans paths which Android never executes.
-dontwarn java.beans.**

# Gson generic type capture. Several stores use anonymous TypeToken<List/Map<...>> classes.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Preserve fields explicitly annotated for Gson while still allowing their classes to shrink
# and be renamed.
-keepclassmembers,allowoptimization class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Operation JSON files are a public compatibility boundary. Preserve field names only; operation
# classes, handlers, dialogs and execution code remain eligible for obfuscation.
-keepclassmembers,allowoptimization class com.auto.master.Task.Operation.** {
    <fields>;
}

# Config UI schemas are exported inside projects as JSON.
-keepclassmembers,allowoptimization class com.auto.master.configui.ConfigUiSchema {
    <fields>;
}
-keepclassmembers,allowoptimization class com.auto.master.configui.ConfigUiPage {
    <fields>;
}
-keepclassmembers,allowoptimization class com.auto.master.configui.ConfigUiComponent {
    <fields>;
}
-keepclassmembers,allowoptimization class com.auto.master.configui.ConfigUiOption {
    <fields>;
}

# Node floating-button settings are stored per task as JSON.
-keepclassmembers,allowoptimization class com.auto.master.floatwin.NodeFloatButtonConfig {
    <fields>;
}

# Gesture files are shared between recording, preview and runtime playback.
-keepclassmembers,allowoptimization class com.auto.master.auto.GestureOverlayView$GestureNode {
    <fields>;
}
-keepclassmembers,allowoptimization class com.auto.master.auto.GestureOverlayView$GestureStroke {
    <fields>;
}
-keepclassmembers,allowoptimization class com.auto.master.auto.GestureOverlayView$PointF {
    <fields>;
}

# Persistent scheduler/trigger entries live in SharedPreferences as Gson JSON.
-keepclassmembers,allowoptimization class com.auto.master.scheduler.ScheduledTask {
    <fields>;
}
-keepclassmembers,allowoptimization class com.auto.master.scheduler.AppNotificationTrigger {
    <fields>;
}

# Keep names for the small set of Android entry classes. Their internal implementation remains
# eligible for shrinking and member obfuscation; manifest rewriting/default Android rules keep
# the framework lifecycle contract intact.
-keepnames class com.auto.master.AtommApplication
-keepnames class com.auto.master.auto.AutoAccessibilityService
-keepnames class com.auto.master.floatwin.FloatWindowService
-keepnames class com.auto.master.capture.MediaProjectionCaptureService
-keepnames class com.auto.master.scheduler.TaskScheduleReceiver

# Remove verbose/debug logging calls and their eagerly-built message strings from release code.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
