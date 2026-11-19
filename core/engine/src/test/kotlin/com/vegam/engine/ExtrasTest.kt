package com.vegam.engine

import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChecksumTest {
    @Test fun `sha256 of abc matches the known vector`() {
        val f = File.createTempFile("vegam", ".txt").also { it.deleteOnExit(); it.writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksum.sha256(f))
    }
}

class UniqueNameTest {
    @Test fun `free name is kept`() = assertEquals("a.zip", UniqueName.pick("a.zip") { false })
    @Test fun `taken name gets a counter before the extension`() =
        assertEquals("a (1).zip", UniqueName.pick("a.zip") { it == "a.zip" })
    @Test fun `counter keeps going`() =
        assertEquals("a (2).zip", UniqueName.pick("a.zip") { it == "a.zip" || it == "a (1).zip" })
    @Test fun `name without extension`() = assertEquals("notes (1)", UniqueName.pick("notes") { it == "notes" })
}

class ScheduleTest {
    private val utc = ZoneId.of("UTC")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun `later today when time is still ahead`() =
        assertEquals(at("2026-11-17T21:00:00Z"), Schedule.nextOccurrence(at("2026-11-17T20:00:00Z"), utc, 21, 0))

    @Test fun `tomorrow when time already passed`() =
        assertEquals(at("2026-11-18T02:00:00Z"), Schedule.nextOccurrence(at("2026-11-17T20:00:00Z"), utc, 2, 0))

    @Test fun `exactly now rolls to tomorrow`() =
        assertEquals(at("2026-11-18T20:00:00Z"), Schedule.nextOccurrence(at("2026-11-17T20:00:00Z"), utc, 20, 0))

    @Test fun `in hours`() = assertEquals(7_200_000L, Schedule.inHours(0, 2))
}

class ExtractUrlTest {
    @Test fun `finds link inside shared text`() =
        assertEquals("https://x.com/f.zip", extractUrl("Check this out https://x.com/f.zip please"))
    @Test fun `trailing punctuation is trimmed`() = assertEquals("https://x.com/f.zip", extractUrl("see https://x.com/f.zip."))
    @Test fun `no link gives null`() { assertNull(extractUrl("hello")); assertNull(extractUrl(null)) }
}
