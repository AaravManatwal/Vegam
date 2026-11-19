package com.vegam.engine

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chhota fake server: HEAD + Range support, optional failures. Real network nahi chahiye. */
class RangeServer(
    val data: ByteArray,
    private val etag: String = "\"v1\"",
    private val acceptRanges: Boolean = true,
    failFirst: Int = 0,
    private val failCode: Int = 503,
    private val forceStatus: Int? = null,
    dropFirst: Int = 0,
) {
    val ranges = CopyOnWriteArrayList<String>()
    val gets = AtomicInteger(0)
    private val toFail = AtomicInteger(failFirst)
    /** Pehle N GET ke body ke beech mein connection kaat do (network gir gaya jaisa). */
    private val toDrop = AtomicInteger(dropFirst)
    val server = MockWebServer().also { s ->
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        s.start()
    }

    fun url(): String = server.url("/file.bin").toString()

    private fun handle(r: RecordedRequest): MockResponse {
        forceStatus?.let { return MockResponse().setResponseCode(it) }
        if (r.method == "HEAD") {
            return MockResponse().setHeader("Content-Length", data.size).setHeader("ETag", etag)
                .apply { if (acceptRanges) setHeader("Accept-Ranges", "bytes") }
        }
        gets.incrementAndGet()
        val range = r.getHeader("Range")
        if (range != null) ranges.add(range)
        if (toFail.getAndDecrement() > 0) return MockResponse().setResponseCode(failCode)
        val resp = if (range != null && acceptRanges) {
            val (a, b) = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range)!!.destructured
            val from = a.toInt()
            val to = minOf(b.toInt(), data.size - 1)
            MockResponse().setResponseCode(206).setHeader("ETag", etag)
                .setHeader("Content-Range", "bytes $from-$to/${data.size}")
                .setBody(Buffer().write(data, from, to - from + 1))
        } else {
            MockResponse().setResponseCode(200).setHeader("ETag", etag).setBody(Buffer().write(data))
        }
        if (toDrop.getAndDecrement() > 0) resp.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        return resp
    }
}

class SegmentedDownloaderTest {
    private val fastRetry = RetryPolicy(maxRetries = 5, baseDelayMs = 10, maxDelayMs = 100)
    private fun data(size: Int) = ByteArray(size) { ((it * 31 + it / 7) and 0xFF).toByte() }
    private fun tmp(): File = File.createTempFile("vegam", ".bin").also { it.deleteOnExit() }
    private fun downloader() = SegmentedDownloader(retry = fastRetry)

    @Test
    fun `multi segment download matches source bytes`() {
        val srv = RangeServer(data(5 * 1024 * 1024))
        val f = tmp()
        runBlocking { downloader().download(srv.url(), f, maxSegments = 8) }
        assertContentEquals(srv.data, f.readBytes())
        assertTrue(srv.gets.get() >= 2, "ek se zyada connection use hona chahiye")
        srv.server.shutdown()
    }

    @Test
    fun `small file uses a single request`() {
        val srv = RangeServer(data(500_000))
        val f = tmp()
        runBlocking { downloader().download(srv.url(), f, maxSegments = 16) }
        assertContentEquals(srv.data, f.readBytes())
        assertTrue(srv.gets.get() == 1)
        srv.server.shutdown()
    }

    @Test
    fun `server without range support falls back to single stream`() {
        val srv = RangeServer(data(3 * 1024 * 1024), acceptRanges = false)
        val f = tmp()
        runBlocking { downloader().download(srv.url(), f, maxSegments = 8) }
        assertContentEquals(srv.data, f.readBytes())
        assertTrue(srv.gets.get() == 1)
        srv.server.shutdown()
    }

    @Test
    fun `503 and 429 bursts are retried and download completes`() {
        for (code in listOf(503, 429)) {
            val srv = RangeServer(data(3 * 1024 * 1024), failFirst = 3, failCode = code)
            val f = tmp()
            runBlocking { downloader().download(srv.url(), f, maxSegments = 8) }
            assertContentEquals(srv.data, f.readBytes())
            srv.server.shutdown()
        }
    }

    @Test
    fun `resume continues from saved offset without re-downloading`() {
        val size = 4 * 1024 * 1024
        val srv = RangeServer(data(size))
        val segs = SegmentPlanner.split(size.toLong(), 2)
        val oneMb = 1024L * 1024
        // Pichhli run mein seg0 ke pehle 1MB likhe ja chuke the.
        val f = tmp()
        f.writeBytes(ByteArray(size).also { srv.data.copyInto(it, 0, 0, oneMb.toInt()) })
        val saved = ResumeState(
            listOf(segs[0].copy(downloaded = oneMb), segs[1]),
            Validators(etag = "\"v1\"", lastModified = null, contentLength = size.toLong()),
        )
        runBlocking { downloader().download(srv.url(), f, maxSegments = 2, resume = saved) }
        assertContentEquals(srv.data, f.readBytes())
        assertTrue(srv.ranges.none { it.startsWith("bytes=0-") }, "shuru se dobara download nahi hona chahiye")
        assertTrue(srv.ranges.contains("bytes=${oneMb}-${segs[0].end}"))
        srv.server.shutdown()
    }

    @Test
    fun `changed etag restarts from zero`() {
        val size = 4 * 1024 * 1024
        val srv = RangeServer(data(size))
        val segs = SegmentPlanner.split(size.toLong(), 2)
        val f = tmp()
        f.writeBytes(ByteArray(size) { 9 })   // purani (galat) file ka kachra
        val saved = ResumeState(
            listOf(segs[0].copy(downloaded = 1024L * 1024), segs[1]),
            Validators(etag = "\"old\"", lastModified = null, contentLength = size.toLong()),
        )
        runBlocking { downloader().download(srv.url(), f, maxSegments = 8, resume = saved) }
        assertContentEquals(srv.data, f.readBytes())
        assertTrue(srv.ranges.any { it.startsWith("bytes=0-") })
        srv.server.shutdown()
    }

    @Test
    fun `onStart reports validators before data flows`() {
        val srv = RangeServer(data(2 * 1024 * 1024))
        var seen: Validators? = null
        val listener = object : DownloadListener {
            override fun onStart(validators: Validators, totalBytes: Long) { seen = validators }
        }
        runBlocking { downloader().download(srv.url(), tmp(), listener = listener) }
        assertTrue(seen?.etag == "\"v1\"" && seen?.contentLength == 2L * 1024 * 1024)
        srv.server.shutdown()
    }

    @Test
    fun `404 fails immediately with DownloadException`() {
        val srv = RangeServer(data(1000), forceStatus = 404)
        assertFailsWith<DownloadException> {
            runBlocking { downloader().download(srv.url(), tmp()) }
        }
        srv.server.shutdown()
    }
}
