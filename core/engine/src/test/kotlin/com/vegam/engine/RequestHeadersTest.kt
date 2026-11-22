package com.vegam.engine

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestHeadersTest {
    @Test fun `encode decode roundtrip keeps only allowed headers`() {
        val enc = RequestHeaders.encode(mapOf("User-Agent" to "UA/1", "Cookie" to "a=b; c=d", "Range" to "bytes=0-1", "Host" to "evil"))!!
        val dec = RequestHeaders.decode(enc)
        assertEquals(mapOf("User-Agent" to "UA/1", "Cookie" to "a=b; c=d"), dec)
    }

    @Test fun `newlines in values are refused (header injection)`() {
        assertNull(RequestHeaders.encode(mapOf("Cookie" to "a=b\r\nX-Evil: 1")))
    }

    @Test fun `decode of null or junk is empty`() {
        assertTrue(RequestHeaders.decode(null).isEmpty())
        assertTrue(RequestHeaders.decode("garbage\n: x").isEmpty())
    }

    @Test fun `downloader sends captured headers on probe and download`() = runBlocking {
        val body = ByteArray(2000) { 7 }
        val s = MockWebServer().apply {
            dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                    MockResponse().setHeader("Content-Length", body.size).apply { if (request.method != "HEAD") setBody(okio.Buffer().write(body)) }
            }
            start()
        }
        val f = File.createTempFile("vegam-hdr", ".bin").also { it.deleteOnExit() }
        SegmentedDownloader().download(
            s.url("/f").toString(), f, 2, null, DownloadListener.NONE,
            mapOf("User-Agent" to "CapturedUA", "Referer" to "https://page.example/x", "Cookie" to "sid=1"),
        )
        val reqs = generateSequence { s.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }.toList()
        assertTrue(reqs.isNotEmpty())
        assertTrue(reqs.all { it.getHeader("User-Agent") == "CapturedUA" && it.getHeader("Cookie") == "sid=1" && it.getHeader("Referer") == "https://page.example/x" })
        assertEquals(body.size.toLong(), f.length())
        s.shutdown()
    }

    @Test fun `cookie is not sent to another host after redirect`() = runBlocking {
        val other = MockWebServer().apply { enqueue(MockResponse().setHeader("Content-Length", "3").setBody("abc")); enqueue(MockResponse().setHeader("Content-Length", "3").setBody("abc")); start() }
        val first = MockWebServer().apply {
            dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) =
                    MockResponse().setResponseCode(302).setHeader("Location", other.url("/real").toString().replace("localhost", "127.0.0.1"))
            }
            start()
        }
        // first = localhost, other redirect target = 127.0.0.1 (alag host naam)
        val client = RequestHeaders.apply(VegamHttp.newClient(), first.url("/f").toString(), mapOf("Cookie" to "secret=1", "User-Agent" to "UA"))
        client.newCall(okhttp3.Request.Builder().url(first.url("/f")).build()).execute().close()
        val hop = other.takeRequest(500, java.util.concurrent.TimeUnit.MILLISECONDS)!!
        assertNull(hop.getHeader("Cookie"))
        assertEquals("UA", hop.getHeader("User-Agent"))
        first.shutdown(); other.shutdown()
    }
}
