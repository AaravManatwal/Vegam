package com.vegam.engine

import java.util.Locale

/** Notification aur UI ke liye human-readable text. Locale.US: decimal hamesha "." rahe. */
object Formatters {
    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024))
        b >= 1L shl 10 -> String.format(Locale.US, "%d KB", b / 1024)
        else -> "$b B"
    }

    fun speed(bytesPerSec: Long): String = bytes(bytesPerSec) + "/s"

    /** null (speed 0) ya negative => "--". */
    fun eta(seconds: Long?): String = when {
        seconds == null || seconds < 0 -> "--"
        seconds >= 3600 -> String.format(Locale.US, "%dh %02dm", seconds / 3600, seconds % 3600 / 60)
        seconds >= 60 -> String.format(Locale.US, "%dm %02ds", seconds / 60, seconds % 60)
        else -> "${seconds}s"
    }
}
