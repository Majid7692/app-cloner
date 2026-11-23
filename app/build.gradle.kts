plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.majidali.appcloner"
    compileSdk = 36

    defaultConfig {
        // Original application id. No other author's name anywhere.
        applicationId = "com.majidali.appcloner"
        minSdk = 28
        // Target API 36 (Android 16, "Baklava"). Play Store requires API 36
        // from February, and 16 KB page size support is required with it.
        // V1 ships no native (.so) libraries at all, so it is 16 KB
        // compatible by construction. See README "16 KB page size".
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep debug builds readable while developing.
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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX + Material 3. Dark mode comes from the DayNight theme
    // (see res/values/themes.xml), not from any extra library.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")

    // Deliberately no other dependencies: no analytics, no crash SDK,
    // no networking library. V1 makes zero network calls.
}
