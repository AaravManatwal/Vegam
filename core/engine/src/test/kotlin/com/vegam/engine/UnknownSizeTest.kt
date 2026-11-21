package com.vegam.engine

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Server size (Content-Length) nahi batata (chunked): Vegam ab 1 connection se download karta hai. */
class UnknownSizeTest {
    private fun chunked(body: ByteArray, type: String = "application/octet-stream") =
        MockResponse().setChunkedBody(Buffer().write(body), 1024).setHeader("Content-Type", type)

    private fun tmp() = File.createTempFile("vegam-unk", ".bin").also { it.deleteOnExit() }

    @Test fun `chunked response without length downloads fully`() = runBlocking {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val s = MockWebServer().apply { dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = chunked(data)
        }; start() }
        val f = tmp()
        var finalTotal = -1L
        val out = SegmentedDownloader().download(s.url("/f").toString(), f, 8, null, object : DownloadListener {
            override fun onStart(validators: Validators, totalBytes: Long) { finalTotal = totalBytes }
        })
        assertEquals(data.size.toLong(), out.totalBytes)
        assertEquals(data.size.toLong(), finalTotal)          // aakhri onStart mein asli size
        assertTrue(data.contentEquals(f.readBytes()))
        s.shutdown()
    }

    @Test fun `probe reports unknown size`() = runBlocking {
        val s = MockWebServer().apply { dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = chunked(ByteArray(10))
        }; start() }
        val info = SegmentedDownloader().probe(s.url("/f").toString())
        assertFalse(info.sizeKnown)
        assertFalse(info.acceptRanges)
        s.shutdown()
    }

    @Test fun `html page is refused instead of saved as a file`() = runBlocking {
        val s = MockWebServer().apply { dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = chunked("<html>hi</html>".toByteArray(), "text/html; charset=utf-8")
        }; start() }
        val ex = assertFailsWith<DownloadException> { SegmentedDownloader().download(s.url("/f").toString(), tmp()) }
        assertTrue(ex.message!!.contains("web page"))
        s.shutdown()
    }

    @Test fun `file info flags web pages`() {
        assertTrue(FileInfo(-1, false, null, null, "text/html; charset=UTF-8").looksLikeWebPage)
        assertFalse(FileInfo(5, true, null, null, "application/zip").looksLikeWebPage)
    }
}
