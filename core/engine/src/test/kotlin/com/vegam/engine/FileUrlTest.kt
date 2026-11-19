package com.vegam.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileUrlTest {
    @Test fun `known file links are detected`() {
        assertTrue(isLikelyFileUrl("https://x.com/a/b/movie.MP4"))
        assertTrue(isLikelyFileUrl("http://x.com/app.apk"))
        assertTrue(isLikelyFileUrl("https://x.com/file.zip?token=abc#frag"))
    }

    @Test fun `normal web pages are not suggested`() {
        assertFalse(isLikelyFileUrl("https://example.com/"))
        assertFalse(isLikelyFileUrl("https://example.com/blog/post.html"))
        assertFalse(isLikelyFileUrl("https://example.com/watch?v=zip"))
    }

    @Test fun `non http text is ignored`() {
        assertFalse(isLikelyFileUrl("ftp://x.com/a.zip"))
        assertFalse(isLikelyFileUrl("just some text.zip"))
    }

    @Test fun `extension in query only does not count`() = assertFalse(isLikelyFileUrl("https://x.com/download?file=a.zip"))
}
