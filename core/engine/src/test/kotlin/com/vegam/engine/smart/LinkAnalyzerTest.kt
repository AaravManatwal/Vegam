package com.vegam.engine.smart

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkAnalyzerTest {
    /** Fake server: har test apna `handler` deta hai. Real internet nahi chahiye. */
    private class Srv(handler: (RecordedRequest) -> MockResponse) {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val server = MockWebServer().also { s ->
            s.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse { requests += request; return handler(request) }
            }
            s.start()
        }
        fun url(path: String = "/file.zip") = server.url(path).toString()
        fun stop() = server.shutdown()
    }

    private fun analyzer() = LinkAnalyzer(maxRetries = 2, retryDelayMs = 1)

    @Test fun `head gives size ranges validators and header filename`() = runBlocking {
        val s = Srv { r ->
            MockResponse().setHeader("Content-Length", "12345").setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"e1\"")
                .setHeader("Content-Type", "application/zip").setHeader("Content-Disposition", "attachment; filename=\"real name.zip\"")
                .setHeader("Server", "unit")
        }
        val r = analyzer().analyze(s.url("/get?id=1"))
        assertEquals(LinkState.VALID, r.state)
        assertEquals(12345L, r.sizeBytes)
        assertEquals(true, r.acceptRanges)
        assertEquals("\"e1\"", r.etag)
        assertEquals("real name.zip", r.fileName)
        assertEquals("application/zip", r.mime)
        assertEquals("HEAD", r.methodUsed)
        assertEquals(FileCategory.ARCHIVE, r.category)
        assertEquals(1, r.attempts)
        assertTrue(s.requests.all { it.method == "HEAD" }, "body wali GET nahi honi chahiye")
        s.stop()
    }

    @Test fun `server that rejects HEAD falls back to range GET`() = runBlocking {
        val s = Srv { r ->
            if (r.method == "HEAD") MockResponse().setResponseCode(405)
            else MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/5000").setHeader("Content-Type", "video/mp4").setBody("x")
        }
        val r = analyzer().analyze(s.url("/clip.mp4"))
        assertEquals(LinkState.VALID, r.state)
        assertEquals(5000L, r.sizeBytes)
        assertEquals(true, r.acceptRanges)
        assertEquals("GET-RANGE", r.methodUsed)
        assertEquals("bytes=0-0", s.requests.last { it.method == "GET" }.getHeader("Range"))
        assertTrue(r.notes.isNotEmpty())
        s.stop()
    }

    @Test fun `presigned style link that is 403 on HEAD but fine on GET is valid`() = runBlocking {
        val s = Srv { r ->
            if (r.method == "HEAD") MockResponse().setResponseCode(403)
            else MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-0/77").setBody("x")
        }
        val r = analyzer().analyze(s.url("/obj.bin"))
        assertEquals(LinkState.VALID, r.state)
        assertEquals(77L, r.sizeBytes)
        s.stop()
    }

    @Test fun `range ignored by server is reported as no range support`() = runBlocking {
        val s = Srv { r ->
            if (r.method == "HEAD") MockResponse().setResponseCode(405)
            else MockResponse().setResponseCode(200).setHeader("Content-Length", "2")
                .setBody("ab")
        }
        val r = analyzer().analyze(s.url("/a.bin"))
        assertEquals(LinkState.VALID, r.state)
        assertEquals(false, r.acceptRanges)
        assertEquals(2L, r.sizeBytes)
        s.stop()
    }

    @Test fun `404 is invalid with the real status`() = runBlocking {
        val s = Srv { MockResponse().setResponseCode(404) }
        val r = analyzer().analyze(s.url("/gone.zip"))
        assertEquals(LinkState.INVALID, r.state)
        assertEquals(404, r.httpStatus)
        s.stop()
    }

    @Test fun `403 without expiry evidence is access denied not expired`() = runBlocking {
        val s = Srv { MockResponse().setResponseCode(403) }
        val r = analyzer().analyze(s.url("/secret.zip"))
        assertEquals(LinkState.ACCESS_DENIED, r.state)
        s.stop()
    }

    @Test fun `403 with past signed expiry is reported as expired with evidence`() = runBlocking {
        val s = Srv { MockResponse().setResponseCode(403) }
        val r = analyzer().analyze(s.url("/f.zip?Expires=1000000001&Signature=x"))
        assertEquals(LinkState.EXPIRED_EVIDENCE, r.state)
        assertTrue(r.stateDetail.contains("expiry"))
        s.stop()
    }

    @Test fun `403 with future expiry is not called expired`() = runBlocking {
        val s = Srv { MockResponse().setResponseCode(403) }
        val future = (System.currentTimeMillis() / 1000) + 3600
        val r = analyzer().analyze(s.url("/f.zip?Expires=$future"))
        assertEquals(LinkState.ACCESS_DENIED, r.state)
        s.stop()
    }

    @Test fun `401 needs login`() = runBlocking {
        val s = Srv { MockResponse().setResponseCode(401) }
        assertEquals(LinkState.AUTH_REQUIRED, analyzer().analyze(s.url()).state)
        s.stop()
    }

    @Test fun `temporary 503 is retried then succeeds`() = runBlocking {
        var n = 0
        val s = Srv { MockResponse().apply { if (n++ < 2) setResponseCode(503) else setHeader("Content-Length", "10").setHeader("Accept-Ranges", "bytes") } }
        val r = analyzer().analyze(s.url())
        assertEquals(LinkState.VALID, r.state)
        assertEquals(3, r.attempts)
        s.stop()
    }

    @Test fun `server that stays 503 stops after limited retries`() = runBlocking {
        val s = Srv { MockResponse().setResponseCode(503) }
        val r = analyzer().analyze(s.url())
        assertEquals(LinkState.TEMP_UNAVAILABLE, r.state)
        assertEquals(3, s.requests.size, "1 + 2 retry, us se zyada nahi")
        s.stop()
    }

    @Test fun `redirect chain is recorded and final url used`() = runBlocking {
        lateinit var base: String
        val s = Srv { r ->
            when (r.path) {
                "/a" -> MockResponse().setResponseCode(302).setHeader("Location", "$base/b")
                "/b" -> MockResponse().setResponseCode(301).setHeader("Location", "/c/final.zip")
                else -> MockResponse().setHeader("Content-Length", "99").setHeader("Accept-Ranges", "bytes")
            }
        }
        base = s.server.url("").toString().trimEnd('/')
        val r = analyzer().analyze("$base/a")
        assertEquals(LinkState.VALID, r.state)
        assertEquals(2, r.redirects.size)
        assertTrue(r.finalUrl.endsWith("/c/final.zip"))
        assertEquals("final.zip", r.fileName)   // naam final link se, "/a" se nahi
        assertEquals(99L, r.sizeBytes)
        s.stop()
    }

    @Test fun `redirect loop ends instead of spinning`() = runBlocking {
        lateinit var base: String
        val s = Srv { MockResponse().setResponseCode(302).setHeader("Location", "$base/loop") }
        base = s.server.url("").toString().trimEnd('/')
        val r = analyzer().analyze("$base/loop")
        assertEquals(LinkState.INVALID, r.state)
        assertTrue(r.stateDetail.contains("redirect"))
        s.stop()
    }

    @Test fun `foreign scheme and garbage are rejected without touching the network`() = runBlocking {
        assertEquals(LinkState.UNSUPPORTED, analyzer().analyze("ftp://example.com/a.zip").state)
        assertEquals(LinkState.UNSUPPORTED, analyzer().analyze("magnet:?xt=urn:btih:abc").state)
        assertEquals(LinkState.INVALID, analyzer().analyze("http://").state)
    }

    @Test fun `server digest header is exposed as hash`() = runBlocking {
        val s = Srv { MockResponse().setHeader("Content-Length", "5").setHeader("Digest", "sha-256=AbCdEf==") }
        val r = analyzer().analyze(s.url())
        assertEquals("sha-256:AbCdEf==", r.contentHash)
        s.stop()
    }

    @Test fun `no digest means no hash`() = runBlocking {
        val s = Srv { MockResponse().setHeader("Content-Length", "5") }
        assertNull(analyzer().analyze(s.url()).contentHash)
        s.stop()
    }

    @Test fun `html page for a file link is flagged`() = runBlocking {
        val s = Srv { MockResponse().setHeader("Content-Length", "500").setHeader("Content-Type", "text/html; charset=utf-8") }
        val r = analyzer().analyze(s.url("/download.zip"))
        assertTrue(r.looksLikeWebPage)
        s.stop()
    }

    @Test fun `steps are reported in order`() = runBlocking {
        val s = Srv { MockResponse().setHeader("Content-Length", "5") }
        val steps = mutableListOf<AnalysisStep>()
        analyzer().analyze(s.url()) { steps += it }
        assertEquals(AnalysisStep.PARSING, steps.first())
        assertEquals(AnalysisStep.DONE, steps.last())
        assertTrue(steps.contains(AnalysisStep.CONTACTING))
        s.stop()
    }

    @Test fun `cancelling the coroutine cancels a hanging request quickly`() = runBlocking {
        val s = Srv { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val job = launch { analyzer().analyze(s.url()) }
        delay(300)
        withTimeout(3_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        s.stop()
    }
}
