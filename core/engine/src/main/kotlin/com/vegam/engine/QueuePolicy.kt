package com.vegam.engine

/** Line mein ek download ka wo hissa jo scheduling ke liye zaroori hai. */
data class QueueItem(
    val id: Long,
    val priority: Int,          // 0 low, 1 normal, 2 high
    val sortOrder: Long,        // drag & drop order
    val createdAt: Long,
    val scheduledAt: Long? = null,
)

/** "Kaun si downloads ab shuru ho" ka faisla. Pure logic: DB ya WorkManager ke bina test hota hai. */
object QueuePolicy {
    const val MIN_PARALLEL = 1
    const val MAX_PARALLEL = 10

    /**
     * `queued` mein se wo items jo abhi start honi chahiye.
     * Order: priority (high pehle) -> sortOrder (user ka drag order) -> createdAt.
     * Chalti hui downloads ko rokta nahi: maxParallel ghatane par naye slots hi kam hote hain.
     */
    fun pickNext(queued: List<QueueItem>, runningCount: Int, maxParallel: Int, now: Long): List<QueueItem> {
        val slots = maxParallel.coerceIn(MIN_PARALLEL, MAX_PARALLEL) - runningCount
        if (slots <= 0) return emptyList()
        return queued
            .filter { (it.scheduledAt ?: 0L) <= now }   // schedule ka time nahi aaya to skip
            .sortedWith(compareByDescending<QueueItem> { it.priority }.thenBy { it.sortOrder }.thenBy { it.createdAt })
            .take(slots)
    }

    /** Drag & drop: `movedId` ko `toIndex` par le jao. Return = naya id order (caller sortOrder = index likhega). */
    fun reorder(ids: List<Long>, movedId: Long, toIndex: Int): List<Long> {
        val list = ids.toMutableList()
        if (!list.remove(movedId)) return ids
        list.add(toIndex.coerceIn(0, list.size), movedId)
        return list
    }
}
