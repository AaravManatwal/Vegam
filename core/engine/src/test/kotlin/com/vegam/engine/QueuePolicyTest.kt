package com.vegam.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueuePolicyTest {
    private fun item(id: Long, pri: Int = 1, order: Long = id, created: Long = id, at: Long? = null) =
        QueueItem(id, pri, order, created, at)

    @Test
    fun `fills only free slots`() {
        val queued = (1L..5L).map { item(it) }
        assertEquals(listOf(1L, 2L), QueuePolicy.pickNext(queued, runningCount = 1, maxParallel = 3, now = 0).map { it.id })
        assertTrue(QueuePolicy.pickNext(queued, runningCount = 3, maxParallel = 3, now = 0).isEmpty())
    }

    @Test
    fun `high priority jumps the line then user order decides`() {
        val queued = listOf(item(1, pri = 1, order = 1), item(2, pri = 1, order = 0), item(3, pri = 2, order = 9), item(4, pri = 0, order = 0))
        assertEquals(listOf(3L, 2L, 1L, 4L), QueuePolicy.pickNext(queued, 0, 10, 0).map { it.id })
    }

    @Test
    fun `lowering max parallel never preempts, only shrinks new slots`() {
        val queued = listOf(item(1))
        assertTrue(QueuePolicy.pickNext(queued, runningCount = 4, maxParallel = 2, now = 0).isEmpty())
    }

    @Test
    fun `scheduled items wait for their time`() {
        val queued = listOf(item(1, at = 5_000), item(2))
        assertEquals(listOf(2L), QueuePolicy.pickNext(queued, 0, 3, now = 1_000).map { it.id })
        assertEquals(listOf(1L, 2L), QueuePolicy.pickNext(queued, 0, 3, now = 5_000).map { it.id })
    }

    @Test
    fun `max parallel is clamped to 1 to 10`() {
        val queued = (1L..20L).map { item(it) }
        assertEquals(10, QueuePolicy.pickNext(queued, 0, 99, 0).size)
        assertEquals(1, QueuePolicy.pickNext(queued, 0, 0, 0).size)
    }

    @Test
    fun `reorder moves item to target index`() {
        assertEquals(listOf(3L, 1L, 2L), QueuePolicy.reorder(listOf(1, 2, 3), movedId = 3, toIndex = 0))
        assertEquals(listOf(2L, 3L, 1L), QueuePolicy.reorder(listOf(1, 2, 3), movedId = 1, toIndex = 99))
        assertEquals(listOf(1L, 2L), QueuePolicy.reorder(listOf(1, 2), movedId = 42, toIndex = 0))
    }
}
