package com.vegam.engine

/**
 * Global speed limit ke liye token bucket. `bytesPerSec <= 0` matlab unlimited.
 * `acquire` batata hai kitni der ruknaa hai; caller coroutine mein delay() karega (blocking nahi).
 */
class TokenBucket(
    private var bytesPerSec: Long,
    private val burstSeconds: Double = 1.0,
) {
    private var tokens = bytesPerSec * burstSeconds
    private var lastNanos = 0L
    private var started = false

    @Synchronized
    fun setRate(newRate: Long) {
        bytesPerSec = newRate
        tokens = tokens.coerceAtMost(newRate * burstSeconds)
    }

    /** `bytes` kharch karo; return = kitne milliseconds ruknaa hai (0 = turant aage badho). */
    @Synchronized
    fun acquire(bytes: Long, nowNanos: Long): Long {
        if (bytesPerSec <= 0L) return 0L
        if (!started) { started = true; lastNanos = nowNanos }
        val capacity = bytesPerSec * burstSeconds
        tokens = (tokens + (nowNanos - lastNanos) / 1e9 * bytesPerSec).coerceAtMost(capacity)
        lastNanos = nowNanos
        tokens -= bytes   // negative = udhaar, wait se chukta hai
        return if (tokens >= 0) 0L else Math.ceil(-tokens / bytesPerSec * 1000).toLong()
    }
}
