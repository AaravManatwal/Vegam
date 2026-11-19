// :core:download = engine + DB ko WorkManager/foreground service se jodta hai.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vegam.core.download"
    compileSdk = 35

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:database"))   // database already engine ko api() karta hai
    api(libs.work.runtime.ktx)
    implementation(libs.core.ktx)
    implementation(libs.documentfile)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
}
