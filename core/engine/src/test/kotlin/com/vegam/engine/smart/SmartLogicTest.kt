package com.vegam.engine.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun rep(
    url: String,
    name: String? = null,
    size: Long? = 1000,
    mime: String? = null,
    state: LinkState = LinkState.VALID,
    final: String = url,
    hops: List<RedirectHop> = emptyList(),
    ranges: Boolean? = true,
    etag: String? = null,
    hash: String? = null,
    lastModified: String? = null,
    page: Boolean = false,
): LinkReport {
    val base = LinkReport.fromUrlOnly(url)
    val n = name ?: base.fileName
    val ext = FileTypes.extensionOf(n)
    return base.copy(
        finalUrl = final, fileName = n, extension = ext, category = FileTypes.categoryOf(ext, mime), mime = mime,
        state = state, stateDetail = "detail", sizeBytes = size, redirects = hops, acceptRanges = ranges,
        etag = etag, contentHash = hash, lastModified = lastModified, looksLikeWebPage = page,
    )
}

class UrlToolsTest {
    @Test fun `normalize drops fragment tracking params and default port`() {
        assertEquals(
            "https://example.com/a/file.zip?id=7",
            UrlTools.normalize("HTTPS://Example.COM:443/a/file.zip?utm_source=x&id=7&fbclid=abc#top"),
        )
    }

    @Test fun `same link in different spelling normalizes equal`() =
        assertEquals(UrlTools.normalize("http://A.com:80/x.zip#1"), UrlTools.normalize("http://a.com/x.zip"))

    @Test fun `garbage input does not crash`() = assertEquals("not a url", UrlTools.normalize("  not a url "))

    @Test fun `host of a link`() = assertEquals("cdn.example.com", UrlTools.host("https://cdn.example.com/f.bin"))

    @Test fun `signed expiry from Expires epoch`() =
        assertEquals(1_700_000_000_000L, UrlTools.signedExpiryMillis("https://x.com/f?Expires=1700000000&Signature=a"))

    @Test fun `signed expiry from amz date plus ttl`() =
        assertEquals(1_704_067_200_000L + 3_600_000L, UrlTools.signedExpiryMillis("https://x.com/f?X-Amz-Date=20240101T000000Z&X-Amz-Expires=3600"))

    @Test fun `no expiry in plain link`() = assertNull(UrlTools.signedExpiryMillis("https://x.com/f.zip?id=5"))

    @Test fun `implausible exp value is ignored`() = assertNull(UrlTools.signedExpiryMillis("https://x.com/f?exp=5"))
}

class FileTypesTest {
    @Test fun `content disposition quoted filename`() =
        assertEquals("report 1.pdf", FileTypes.parseContentDisposition("attachment; filename=\"report 1.pdf\""))

    @Test fun `content disposition rfc5987 wins over plain`() =
        assertEquals("résumé.pdf", FileTypes.parseContentDisposition("attachment; filename=\"fallback.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"))

    @Test fun `content disposition path traversal is stripped`() =
        assertEquals("evil.sh", FileTypes.parseContentDisposition("attachment; filename=\"../../etc/evil.sh\""))

    @Test fun `no disposition gives null and url name is used`() {
        assertNull(FileTypes.parseContentDisposition(null))
        assertEquals("movie.mp4", FileTypes.resolveName("https://x.com/dl/movie.mp4?token=1", null))
    }

    @Test fun `disposition name beats url name`() =
        assertEquals("real.zip", FileTypes.resolveName("https://x.com/get?id=3", "attachment; filename=real.zip"))

    @Test fun `category falls back to mime when extension unknown`() {
        assertEquals(FileCategory.VIDEO, FileTypes.categoryOf("xyz", "video/x-matroska"))
        assertEquals(FileCategory.OTHER, FileTypes.categoryOf(null, "application/octet-stream"))
        assertEquals(FileCategory.ARCHIVE, FileTypes.categoryOf("zip", null))
    }

