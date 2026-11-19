// :core:ui = Vegam ka design system: theme, colors, shapes, reusable components (ring, ticker, tiles).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vegam.core.ui"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    // api: feature modules ko Compose alag se declare nahi karna padega, aur versions ek jagah se aate hain.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    api(libs.compose.icons.extended)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
}
