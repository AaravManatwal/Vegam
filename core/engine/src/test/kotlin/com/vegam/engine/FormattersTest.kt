package com.vegam.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test fun `bytes pick the right unit`() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("2 KB", Formatters.bytes(2048))
        assertEquals("1.5 MB", Formatters.bytes(1536L * 1024))
        assertEquals("1.00 GB", Formatters.bytes(1L shl 30))
    }

    @Test fun `speed adds per second`() = assertEquals("3.0 MB/s", Formatters.speed(3L * 1024 * 1024))

    @Test fun `eta formats seconds minutes hours`() {
        assertEquals("--", Formatters.eta(null))
        assertEquals("45s", Formatters.eta(45))
        assertEquals("1m 05s", Formatters.eta(65))
        assertEquals("1h 02m", Formatters.eta(3725))
    }
}
