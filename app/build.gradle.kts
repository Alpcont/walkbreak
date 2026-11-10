plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alp.walkbreak"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alp.walkbreak"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Fixed key so every new APK installs as an update over the previous one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("walkbreak-debug.keystore")
            storePassword = "walkbreak"
            keyAlias = "walkbreak"
            keyPassword = "walkbreak"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// No extra libraries needed — the app uses only the Android platform APIs.
