package com.vegam.engine

/**
 * Throttle hone par connections 8 -> 4 -> 2 -> 1, network theek hone par wapas badhao (double).
 * Throttle signal: HTTP 429/503, ya throughput peak ke 40% se neeche.
 */
class AdaptiveConcurrency(
    val max: Int = 8,
    val min: Int = 1,
    private val recoverAfterMs: Long = 10_000L,
) {
    var current: Int = max
        private set
    private var lastChangeMs = Long.MIN_VALUE / 2

    fun onThrottled(nowMs: Long): Int {
        current = (current / 2).coerceAtLeast(min)
        lastChangeMs = nowMs
        return current
    }

    /** Healthy tick: `recoverAfterMs` tak koi change nahi hua to ek step upar. */
    fun onHealthy(nowMs: Long): Int {
        if (current < max && nowMs - lastChangeMs >= recoverAfterMs) {
            current = (current * 2).coerceAtMost(max)
            lastChangeMs = nowMs
        }
        return current
    }

    companion object {
        fun isThrottleStatus(code: Int) = code == 429 || code == 503
        fun isThroughputDrop(currentBps: Long, peakBps: Long) = peakBps > 0 && currentBps < peakBps * 0.4
    }
}
