package com.vegam.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal data class Probe(
    val totalBytes: Long, val acceptRanges: Boolean, val validators: Validators, val contentType: String? = null,
) {
    val sizeKnown: Boolean get() = totalBytes > 0
}

/** Download shuru karne se pehle: size, Range support, ETag/Last-Modified pata karo. Blocking call. */
internal object Prober {
    fun probe(client: OkHttpClient, url: String): Probe {
        // 1) HEAD try karo
        try {
            val head = Request.Builder().url(url).head().header("Accept-Encoding", "identity").build()
            client.newCall(head).execute().use { r ->
                val len = r.header("Content-Length")?.toLongOrNull()
                if (r.isSuccessful && len != null && len > 0) {
                    val ranges = r.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    return Probe(len, ranges, validatorsOf(r, len), r.header("Content-Type"))
                }
            }
        } catch (_: IOException) {
            // HEAD fail hua, GET se try karte hain
        }
        // 2) Fallback: GET bytes=0-0. 206 = Range support hai.
        val get = Request.Builder().url(url)
            .header("Range", "bytes=0-0").header("Accept-Encoding", "identity").build()
        client.newCall(get).execute().use { r ->
            if (!r.isSuccessful) throw DownloadException("Server ne HTTP ${r.code} diya")
            if (r.code == 206) {
                val total = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: throw DownloadException("Content-Range se size nahi mila")
                return Probe(total, true, validatorsOf(r, total), r.header("Content-Type"))
            }
            val len = r.header("Content-Length")?.toLongOrNull()
            // Content-Length nahi (chunked): size unknown. Engine ab 1 connection se stream karta hai.
            if (len == null || len <= 0) return Probe(-1, false, validatorsOf(r, -1), r.header("Content-Type"))
            return Probe(len, false, validatorsOf(r, len), r.header("Content-Type"))
        }
    }

    private fun validatorsOf(r: Response, len: Long) =
        Validators(etag = r.header("ETag"), lastModified = r.header("Last-Modified"), contentLength = len)
}