    @Test fun `html without attachment is a web page`() {
        assertTrue(FileTypes.looksLikeWebPage("text/html; charset=utf-8", null))
        assertFalse(FileTypes.looksLikeWebPage("text/html", "attachment; filename=a.html"))
    }

    @Test fun `extension parsing`() {
        assertEquals("gz", FileTypes.extensionOf("a.tar.gz"))
        assertNull(FileTypes.extensionOf("README"))
        assertNull(FileTypes.extensionOf(".hidden"))
    }
}

class SecurityAssessorTest {
    @Test fun `plain https zip is green`() {
        val a = SecurityAssessor.assess(rep("https://example.com/files/data.zip", mime = "application/zip"))
        assertEquals(RiskLevel.GREEN, a.level)
    }

    @Test fun `http is yellow`() {
        val a = SecurityAssessor.assess(rep("http://example.com/data.zip"))
        assertEquals(RiskLevel.YELLOW, a.level)
        assertTrue(a.findings.any { it.code == "HTTP" })
    }

    @Test fun `apk is yellow and mentions signature limit honestly`() {
        val a = SecurityAssessor.assess(rep("https://example.com/app.apk"))
        assertEquals(RiskLevel.YELLOW, a.level)
        assertTrue(a.findings.any { it.code == "APK_SIG" && it.severity == Severity.INFO })
    }

    @Test fun `document name hiding an executable is red`() {
        val a = SecurityAssessor.assess(rep("https://example.com/invoice.pdf.exe"))
        assertEquals(RiskLevel.RED, a.level)
        assertNotNull(a.blockReason)
    }

    @Test fun `ordinary double extension like tar gz is not red`() =
        assertEquals(RiskLevel.GREEN, SecurityAssessor.assess(rep("https://example.com/src.tar.gz")).level)

    @Test fun `userinfo trick with executable is red but with plain zip only yellow`() {
        assertEquals(RiskLevel.RED, SecurityAssessor.assess(rep("https://paypal.com@evil.example/x.exe")).level)
        assertEquals(RiskLevel.YELLOW, SecurityAssessor.assess(rep("https://paypal.com@evil.example/x.zip")).level)
    }

    @Test fun `ip host and punycode are yellow`() {
        assertTrue(SecurityAssessor.assess(rep("http://192.168.1.5/a.zip")).findings.any { it.code == "IP_HOST" })
        assertTrue(SecurityAssessor.assess(rep("https://xn--pypal-4ve.com/a.zip")).findings.any { it.code == "IDN_HOST" })
    }

    @Test fun `brand lookalike flagged but real brand domain and cdn are not`() {
        assertTrue(SecurityAssessor.assess(rep("https://paypal-login.example.com/a.zip")).findings.any { it.code == "BRAND_LOOKALIKE" })
        assertFalse(SecurityAssessor.assess(rep("https://www.paypal.com/a.zip")).findings.any { it.code == "BRAND_LOOKALIKE" })
        assertFalse(SecurityAssessor.assess(rep("https://storage.googleapis.com/b/a.zip")).findings.any { it.code == "BRAND_LOOKALIKE" })
        assertFalse(SecurityAssessor.assess(rep("https://applecart.org/a.zip")).findings.any { it.code == "BRAND_LOOKALIKE" })
    }

    @Test fun `mime and extension disagree is yellow`() {
        val a = SecurityAssessor.assess(rep("https://example.com/clip.zip", mime = "video/mp4"))
        assertTrue(a.findings.any { it.code == "MIME_MISMATCH" })
        assertEquals(RiskLevel.YELLOW, a.level)
    }

    @Test fun `octet-stream does not count as mismatch`() =
        assertEquals(RiskLevel.GREEN, SecurityAssessor.assess(rep("https://example.com/a.zip", mime = "application/octet-stream")).level)

    @Test fun `html page where a file was expected is yellow`() {
        val a = SecurityAssessor.assess(rep("https://example.com/a.zip", mime = "text/html", page = true))
        assertTrue(a.findings.any { it.code == "HTML_FOR_FILE" })
    }

