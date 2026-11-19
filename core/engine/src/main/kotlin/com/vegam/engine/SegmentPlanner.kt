package com.vegam.engine

/** File ko segments mein todna aur work-stealing ka faisla. Pure logic, network nahi. */
object SegmentPlanner {
    const val SMALL_FILE_BYTES = 1L * 1024 * 1024      // <1MB => single connection
    const val MIN_SEGMENT_BYTES = 256L * 1024          // is se chhota segment faayde ka nahi
    const val MIN_STEAL_BYTES = 512L * 1024            // is se kam bacha ho to steal mat karo
    const val MAX_SEGMENTS = 16

    /**
     * File ko `requested` (1..16) segments mein baanto. Bache hue bytes pehle segments ko mil jaate hain,
     * taaki koi byte miss ya overlap na ho.
     */
    fun split(totalBytes: Long, requested: Int, acceptRanges: Boolean = true): List<Segment> {
        require(totalBytes > 0) { "totalBytes > 0 hona chahiye" }
        if (!acceptRanges || totalBytes < SMALL_FILE_BYTES) {
            return listOf(Segment(index = 0, start = 0, end = totalBytes - 1))
        }
        val wanted = requested.coerceIn(1, MAX_SEGMENTS)
        val count = minOf(wanted.toLong(), totalBytes / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1)
        val base = totalBytes / count
        val extra = totalBytes % count
        var cursor = 0L
        return List(count) { i ->
            val size = base + if (i < extra) 1 else 0
            Segment(i, cursor, cursor + size - 1).also { cursor += size }
        }
    }

    /** Steal ka result: victim ka naya (chhota) end, aur naya segment jo khali worker uthayega. */
    data class Steal(val victim: Segment, val thief: Segment)

    /**
     * Sabse zyada bache wale segment ka aadha hissa nikalo.
     * NOTE: victim ka in-flight request purane `end` tak chal raha hota hai; downloader ko
     * victim worker ko naye `end` par rokna padega (Phase 1B mein handle hoga).
     */
    fun steal(segments: List<Segment>, newIndex: Int): Steal? {
        val victim = segments.maxByOrNull { it.remaining } ?: return null
        if (victim.remaining < MIN_STEAL_BYTES) return null
        val splitAt = victim.nextByte + victim.remaining / 2
        return Steal(
            victim = victim.copy(end = splitAt - 1),
            thief = Segment(index = newIndex, start = splitAt, end = victim.end),
        )
    }
}
