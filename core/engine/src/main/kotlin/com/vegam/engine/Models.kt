package com.vegam.engine

/**
 * Ek segment = file ka ek byte-range. `end` inclusive hai (HTTP Range header jaisa).
 * `downloaded` = is segment mein ab tak kitne bytes disk par likh chuke hain.
 * Phase 2 mein ye Room mein save hoga, resume ke liye.
 */
data class Segment(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long = 0L,
) {
    /** Agla byte jo hume server se maangna hai (resume point). */
    val nextByte: Long get() = start + downloaded
    val remaining: Long get() = (end - nextByte + 1).coerceAtLeast(0L)
    val isDone: Boolean get() = remaining == 0L
    val rangeHeader: String get() = "bytes=$nextByte-$end"
}

/** Server ke validators: resume se pehle check hote hain ki file badli to nahi. */
data class Validators(
    val etag: String?,
    val lastModified: String?,
    val contentLength: Long,
)
