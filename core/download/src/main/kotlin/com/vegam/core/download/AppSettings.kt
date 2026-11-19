package com.vegam.core.download

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Poori app ki settings ek jagah. Ye pure data hai: DataStore ya UI ke bina test ho sakta hai. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val maxParallel: Int = 3,
    val defaultSegments: Int = 8,
    /** MB/s mein; 0 = unlimited. */
    val speedLimitMbps: Int = 0,
    val wifiOnlyDefault: Boolean = false,
    val chargingOnly: Boolean = false,
    /** Clipboard mein file ka link ho to app khulte hi "Download?" poochho. */
    val clipboardWatch: Boolean = true,
    val onboardingDone: Boolean = false,
    // ---- Smart Detection (sab default ON; sab local, kuch bahar nahi jaata) ----
    /** Link milte hi size/type/server jaanch (HEAD / chhota Range GET). Band = Smart dialog sirf naam dikhata hai. */
    val smartAnalysis: Boolean = true,
    val duplicateDetection: Boolean = true,
    val linkValidation: Boolean = true,
    val securityCheck: Boolean = true,
    /** Vegam background mein ho aur jaanch poori ho jaye to notification. */
    val smartNotifications: Boolean = true,
    /** Phone par hi detection history (URL, naam, faisla). Band = kuch save nahi hota. */
    val detectionHistory: Boolean = true,
    /** ON = app background mein jaye to bhi jaanch poori hoti hai. OFF = background mein jaate hi ruk jaati hai. */
    val backgroundAnalysis: Boolean = true,
) {
    /** Kharab ya purani value (DataStore mein) kabhi engine tak na pahunche. */
    fun sanitized() = copy(
        maxParallel = maxParallel.coerceIn(1, 10),
        defaultSegments = defaultSegments.coerceIn(1, 16),
        speedLimitMbps = speedLimitMbps.coerceIn(0, 1000),
    )

    val speedLimitBytesPerSec: Long get() = speedLimitMbps.toLong() * 1024 * 1024
}
