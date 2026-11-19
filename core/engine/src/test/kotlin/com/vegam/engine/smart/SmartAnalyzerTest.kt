package com.vegam.engine.smart

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartAnalyzerTest {
    private fun analyzer() = SmartAnalyzer(LinkAnalyzer(maxRetries = 1, retryDelayMs = 1))
    private fun server(resp: () -> MockResponse) = MockWebServer().also { s ->
        s.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = resp()
        }
        s.start()
    }

    @Test fun `all toggles on gives every section`() = runBlocking {
        val s = server { MockResponse().setHeader("Content-Length", "1000").setHeader("Accept-Ranges", "bytes").setHeader("Content-Type", "application/zip") }
        val r = analyzer().run(s.url("/a.zip").toString(), emptyList())
        assertEquals(LinkState.VALID, r.report.state)
        assertNotNull(r.security); assertNotNull(r.duplicate)
        assertEquals(Compat.OK, r.compatibility.level)
        assertTrue(r.decision.canDownload)
        s.shutdown()
    }

    @Test fun `security off leaves it unchecked but other parts still run`() = runBlocking {
        val s = server { MockResponse().setHeader("Content-Length", "1000").setHeader("Accept-Ranges", "bytes") }
        val r = analyzer().run(s.url("/a.zip").toString(), emptyList(), SmartToggles(securityCheck = false))
        assertNull(r.security); assertNotNull(r.duplicate)
        assertEquals(1000L, r.report.sizeBytes)
        s.shutdown()
    }

    @Test fun `duplicate off skips only duplicates`() = runBlocking {
        val s = server { MockResponse().setHeader("Content-Length", "1000") }
        val r = analyzer().run(s.url("/a.zip").toString(), emptyList(), SmartToggles(duplicateDetection = false))
        assertNull(r.duplicate); assertNotNull(r.security)
        s.shutdown()
    }

    @Test fun `analysis off makes no network call and still decides`() = runBlocking {
        val s = server { error("network nahi chalna chahiye") }
        val r = analyzer().run(s.url("/a.zip").toString(), emptyList(), SmartToggles(linkAnalysis = false))
        assertEquals(LinkState.NOT_CHECKED, r.report.state)
        assertEquals(0, s.requestCount)
        assertTrue(r.decision.canDownload)
        s.shutdown()
    }

    @Test fun `validation off hides link verdict but keeps size`() = runBlocking {
        val s = server { MockResponse().setResponseCode(404) }
        val r = analyzer().run(s.url("/a.zip").toString(), emptyList(), SmartToggles(linkValidation = false))
        assertEquals(LinkState.NOT_CHECKED, r.report.state)
        assertTrue(r.decision.canDownload)
        s.shutdown()
    }

    @Test fun `known duplicate shows up and asks for extra confirmation`() = runBlocking {
        val s = server { MockResponse().setHeader("Content-Length", "1000").setHeader("Accept-Ranges", "bytes") }
        val url = s.url("/a.zip").toString()
        val known = listOf(KnownDownload(1, UrlTools.normalize(url), "a.zip", 1000, null, null, null, KnownState.COMPLETED))
        val r = analyzer().run(url, known)
        assertEquals(DuplicateKind.PREVIOUSLY_DOWNLOADED, r.duplicate?.kind)
        assertTrue(r.decision.extraConfirm.isNotEmpty())
        s.shutdown()
    }
}