    @Test fun `https to http downgrade is flagged`() {
        val a = SecurityAssessor.assess(rep("https://a.com/x.zip", final = "http://b.com/x.zip", hops = listOf(RedirectHop("https://a.com/x.zip", 302))))
        assertTrue(a.findings.any { it.code == "DOWNGRADE" })
    }

    @Test fun `cross domain redirect is info for normal file`() {
        val a = SecurityAssessor.assess(rep("https://a.com/x.zip", final = "https://cdn.b.com/x.zip", hops = listOf(RedirectHop("https://a.com/x.zip", 302))))
        assertEquals(RiskLevel.GREEN, a.level)
        assertTrue(a.findings.any { it.code == "REDIRECT_HOST" && it.severity == Severity.INFO })
    }

    @Test fun `label never says safe`() {
        RiskLevel.entries.forEach { assertFalse(SecurityAssessor.label(it).contains("safe", ignoreCase = true) && !SecurityAssessor.label(it).contains("nahi")) }
    }
}

class DuplicateDetectorTest {
    private fun known(id: Long, url: String, name: String, size: Long?, state: KnownState = KnownState.COMPLETED, etag: String? = null, hash: String? = null, lm: String? = null, exists: Boolean = true) =
        KnownDownload(id, UrlTools.normalize(url), name, size, etag, lm, hash, state, exists)

    @Test fun `same name different size is not a duplicate`() {
        val r = DuplicateDetector.detect(rep("https://b.com/a.zip", size = 2000), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000)))
        assertEquals(DuplicateKind.NONE, r.kind)
        assertEquals(1, r.sameNameDifferentFiles.size)
        assertFalse(r.isDuplicateWarning)
    }

    @Test fun `same link while downloading`() {
        val r = DuplicateDetector.detect(rep("https://a.com/a.zip#x"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, KnownState.ACTIVE)))
        assertEquals(DuplicateKind.ALREADY_DOWNLOADING, r.kind)
    }

    @Test fun `same link already completed`() {
        val r = DuplicateDetector.detect(rep("https://a.com/a.zip"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000)))
        assertEquals(DuplicateKind.PREVIOUSLY_DOWNLOADED, r.kind)
        assertTrue(r.isDuplicateWarning)
    }

    @Test fun `completed record but file deleted is not a warning`() {
        val r = DuplicateDetector.detect(rep("https://a.com/a.zip"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, exists = false)))
        assertEquals(DuplicateKind.PREVIOUSLY_DOWNLOADED_FILE_MISSING, r.kind)
        assertFalse(r.isDuplicateWarning)
    }

    @Test fun `different link same size and etag is same file even with another name`() {
        val r = DuplicateDetector.detect(rep("https://mirror.com/b.zip", name = "b.zip", etag = "\"e1\""), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, etag = "\"e1\"")))
        assertEquals(DuplicateKind.SAME_FILE_OTHER_LINK, r.kind)
    }

    @Test fun `hash match beats everything and hash mismatch rules out`() {
        val same = DuplicateDetector.detect(rep("https://m.com/b.zip", hash = "AB12"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, hash = "ab12")))
        assertEquals(DuplicateKind.SAME_FILE_OTHER_LINK, same.kind)
        val diff = DuplicateDetector.detect(rep("https://m.com/a.zip", name = "a.zip", hash = "ff"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, hash = "00")))
        assertEquals(DuplicateKind.NONE, diff.kind)
    }

    @Test fun `same name and size without proof is only possible`() {
        val r = DuplicateDetector.detect(rep("https://b.com/a.zip", name = "a.zip"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000)))
        assertEquals(DuplicateKind.POSSIBLE_SAME_FILE, r.kind)
    }

    @Test fun `differing etags rule out possible duplicate`() {
        val r = DuplicateDetector.detect(rep("https://b.com/a.zip", name = "a.zip", etag = "\"x\""), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, etag = "\"y\"")))
        assertEquals(DuplicateKind.NONE, r.kind)
    }

    @Test fun `failed or cancelled records are ignored`() {
        val r = DuplicateDetector.detect(rep("https://a.com/a.zip"), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000, KnownState.ENDED)))
        assertEquals(DuplicateKind.NONE, r.kind)
    }

    @Test fun `unknown size never claims duplicate across links`() {
        val r = DuplicateDetector.detect(rep("https://b.com/a.zip", name = "a.zip", size = null), listOf(known(1, "https://a.com/a.zip", "a.zip", 1000)))
        assertEquals(DuplicateKind.NONE, r.kind)
    }
}

