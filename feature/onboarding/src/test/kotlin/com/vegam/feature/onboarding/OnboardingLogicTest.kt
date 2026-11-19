package com.vegam.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingLogicTest {
    @Test fun `next label changes only on the last page`() {
        assertEquals("Next", nextLabel(0))
        assertEquals("Next", nextLabel(ONBOARDING_PAGES.size - 2))
        assertEquals("Get started", nextLabel(ONBOARDING_PAGES.size - 1))
    }

    @Test fun `skip hidden on last page`() {
        assertTrue(showSkip(0))
        assertFalse(showSkip(ONBOARDING_PAGES.size - 1))
    }

    @Test fun `every page has text`() {
        ONBOARDING_PAGES.forEach { assertTrue(it.title.isNotBlank() && it.body.isNotBlank()) }
    }
}
