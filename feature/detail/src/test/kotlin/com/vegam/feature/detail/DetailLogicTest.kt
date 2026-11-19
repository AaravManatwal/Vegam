package com.vegam.feature.detail

import com.vegam.engine.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailLogicTest {
    @Test fun `segment progress is downloaded over length`() {
        val ui = segmentUi(Segment(index = 2, start = 1_000, end = 1_999, downloaded = 250))
        assertEquals(0.25f, ui.progress)
        assertFalse(ui.done)
        assertEquals(2, ui.index)
    }

    @Test fun `finished segment is done`() {
        val ui = segmentUi(Segment(0, 0, 99, downloaded = 100))
        assertEquals(1f, ui.progress)
        assertTrue(ui.done)
    }

    @Test fun `stolen segment with shrunk end never exceeds 100 percent`() {
        // Work-steal ke baad end chhota ho sakta hai, downloaded purana: progress 1 par ruke.
        assertEquals(1f, segmentUi(Segment(0, 0, 49, downloaded = 80)).progress)
    }

    @Test fun `graph max snaps to the next round step`() {
        assertEquals(128L * 1024, niceMax(0))
        assertEquals(1024L * 1024, niceMax(900L * 1024))
        assertEquals(1024L * 1024, niceMax(1024L * 1024))
        assertEquals(4L * 1024 * 1024, niceMax(3L * 1024 * 1024))
    }

    @Test fun `graph max grows past the last step in whole steps`() {
        val last = 131072L * 1024
        assertEquals(2 * last, niceMax(last + 1))
    }
}
