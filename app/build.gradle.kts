plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vegam.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vegam.app"
        minSdk = 26
        targetSdk = 35
        // CI release mein run number se badhta hai (Play Store har upload par bada versionCode maangta hai).
        versionCode = (System.getenv("VEGAM_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.0-rc1"
    }

    // Keystore sirf tab jab CI secrets de (VEGAM_KEYSTORE_PATH...). Nahi to release unsigned banta hai, build tootta nahi.
    val keystorePath = System.getenv("VEGAM_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("VEGAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VEGAM_KEY_ALIAS") ?: "vegam"
                keyPassword = System.getenv("VEGAM_KEY_PASSWORD") ?: System.getenv("VEGAM_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:database"))
    implementation(project(":core:download"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:add"))
    implementation(project(":feature:smart"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
