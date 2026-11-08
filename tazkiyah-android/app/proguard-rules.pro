# R8 rules for the release build. AndroidX, Hilt, Room, Media3, Coil, OkHttp and
# kotlinx.serialization ship their own consumer rules; only NewPipeExtractor needs help.

# NewPipeExtractor runs YouTube's player JavaScript in Rhino and builds requests by reflection
# (protobuf-javalite, nanojson). Keep them whole: shrinking them breaks stream extraction silently.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Desktop-JVM APIs that Rhino and jsoup reference but never use on Android.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.tools.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# The YouTube IFrame player calls back into Kotlin from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
