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

# Tink / EncryptedSharedPreferences — R8 strips reflection targets without these
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# Network storage libraries
-keep class org.apache.commons.net.** { *; }
-keep class at.bitfire.dav4jvm.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn org.apache.commons.net.**
-dontwarn at.bitfire.dav4jvm.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# smbj — SMB2/3 client
-keep class com.hierynomus.** { *; }
-keep class com.rapid7.** { *; }
-dontwarn com.hierynomus.**
-dontwarn com.rapid7.**