class SmartPolicyTest {
    private val none = DuplicateResult(DuplicateKind.NONE, null, "x")
    private fun decide(r: LinkReport, dup: DuplicateResult = none): Decision {
        val sec = SecurityAssessor.assess(r)
        return SmartPolicy.decide(sec, SmartPolicy.compatibility(r), r, dup)
    }

    @Test fun `green valid link downloads directly`() {
        val d = decide(rep("https://a.com/a.zip"))
        assertTrue(d.canDownload); assertTrue(d.extraConfirm.isEmpty())
    }

    @Test fun `yellow needs extra confirmation with reasons`() {
        val d = decide(rep("http://a.com/a.zip"))
        assertTrue(d.canDownload); assertTrue(d.extraConfirm.isNotEmpty())
    }

    @Test fun `red is blocked without a bypass`() {
        val d = decide(rep("https://a.com/doc.pdf.exe"))
        assertFalse(d.canDownload); assertNotNull(d.blockReason); assertTrue(d.extraConfirm.isEmpty())
    }

    @Test fun `duplicate asks for extra confirmation`() {
        val dup = DuplicateResult(DuplicateKind.PREVIOUSLY_DOWNLOADED, null, "pehle ho chuka")
        val d = decide(rep("https://a.com/a.zip"), dup)
        assertTrue(d.canDownload); assertTrue(d.extraConfirm.contains("pehle ho chuka"))
    }

    @Test fun `missing metadata after failed analysis does not block`() {
        val d = decide(rep("https://a.com/a.zip", state = LinkState.INCONCLUSIVE, size = null, ranges = null))
        assertTrue(d.canDownload)
    }

    @Test fun `server that reached us but gives no size is not possible for this engine`() {
        val c = SmartPolicy.compatibility(rep("https://a.com/a.zip", size = null))
        assertEquals(Compat.NOT_POSSIBLE, c.level)
    }

    @Test fun `no range support is limited not blocked`() {
        val c = SmartPolicy.compatibility(rep("https://a.com/a.zip", ranges = false))
        assertEquals(Compat.LIMITED, c.level)
    }

    @Test fun `tls error and auth and foreign scheme are not possible`() {
        assertEquals(Compat.NOT_POSSIBLE, SmartPolicy.compatibility(rep("https://a.com/a.zip", state = LinkState.TLS_ERROR)).level)
        assertEquals(Compat.NOT_POSSIBLE, SmartPolicy.compatibility(rep("https://a.com/a.zip", state = LinkState.AUTH_REQUIRED)).level)
        assertEquals(Compat.NOT_POSSIBLE, SmartPolicy.compatibility(LinkReport.fromUrlOnly("ftp://a.com/a.zip")).level)
    }

    @Test fun `expired or denied link needs extra confirmation but is not hard blocked`() {
        val d = decide(rep("https://a.com/a.zip", state = LinkState.ACCESS_DENIED))
        assertTrue(d.canDownload); assertTrue(d.extraConfirm.contains("detail"))
    }

    @Test fun `https alternative is only suggested for failed http links`() {
        assertNotNull(SmartPolicy.httpsAlternative(rep("http://a.com/a.zip", state = LinkState.NETWORK_ERROR)))
        assertNull(SmartPolicy.httpsAlternative(rep("http://a.com/a.zip", state = LinkState.VALID)))
        assertNull(SmartPolicy.httpsAlternative(rep("https://a.com/a.zip", state = LinkState.NETWORK_ERROR)))
        assertEquals("https://a.com/a.zip", SmartPolicy.httpsAlternative(rep("http://a.com/a.zip", state = LinkState.NETWORK_ERROR))!!.url)
    }
}
