package com.vegam.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentPlannerTest {
    @Test
    fun `split covers every byte exactly once`() {
        val total = 10_000_003L
        val segs = SegmentPlanner.split(total, requested = 8)
        assertEquals(8, segs.size)
        assertEquals(0L, segs.first().start)
        assertEquals(total - 1, segs.last().end)
        // Har segment pichhle ke turant baad shuru hota hai: na gap, na overlap.
        segs.zipWithNext().forEach { (a, b) -> assertEquals(a.end + 1, b.start) }
        assertEquals(total, segs.sumOf { it.remaining })
    }

    @Test
    fun `small file uses single connection`() {
        assertEquals(1, SegmentPlanner.split(500_000L, requested = 16).size)
    }

    @Test
    fun `no range support falls back to single connection`() {
        assertEquals(1, SegmentPlanner.split(50_000_000L, requested = 8, acceptRanges = false).size)
    }

    @Test
    fun `requested segments are clamped to 16`() {
        assertEquals(16, SegmentPlanner.split(500_000_000L, requested = 99).size)
    }

    @Test
    fun `steal takes second half of largest remaining`() {
        val seg = Segment(index = 0, start = 0, end = 4_194_303) // 4MB
        val steal = assertNotNull(SegmentPlanner.steal(listOf(seg), newIndex = 1))
        assertEquals(2_097_151L, steal.victim.end)
        assertEquals(2_097_152L, steal.thief.start)
        assertEquals(4_194_303L, steal.thief.end)
        assertEquals(seg.remaining, steal.victim.remaining + steal.thief.remaining)
    }

    @Test
    fun `steal refuses when too little remains`() {
        val seg = Segment(index = 0, start = 0, end = 400_000)
        assertNull(SegmentPlanner.steal(listOf(seg), newIndex = 1))
    }

    @Test
    fun `resume range header starts from written bytes`() {
        val seg = Segment(index = 2, start = 1_000, end = 1_999, downloaded = 250)
        assertEquals("bytes=1250-1999", seg.rangeHeader)
        assertEquals(750L, seg.remaining)
    }
}

class ResumeValidatorTest {
    private val base = Validators(etag = "\"abc\"", lastModified = "Mon, 16 Nov 2026", contentLength = 1000)

    @Test fun `same file can resume`() = assertTrue(ResumeValidator.canResume(base, base))
    @Test fun `changed etag restarts`() = assertFalse(ResumeValidator.canResume(base, base.copy(etag = "\"xyz\"")))
    @Test fun `changed length restarts`() = assertFalse(ResumeValidator.canResume(base, base.copy(contentLength = 999)))
    @Test fun `missing etag on server is tolerated`() = assertTrue(ResumeValidator.canResume(base, base.copy(etag = null)))
}

class RetryPolicyTest {
    @Test
    fun `backoff doubles then gives up after 5`() {
        val p = RetryPolicy()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), (0..4).map { p.delayMs(it) })
        assertNull(p.delayMs(5))
    }

    @Test
    fun `retryable statuses`() {
        assertTrue(RetryPolicy.isRetryableStatus(429))
        assertTrue(RetryPolicy.isRetryableStatus(503))
        assertFalse(RetryPolicy.isRetryableStatus(404))
        assertFalse(RetryPolicy.isRetryableStatus(403))
    }
}

class AdaptiveConcurrencyTest {
    @Test
    fun `throttle halves down to 1 and recovers by doubling`() {
        val c = AdaptiveConcurrency(max = 8, recoverAfterMs = 10_000)
        assertEquals(4, c.onThrottled(0))
        assertEquals(2, c.onThrottled(100))
        assertEquals(1, c.onThrottled(200))
        assertEquals(1, c.onThrottled(300)) // min se neeche nahi
        assertEquals(1, c.onHealthy(5_000))  // abhi jaldi hai
        assertEquals(2, c.onHealthy(10_300))
        assertEquals(4, c.onHealthy(20_400))
    }

    @Test
    fun `throughput drop detection`() {
        assertTrue(AdaptiveConcurrency.isThroughputDrop(300, 1_000))
        assertFalse(AdaptiveConcurrency.isThroughputDrop(900, 1_000))
    }
}

class SpeedMonitorTest {
    @Test
    fun `average over window and eta`() {
        val m = SpeedMonitor()
        m.record(1_000, 0); m.record(1_000, 500); m.record(1_000, 900)
        assertEquals(3_000L, m.bytesPerSec(1_000))
        assertEquals(4L, SpeedMonitor.etaSeconds(10_000, 3_000))
        assertNull(SpeedMonitor.etaSeconds(10_000, 0))
    }

    @Test
    fun `series places bytes in the right second`() {
        val m = SpeedMonitor()
        m.record(100, 5_000); m.record(50, 5_500)
        assertEquals(150L, m.series(7_000)[57]) // 2 second purana
    }
}

class TokenBucketTest {
    @Test
    fun `unlimited never waits`() = assertEquals(0L, TokenBucket(0).acquire(10_000_000, 0))

    @Test
    fun `limit forces wait after burst`() {
        val b = TokenBucket(bytesPerSec = 1_000)
        assertEquals(0L, b.acquire(1_000, 0))       // burst khatam
        assertEquals(500L, b.acquire(500, 0))       // 0.5s ruko
    }
}
