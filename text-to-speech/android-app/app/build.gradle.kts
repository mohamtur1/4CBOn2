plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.mohamtur1.ttsqueueplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.mohamtur1.ttsqueueplayer"
        minSdk = 26          // Android 8.0+ (foreground services, notification channels)
        targetSdk = 34       // Android 14
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Unsigned/sideload build for now; add a signing config before Play Store.
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

dependencies {
    // Deliberately NO androidx / Compose / Media3: the app uses only the Android
    // framework (Activity, TextToSpeech, MediaSession, foreground Service).
    // That keeps the APK tiny and the build simple and robust.
    testImplementation("junit:junit:4.13.2")
}
