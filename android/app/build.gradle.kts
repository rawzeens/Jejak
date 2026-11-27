plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.r4xn.jejak"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.r4xn.jejak"
        minSdk = 26          // Android 8.0
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    // OpenStreetMap maps (no Google account or map key needed).
    implementation(libs.osmdroid)

    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (Android's copy is stubbed there).
    testImplementation(libs.org.json)
}
