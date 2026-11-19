package com.vegam.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LiveProgressTest {
    @Test fun `speed is stored immediately, history samples once per second`() {
        val lp = LiveProgress()
        lp.update(1, 100, nowMs = 0)
        lp.update(1, 200, nowMs = 500)     // 1s se kam: history mein nahi
        lp.update(1, 300, nowMs = 1_000)
        assertEquals(300L, lp.speeds.value[1])
        assertEquals(listOf(100L, 300L), lp.history.value[1])
    }

    @Test fun `history keeps only the last 60 samples`() {
        val lp = LiveProgress()
        repeat(80) { lp.update(1, it.toLong(), nowMs = it * 1_000L) }
        val h = lp.history.value.getValue(1)
        assertEquals(60, h.size)
        assertEquals(79L, h.last())
        assertEquals(20L, h.first())
    }

    @Test fun `clear drops live speed but keeps history, forget drops both`() {
        val lp = LiveProgress()
        lp.update(1, 100, nowMs = 0)
        lp.clear(1)
        assertFalse(lp.speeds.value.containsKey(1))
        assertEquals(listOf(100L), lp.history.value[1])
        lp.forget(1)
        assertFalse(lp.history.value.containsKey(1))
    }

    @Test fun `downloads are tracked separately`() {
        val lp = LiveProgress()
        lp.update(1, 10, nowMs = 0); lp.update(2, 20, nowMs = 0)
        assertEquals(listOf(10L), lp.history.value[1])
        assertEquals(listOf(20L), lp.history.value[2])
    }
}
