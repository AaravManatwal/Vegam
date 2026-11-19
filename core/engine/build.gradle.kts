// :core:engine = VegamEngine. Pure Kotlin/JVM module (Android dependency nahi),
// taaki unit tests seedha JVM par chalein, emulator ki zaroorat nahi.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.okhttp)
    api(libs.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    useJUnitPlatform()
}
