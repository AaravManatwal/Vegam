package com.vegam.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzyMatchTest {
    @Test fun `blank query matches everything`() = assertTrue(FuzzyMatch.matches("  ", "anything.zip"))
    @Test fun `letters must appear in order`() {
        assertTrue(FuzzyMatch.matches("ubn", "Ubuntu-24.04.iso"))
        assertFalse(FuzzyMatch.matches("nbu", "Ubuntu-24.04.iso"))
    }
    @Test fun `case does not matter`() = assertTrue(FuzzyMatch.matches("MOVIE", "movie-4k.mkv"))
    @Test fun `missing letter fails`() = assertFalse(FuzzyMatch.matches("xyz", "movie.mkv"))
}

class UrlNamesTest {
    @Test fun `plain url`() = assertEquals("file.zip", UrlNames.fileName("https://a.com/x/file.zip"))
    @Test fun `query and fragment are dropped`() = assertEquals("f.iso", UrlNames.fileName("https://a.com/f.iso?token=1&x=2#top"))
    @Test fun `percent encoding is decoded`() = assertEquals("My File.pdf", UrlNames.fileName("https://a.com/My%20File.pdf"))
    @Test fun `plus stays plus`() = assertEquals("a+b.zip", UrlNames.fileName("https://a.com/a+b.zip"))
    @Test fun `no name falls back`() = assertEquals("download.bin", UrlNames.fileName("https://a.com/"))
    @Test fun `bad chars are replaced`() = assertEquals("a_b.zip", UrlNames.fileName("https://a.com/a%3Ab.zip"))
    @Test fun `http url validation`() {
        assertTrue(UrlNames.isHttpUrl("https://example.com/f.zip"))
        assertFalse(UrlNames.isHttpUrl("ftp://example.com/f.zip"))
        assertFalse(UrlNames.isHttpUrl("https://exa mple.com"))
        assertFalse(UrlNames.isHttpUrl("hello"))
    }
}
