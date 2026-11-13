plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Stamped into BuildConfig so the installed app can identify itself on the Home screen
// (see BuildStamp.kt / AndroidBuildStampReader.kt). Both values come from the same inputs the
// signing pipeline uses, so the phone and the release page cannot disagree:
//   BUILT_UTC           — when this build was made, in UTC. CI passes -PbuiltUtc so the APK, the
//                         release title and the release body all carry the identical stamp; a local
//                         build without that property falls back to configuration time.
//   SIGNER_SHA256_PIN   — android/debug-signer.sha256, the certificate digest CI asserts against.
//                         Read defensively: a missing pin yields an empty string, which the stamp
//                         reports as "no pinned key" rather than failing the build.
val builtUtc: String = providers.gradleProperty("builtUtc").orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: java.time.ZonedDateTime
        .now(java.time.ZoneOffset.UTC)
        .format(java.time.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", java.util.Locale.US))
val pinnedSignerSha256: String = rootProject
    .file("debug-signer.sha256")
    .takeIf { it.isFile }
    ?.readText()
    ?.trim()
    .orEmpty()

android {
    namespace = "io.github.mohamtur1.fourcbon2"
    // Stable Compose/AndroidX dependencies require API 37 at compile time.
    // targetSdk remains 36 pending separate behavior-change testing.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mohamtur1.fourcbon2"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-dev"

        buildConfigField("String", "BUILT_UTC", "\"$builtUtc\"")
        buildConfigField("String", "SIGNER_SHA256_PIN", "\"$pinnedSignerSha256\"")
    }

    // Pin the debug identity to the keystore committed at android/debug.keystore.
    //
    // Without this, AGP resolves the debug key from ~/.android/debug.keystore on whichever machine
    // runs the build. A GitHub-hosted runner that does not already have that file ends up with a
    // different certificate, and an APK signed by a different certificate cannot be installed over
    // an existing one (Android reports "App not installed"). This was observed in CI: the committed
    // keystore was present and copied to ~/.android, yet the build still signed with a third key.
    //
    // Naming the store file explicitly removes every home-directory and cache dependency.
    // The passwords are the well-known public debug values, not secrets; see android/DEBUG_SIGNING.md.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storeType = "JKS"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Off by default since AGP 8; the on-device build stamp reads BuildConfig.BUILT_UTC and
        // BuildConfig.SIGNER_SHA256_PIN.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
