package com.vegam.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsLogicTest {
    @Test fun `exact steps map to their own index`() {
        SPEED_STEPS.forEachIndexed { i, v -> assertEquals(i, stepIndexOf(v)) }
    }

    @Test fun `odd saved value snaps to nearest step`() {
        assertEquals(SPEED_STEPS.indexOf(5), stepIndexOf(6))
        assertEquals(SPEED_STEPS.indexOf(50), stepIndexOf(900))
        assertEquals(0, stepIndexOf(-3))
    }

    @Test fun `labels`() {
        assertEquals("Unlimited", speedLabel(0))
        assertEquals("10 MB/s", speedLabel(10))
    }
}
