package com.vegam.feature.add

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureHeadersTest {
    @Test fun `builds ua referer cookie and skips blanks`() {
        assertEquals(
            mapOf("User-Agent" to "UA", "Referer" to "https://p.example/x", "Cookie" to "a=b"),
            captureHeaders("UA", "https://p.example/x", "a=b"),
        )
        assertEquals(mapOf("User-Agent" to "UA"), captureHeaders("UA", null, ""))
    }
}
