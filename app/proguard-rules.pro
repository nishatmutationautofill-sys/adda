# WebRTC (native থেকে JNI দিয়ে ক্লাস ডাকে)
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
-dontwarn org.webrtc.**

# Socket.IO / Engine.IO / OkHttp
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 (ExoPlayer / Transformer)
-dontwarn androidx.media3.**

# ZXing QR
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

# রানটাইম স্ট্যাক ট্রেস পড়ার জন্য
-keepattributes SourceFile,LineNumberTable

# Start.io SDK
-keep class com.startapp.** { *; }
-keep class com.truenet.** { *; }
-dontwarn com.startapp.**
-dontwarn com.truenet.**
-dontwarn org.jetbrains.annotations.**

# রিলিজ বিল্ডে R8 যেন "Missing class" সতর্কতায় ব্যর্থ না হয়
-ignorewarnings
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn kotlin.Unit
