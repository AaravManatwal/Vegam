package com.vegam.core.database

import com.vegam.engine.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MappersTest {
    private val entity = DownloadEntity(
        id = 7, url = "https://x/f.bin", fileName = "f.bin", targetPath = "/tmp/f.bin",
        totalBytes = 4_000, etag = "\"abc\"", lastModified = "Mon, 16 Nov 2026",
    )

    @Test
    fun `segment survives entity round trip`() {
        val seg = Segment(index = 3, start = 1_000, end = 1_999, downloaded = 250)
        assertEquals(seg, seg.toEntity(downloadId = 7).toSegment())
    }

    @Test
    fun `resume state carries validators and segments`() {
        val rows = listOf(
            Segment(0, 0, 1_999, 500).toEntity(7),
            Segment(1, 2_000, 3_999, 0).toEntity(7),
        )
        val state = assertNotNull(entity.resumeState(rows))
        assertEquals(2, state.segments.size)
        assertEquals("\"abc\"", state.validators.etag)
        assertEquals(4_000L, state.validators.contentLength)
        assertEquals(500L, state.segments[0].downloaded)
    }

    @Test
    fun `no segments or unknown size means fresh download`() {
        assertNull(entity.resumeState(emptyList()))
        assertNull(entity.copy(totalBytes = 0).resumeState(listOf(Segment(0, 0, 9).toEntity(7))))
    }
}
