package com.vegam.engine

import java.net.URLDecoder

/** Search bar ka fuzzy match: query ke akshar text mein order se aaye (beech mein gap chalega). */
object FuzzyMatch {
    fun matches(query: String, text: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        var k = 0
        for (c in text.lowercase()) {
            if (c == q[k]) k++
            if (k == q.length) return true
        }
        return false
    }
}

/** URL se file ka naam nikalna. Query/fragment hatao, %20 decode karo, galat characters saaf karo. */
object UrlNames {
    private val BAD = Regex("[\\\\/:*?\"<>|]")

    fun fileName(url: String): String {
        val path = url.substringBefore('#').substringBefore('?').trimEnd('/')
        val last = path.substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(last.replace("+", "%2B"), "UTF-8")   // '+' ko space na banne do
        } catch (e: IllegalArgumentException) {
            last
        }
        val clean = decoded.replace(BAD, "_").trim()
        return clean.ifBlank { "download.bin" }
    }

    fun isHttpUrl(url: String): Boolean {
        val u = url.trim()
        return (u.startsWith("http://") || u.startsWith("https://")) && u.length > 10 && !u.contains(' ')
    }
}
