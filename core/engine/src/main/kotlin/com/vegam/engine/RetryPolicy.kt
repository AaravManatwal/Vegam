package com.vegam.engine

/** Exponential backoff (1s, 2s, 4s, 8s, 16s), max 5 retries per segment. */
class RetryPolicy(
    val maxRetries: Int = 5,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
) {
    /** attempt 0 = pehla retry. null = ab aur retry nahi, task fail. */
    fun delayMs(attempt: Int): Long? {
        if (attempt >= maxRetries) return null
        return (baseDelayMs shl attempt).coerceAtMost(maxDelayMs)
    }

    companion object {
        /** 408/429/5xx retry karne layak; baaki 4xx (404, 403...) permanent hain. */
        fun isRetryableStatus(code: Int) = code == 408 || code == 429 || code in 500..599
    }
}
