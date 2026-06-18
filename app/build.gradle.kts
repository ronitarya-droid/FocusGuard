plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.focus.guard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.focus.guard"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 minification + resource shrinking. CRITICAL for FocusGuard:
            // without this, the release APK is trivially reversible via
            // 'apktool d' — an attacker (or a determined user) can read every
            // blocked package name, every keyword, and every bypass-detection
            // rule in plaintext, then craft a targeted workaround.
            //
            // With R8 enabled, class/field/method names are obfuscated and
            // unused code is stripped. The proguard-rules.pro file keeps the
            // Android-entry-point classes intact (manifest-referenced
            // Activities, Services, Receivers, DeviceAdminReceiver subclasses).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}