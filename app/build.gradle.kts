plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.infernadragon"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.infernadragon"
        minSdk = 26
        targetSdk = 28
        versionCode = 9
        versionName = "0.9-gpu-renderer"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}