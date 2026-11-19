package com.vegam.engine.smart

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** URL ke chhote helper: pehchaan (normalize), host, aur URL ke andar likhi expiry. Sab offline. */
object UrlTools {
    private val TRACKING = Regex("^(utm_.*|fbclid|gclid|igshid|mc_eid)$", RegexOption.IGNORE_CASE)
    private val AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /**
     * Duplicate pehchanne ke liye same link ka ek hi roop: scheme/host chhote akshar, default port hata, #fragment hata,
     * tracking params (utm_*, fbclid, gclid...) hata. Baaki params waise hi (wo file chun sakte hain).
     * Samajh na aaye to trim kiya hua original lautata hai.
     */
    fun normalize(url: String): String {
        val h = url.trim().toHttpUrlOrNull() ?: return url.trim()
        val b = h.newBuilder().fragment(null)
        h.queryParameterNames.filter { TRACKING.matches(it) }.forEach { b.removeAllQueryParameters(it) }
        return b.build().toString()
    }

    fun host(url: String): String? = url.trim().toHttpUrlOrNull()?.host

    /**
     * Signed links (S3, CloudFront, Google Storage) apni expiry URL mein hi likhte hain. Mile to epoch millis,
     * nahi to null. Sirf wahi jo URL mein saaf likha ho: andaza nahi lagate.
     */
    fun signedExpiryMillis(url: String): Long? {
        val h = url.trim().toHttpUrlOrNull() ?: return null
        fun q(name: String): String? = h.queryParameterNames.firstOrNull { it.equals(name, true) }?.let { h.queryParameter(it) }
        for (key in listOf("Expires", "exp")) {
            val v = q(key)?.toLongOrNull() ?: continue
            if (v in 1_000_000_000L..4_102_444_800L) return v * 1000
        }
        for ((dateKey, ttlKey) in listOf("X-Amz-Date" to "X-Amz-Expires", "X-Goog-Date" to "X-Goog-Expires")) {
            val d = q(dateKey) ?: continue
            val ttl = q(ttlKey)?.toLongOrNull() ?: continue
            val start = runCatching { LocalDateTime.parse(d, AMZ_DATE).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() ?: continue
            return start + ttl * 1000
        }
        return null
    }
}
