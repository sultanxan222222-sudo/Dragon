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
        versionCode = 7
        versionName = "0.7-3d-only"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
