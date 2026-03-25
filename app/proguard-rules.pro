# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep attributes for annotations, signature (useful for Kotlin and Gson/Retrofit)
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# --- Javascript Interface ---
# Keep JavascriptInterface methods so WebView can interact with Android code
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Gson ---
# Gson specific classes
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
# Application model classes that will be serialized/deserialized by Gson
-keep class com.ganha.test.bean.** { *; }

# --- VasDolly (Channel Packaging) ---
-keep class com.tencent.vasdolly.** { *; }

# --- Firebase / Play Services ---
# Generally handled by Google's own consumer rules, but we explicitly keep messaging services if custom defined
-keep class com.ganha.test.noticemessage.MyFirebaseMessagingService { *; }
-keep class com.ganha.test.noticemessage.MyWorker { *; }

# --- XXPermissions ---
-keep class com.hjq.permissions.** { *; }
