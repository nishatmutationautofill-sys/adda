plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.adda.chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adda.chatkt"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
        // শুধু বাংলা ও ইংরেজি রিসোর্স রাখা (লাইব্রেরির বাকি ভাষা বাদ)
        resourceConfigurations += listOf("en", "bn")
        // x86/x86_64 বাদ দিয়ে শুধু দুই ধরনের মোবাইল CPU রাখা (ফাইল সাইজ কম রাখতে)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // একটাই APK ফাইল: arm64-v8a (নতুন ফোন) ও armeabi-v7a (পুরনো/সস্তা ফোন) — দুটোর native লাইব্রেরি একই ফাইলে থাকে।
    // ফোন নিজে থেকে নিজের CPU-র উপযোগী লাইব্রেরি বেছে নেয়, তাই দুই ধরনের মোবাইলে একই APK ইনস্টল হয়।
    splits {
        abi {
            isEnable = false
        }
    }

    // একটাই ফিক্সড ডিবাগ কী: প্রতি বিল্ডের APK একই সইয়ে হয়, তাই পুরনো অ্যাপের ওপর আপডেট ইনস্টল হয়
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // সরাসরি ইনস্টল করার জন্য ডিবাগ কী দিয়েই সই করা (Play Store এর জন্য নিজের কী লাগবে)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // native লাইব্রেরি APK-র ভেতরে কমপ্রেস থাকবে → ডাউনলোড/ফাইল সাইজ ছোট
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/kotlin/**"
            excludes += "kotlin-tooling-metadata.json"
            excludes += "DebugProbesKt.bin"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/proguard/**"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Start.io ads SDK (banner and other ad formats)
    implementation("com.startapp:inapp-sdk:5.+")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Media3: Transformer = হার্ডওয়্যার H.264 ভিডিও কমপ্রেস, ExoPlayer/UI = অ্যাপের ভেতরে ভিডিও ও ভয়েস প্লেয়ার
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}

// google-services.json must belong to applicationId com.adda.chatkt.
// If it is absent, the project can still compile, but FCM needs the correct file to work.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
