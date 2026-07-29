plugins {
    id("com.android.application")
}

android {
    namespace = "PACKAGE_PLACEHOLDER"   // Replaced at build time by generate-package.sh
    compileSdk = 36

    defaultConfig {
        applicationId = "PACKAGE_PLACEHOLDER"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Zero dependencies. The stub only needs INTERNET permission.
    // No AndroidX, no support libraries, no third-party code.
    // This keeps the APK tiny (~12KB) and statically clean.
}
