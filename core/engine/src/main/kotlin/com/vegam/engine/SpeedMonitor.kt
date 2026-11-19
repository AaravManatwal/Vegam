package com.vegam.engine

/**
 * Rolling-window speed meter + last-60-second graph data.
 * Time parameter se aata hai (nowMs), isliye tests mein fake clock chala sakte hain.
 */
class SpeedMonitor(
    private val windowMs: Long = 3_000L,
    private val historySeconds: Int = 60,
) {
    private val samples = ArrayDeque<Pair<Long, Long>>()   // (timeMs, bytes)
    private val buckets = ArrayDeque<Pair<Long, Long>>()   // (second, bytes) graph ke liye

    @Synchronized
    fun record(bytes: Long, nowMs: Long) {
        samples.addLast(nowMs to bytes)
        val sec = nowMs / 1000
        if (buckets.isNotEmpty() && buckets.last().first == sec) {
            buckets.addLast(sec to (buckets.removeLast().second + bytes))
        } else {
            buckets.addLast(sec to bytes)
        }
        trim(nowMs)
    }

    /** Pichhle `windowMs` ka average bytes/sec. Shuru ke 1s tak divisor 1s rakhte hain (spike se bachne ko). */
    @Synchronized
    fun bytesPerSec(nowMs: Long): Long {
        trim(nowMs)
        if (samples.isEmpty()) return 0L
        val span = (nowMs - samples.first().first).coerceIn(1_000L, windowMs)
        return samples.sumOf { it.second } * 1000 / span
    }

    /** Graph ke liye: last `historySeconds` ka har second ka throughput (purana -> naya). */
    @Synchronized
    fun series(nowMs: Long): LongArray {
        val nowSec = nowMs / 1000
        val out = LongArray(historySeconds)
        for ((sec, bytes) in buckets) {
            val age = (nowSec - sec).toInt()
            if (age in 0 until historySeconds) out[historySeconds - 1 - age] = bytes
        }
        return out
    }

    private fun trim(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().first >= windowMs) samples.removeFirst()
        val nowSec = nowMs / 1000
        while (buckets.isNotEmpty() && nowSec - buckets.first().first >= historySeconds) buckets.removeFirst()
    }

    companion object {
        /** ETA seconds; speed 0 ho to null (UI "--" dikhayega). */
        fun etaSeconds(remainingBytes: Long, bytesPerSec: Long): Long? =
            if (bytesPerSec <= 0L) null else (remainingBytes + bytesPerSec - 1) / bytesPerSec
    }
}
