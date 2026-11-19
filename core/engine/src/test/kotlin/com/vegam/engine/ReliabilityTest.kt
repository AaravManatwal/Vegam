package com.vegam.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phase 5: "real duniya" jaise haalat: connection beech mein tootna, kill ke baad resume,
 * retries ka khatam hona, speed limit, badi file. Sab fake server par, network ke bina.
 */
class ReliabilityTest {
    private val fastRetry = RetryPolicy(maxRetries = 5, baseDelayMs = 10, maxDelayMs = 100)
    private val mb = 1024 * 1024
    private fun data(size: Int) = ByteArray(size) { ((it * 31 + it / 7) and 0xFF).toByte() }
    private fun tmp(): File = File.createTempFile("vegam", ".bin").also { it.deleteOnExit() }
    private fun downloader() = SegmentedDownloader(retry = fastRetry)

    @Test
    fun `connection cut mid body is retried and bytes still match`() {
        val srv = RangeServer(data(6 * mb), dropFirst = 3)
        val f = tmp()
        runBlocking { downloader().download(srv.url(), f, maxSegments = 4) }
        assertContentEquals(srv.data, f.readBytes())
        srv.server.shutdown()
    }

    @Test
    fun `server that never recovers ends in DownloadException not a hang`() {
        val srv = RangeServer(data(4 * mb), failFirst = 10_000, failCode = 503)
        assertFailsWith<DownloadException> {
            runBlocking { withTimeout(30_000) { downloader().download(srv.url(), tmp(), maxSegments = 4) } }
        }
        srv.server.shutdown()
    }

    @Test
    fun `cancel mid download then resume gives identical file`() {
        val size = 8 * mb
        val srv = RangeServer(data(size))
        val f = tmp()
        var saved: List<Segment> = emptyList()
        runBlocking {
            val slow = SegmentedDownloader(retry = fastRetry, rateLimiter = TokenBucket(3L * mb))
            val job = launch(Dispatchers.IO) {
                slow.download(srv.url(), f, maxSegments = 4, listener = object : DownloadListener {
                    override fun onStart(validators: Validators, totalBytes: Long) { captured = validators }
                    override fun onSegments(segments: List<Segment>) { saved = segments }
                })
            }
            // "App kill": kuch data aane ke baad download cancel.
            withTimeout(20_000) { while (saved.sumOf { it.downloaded } < 2L * mb) delay(20) }
            job.cancelAndJoin()
        }
        val savedValidators = checkNotNull(captured) { "onStart nahi bula" }
        val partial = saved.sumOf { it.downloaded }
        assertTrue(partial in 1 until size.toLong(), "cancel ke waqt adhoori progress honi chahiye, mili $partial")

        // "App dobara khuli": saved state se resume.
        runBlocking { downloader().download(srv.url(), f, maxSegments = 4, resume = ResumeState(saved, savedValidators)) }
        assertContentEquals(srv.data, f.readBytes())
        srv.server.shutdown()
    }

    @Test
    fun `speed limit really slows the download`() {
        val srv = RangeServer(data(6 * mb))
        val limited = SegmentedDownloader(retry = fastRetry, rateLimiter = TokenBucket(2L * mb))
        val t0 = System.nanoTime()
        val f = tmp()
        runBlocking { limited.download(srv.url(), f, maxSegments = 4) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertContentEquals(srv.data, f.readBytes())
        // 6MB @ 2MB/s, 1s burst: kam se kam ~1.5 second lagna chahiye (bina limit ke ye 100ms se kam hota).
        assertTrue(ms >= 1_500, "limit lagu nahi hui: $ms ms")
        srv.server.shutdown()
    }

    @Test
    fun `big file with 16 segments is byte exact`() {
        val srv = RangeServer(data(24 * mb))
        val f = tmp()
        val out = runBlocking { downloader().download(srv.url(), f, maxSegments = 16) }
        assertContentEquals(srv.data, f.readBytes())
        assertTrue(out.segmentsUsed >= 1)
        srv.server.shutdown()
    }

    @Volatile private var captured: Validators? = null
}
