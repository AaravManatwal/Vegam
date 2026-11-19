package com.vegam.feature.smart

import com.vegam.core.database.DetectionEntity
import com.vegam.core.database.DetectionRow
import com.vegam.engine.smart.LinkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartTextTest {
    private fun row(name: String, domain: String = "a.com", decision: String = "PENDING", sec: String = "GREEN",
                    dlStatus: String? = null, dlErr: String? = null, warn: String = "") =
        DetectionRow(
            DetectionEntity(id = name.hashCode().toLong(), url = "https://$domain/$name", normalizedUrl = "https://$domain/$name",
                domain = domain, fileName = name, fileType = "OTHER", linkState = "VALID", decision = decision,
                securityLevel = sec, warnings = warn),
            dlStatus, dlErr,
        )

    @Test fun `size text handles unknown`() {
        assertEquals("Size pata nahi", SmartText.sizeText(null))
        assertEquals("Size pata nahi", SmartText.sizeText(0))
    }

    @Test fun `expired wording is only for evidence state`() {
        assertTrue(SmartText.linkState(LinkState.ACCESS_DENIED).first.contains("access mana"))
        assertTrue(!SmartText.linkState(LinkState.ACCESS_DENIED).first.contains("expir", ignoreCase = true))
        assertEquals(Tone.BAD, SmartText.linkState(LinkState.EXPIRED_EVIDENCE).second)
    }

    @Test fun `inconclusive is a warning not an error`() = assertEquals(Tone.WARN, SmartText.linkState(LinkState.INCONCLUSIVE).second)

    @Test fun `history outcome uses the real download status`() {
        assertEquals("Download ho gaya", row("a", decision = "DOWNLOAD", dlStatus = "COMPLETED").outcome())
        assertTrue(row("a", decision = "DOWNLOAD", dlStatus = "FAILED", dlErr = "HTTP 500").outcome().contains("HTTP 500"))
        assertEquals("Block", row("a", decision = "BLOCKED").outcome())
        assertEquals("Cancel", row("a", decision = "CANCEL").outcome())
    }

    @Test fun `history search and filter`() {
        val rows = listOf(
            row("movie.mp4", "cdn.x.com", "DOWNLOAD"), row("app.apk", "evil.example", "BLOCKED", "RED"),
            row("doc.pdf", "b.org", "CANCEL"), row("c.zip", "c.org", "PENDING", "YELLOW"),
        )
        assertEquals(4, filterRows(rows, "", HistoryFilter.ALL).size)
        assertEquals(listOf("movie.mp4"), filterRows(rows, "", HistoryFilter.DOWNLOADED).map { it.d.fileName })
        assertEquals(listOf("app.apk"), filterRows(rows, "", HistoryFilter.BLOCKED).map { it.d.fileName })
        assertEquals(listOf("app.apk", "c.zip"), filterRows(rows, "", HistoryFilter.WARNINGS).map { it.d.fileName })
        assertEquals(listOf("doc.pdf"), filterRows(rows, "B.ORG", HistoryFilter.ALL).map { it.d.fileName })
    }
}
