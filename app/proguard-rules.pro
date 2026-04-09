# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Rhino may reference desktop JDK java.beans APIs that do not exist on Android.
# Those code paths are not used by this app, so suppress missing-class failures in release shrink.
-dontwarn java.beans.**

# Operation panel relies on stable operation model fields when reading/writing operations.json.
# Keep these models intact in release to avoid node panel data disappearing after obfuscation.
-keep class com.auto.master.Task.Operation.MetaOperation { *; }
-keep class com.auto.master.Task.Operation.** extends com.auto.master.Task.Operation.MetaOperation { *; }

# Gson TypeToken: R8 with proguard-android-optimize.txt can strip generic type signatures
# from anonymous TypeToken subclasses (e.g. new TypeToken<List<MetaOperation>>(){}.getType()).
# Without these rules, getType() returns the raw List type and Gson skips the custom
# MetaOperationDeserializer, causing fromJson() to return an empty list in release.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep the Gson type-adapter helper and its inner deserializer/serializer classes.
# R8 may otherwise inline or remove them, leaving Gson with no adapter for MetaOperation.
-keep class com.auto.master.Task.Operation.OperationGsonHelper { *; }
-keep class com.auto.master.Task.Operation.OperationGsonHelper$* { *; }
