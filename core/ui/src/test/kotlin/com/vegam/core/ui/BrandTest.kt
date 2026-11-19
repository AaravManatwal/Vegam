package com.vegam.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandTest {
    @Test fun `logo has three lines that all converge on one apex`() {
        assertEquals(3, LOGO_LINES.size)
        LOGO_LINES.forEach {
            assertEquals(LOGO_APEX_X, it.x1)
            assertEquals(LOGO_APEX_Y, it.y1)
        }
    }

    @Test fun `logo lines stay inside the box with room for the stroke`() {
        LOGO_LINES.forEach { l ->
            listOf(l.x0, l.y0, l.x1, l.y1).forEach { assertTrue(it in 0.05f..0.95f, "coordinate $it too close to edge") }
        }
    }

    @Test fun `outer lines are the V arms and the middle one is shorter`() {
        val (left, right, mid) = LOGO_LINES
        assertTrue(left.x0 < LOGO_APEX_X && right.x0 > LOGO_APEX_X)
        assertTrue(mid.y0 > left.y0)   // beech wali line neeche se shuru: chhoti
    }

    @Test fun `check mark goes down then up`() {
        val (a, b, c) = CHECK_POINTS
        assertTrue(b.y > a.y && c.y < b.y)
        assertTrue(a.x < b.x && b.x < c.x)
    }
}
