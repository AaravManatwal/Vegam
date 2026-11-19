package com.vegam.core.download

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test fun `out of range values are clamped`() {
        val s = AppSettings(maxParallel = 99, defaultSegments = 0, speedLimitMbps = -5).sanitized()
        assertEquals(10, s.maxParallel)
        assertEquals(1, s.defaultSegments)
        assertEquals(0, s.speedLimitMbps)
    }

    @Test fun `valid values are untouched`() {
        val s = AppSettings(maxParallel = 4, defaultSegments = 12, speedLimitMbps = 5)
        assertEquals(s, s.sanitized())
    }

    @Test fun `clipboard watch is on by default`() = assertEquals(true, AppSettings().clipboardWatch)

    @Test fun `speed limit converts MB to bytes and zero stays unlimited`() {
        assertEquals(5L * 1024 * 1024, AppSettings(speedLimitMbps = 5).speedLimitBytesPerSec)
        assertEquals(0L, AppSettings().speedLimitBytesPerSec)
    }

    @Test fun `smart detection settings default to on`() {
        val s = AppSettings()
        assertEquals(true, s.smartAnalysis && s.duplicateDetection && s.linkValidation && s.securityCheck)
        assertEquals(true, s.smartNotifications && s.detectionHistory && s.backgroundAnalysis)
    }
}
