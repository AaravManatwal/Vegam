package com.vegam.engine

import java.io.IOException

/** Fatal error: retry se theek nahi hoga (404, retries khatam, file badli, size mismatch). */
class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Retry-able HTTP status ke liye internal signal (429/503/5xx). */
internal class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Pichhli adhoori download ki state. Phase 2 mein ye Room se aayegi. */
data class ResumeState(val segments: List<Segment>, val validators: Validators)

data class DownloadOutcome(val validators: Validators, val totalBytes: Long, val segmentsUsed: Int)

/** UI/Service ko updates. Callbacks background thread par aate hain, UI thread par post karna caller ka kaam hai. */
interface DownloadListener {
    /** Probe ke turant baad ek baar: ETag/size ab save karo, warna kill ke baad resume validate nahi ho paayega. */
    fun onStart(validators: Validators, totalBytes: Long) {}
    fun onProgress(downloaded: Long, total: Long, bytesPerSec: Long) {}
    /** Har ~500ms + download khatam/cancel/fail hone par ek baar. Resume ke liye ise save karo. */
    fun onSegments(segments: List<Segment>) {}
    fun onEvent(message: String) {}

    companion object {
        val NONE: DownloadListener = object : DownloadListener {}
    }
}
