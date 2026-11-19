package com.vegam.engine.smart

import com.vegam.engine.VegamHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AnalysisStep { PARSING, CONTACTING, READING_HEADERS, VALIDATING, DONE }

/**
 * Download shuru kiye bina link ki jaanch: sirf headers padhta hai, file ka body kabhi nahi.
 *
 * - Pehle HEAD. Server HEAD na maane (400/403/404/405/406/501) ya size na de to ek "Range: bytes=0-0" GET
 *   (body padhe bina band). S3 jaise presigned links HEAD par 403 dete hain par GET par chalte hain.
 * - Redirect haath se follow hote hain (max [maxRedirects]) taaki poora raasta dikhe aur HTTPS->HTTP gir jaana pakda jaye.
 * - Asthayi galti (timeout, 429, 502-504) par sirf [maxRetries] baar dobara, chhote wait ke saath. Kabhi anant nahi.
 * - Coroutine cancel hote hi chalta network call bhi cancel.
 * - Cookies/credentials kuch nahi bhejta; kuch bahar upload nahi hota (sirf wahi link jise user ne chuna).
 *
 * Nateeja kabhi "andaza" nahi: server ka jawab saaf na ho to state = INCONCLUSIVE.
 */
class LinkAnalyzer(
    baseClient: OkHttpClient = VegamHttp.newClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val maxRetries: Int = 2,
    private val retryDelayMs: Long = 600,
    private val maxRedirects: Int = 8,
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private class Fetched(val response: Response, val method: String)
    private sealed interface Fx {
        class Ok(val f: Fetched) : Fx
        class Err(val state: LinkState, val detail: String) : Fx
    }

    suspend fun analyze(rawUrl: String, onStep: (AnalysisStep) -> Unit = {}): LinkReport = withContext(Dispatchers.IO) {
        onStep(AnalysisStep.PARSING)
        val input = rawUrl.trim()
        val scheme = input.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") {
            return@withContext fail(input, input, LinkState.UNSUPPORTED, "Vegam sirf http/https links download karta hai.", emptyList(), 0)
        }
        var current: HttpUrl = input.toHttpUrlOrNull()
            ?: return@withContext fail(input, input, LinkState.INVALID, "Link ka format galat hai.", emptyList(), 0)

        val hops = ArrayList<RedirectHop>()
        val notes = ArrayList<String>()
        val counter = intArrayOf(0)
        var report: LinkReport? = null

        onStep(AnalysisStep.CONTACTING)
        while (report == null) {
            val fx = fetch(current, counter, notes)
            if (fx is Fx.Err) {
                report = fail(input, current.toString(), fx.state, fx.detail, hops, counter[0], notes)
                break
            }
            val fetched = (fx as Fx.Ok).f
            val r = fetched.response
            try {
                if (r.code in REDIRECTS) {
                    hops += RedirectHop(current.toString(), r.code)
                    val loc = r.header("Location")
                    val next = loc?.let { current.resolve(it) }
                    when {
                        loc == null -> report = fail(input, current.toString(), LinkState.INCONCLUSIVE, "Server ne redirect (HTTP ${r.code}) diya par naya link nahi bataya.", hops, counter[0], notes)
                        next == null -> report = fail(input, current.toString(), LinkState.INVALID, "Redirect ka naya link samajh nahi aaya: $loc", hops, counter[0], notes)
                        hops.size > maxRedirects -> report = fail(input, current.toString(), LinkState.INVALID, "Bahut zyada redirect ($maxRedirects se upar): link ghoom raha hai.", hops, counter[0], notes)
                        else -> current = next
                    }
                } else {
                    onStep(AnalysisStep.READING_HEADERS)
                    report = build(input, current, fetched, hops, counter[0], notes)
                }
            } finally {
                r.close()
            }
        }
        onStep(AnalysisStep.VALIDATING)
        onStep(AnalysisStep.DONE)
        report!!
    }

    // ---------- network ----------

    private suspend fun fetch(url: HttpUrl, counter: IntArray, notes: MutableList<String>): Fx {
        val head = exec(url, useHead = true, counter = counter)
        if (head is Fx.Err) return head
        val h = (head as Fx.Ok).f
        val code = h.response.code
        val noLength = code in 200..299 && h.response.header("Content-Length") == null
        if (code !in HEAD_FALLBACK && !noLength) return head
        // HEAD kaam ka nahi nikla: ek ranged GET (body nahi padhenge).
        val get = exec(url, useHead = false, counter = counter)
        return if (get is Fx.Ok) {
            h.response.close()
            if (code !in 200..299) notes += "Server ne HEAD par HTTP $code diya; Range GET se jaankari li."
            else notes += "HEAD se size nahi mila; Range GET se liya."
            get
        } else if (code in 200..299) head else { h.response.close(); get }
    }

    private suspend fun exec(url: HttpUrl, useHead: Boolean, counter: IntArray): Fx {
        var last: Fx.Err? = null
        for (attempt in 0..maxRetries) {
            if (attempt > 0) delay(retryDelayMs * attempt)
            counter[0]++
            val req = Request.Builder().url(url)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "Vegam/1.0 link-check")
                .apply { if (useHead) head() else header("Range", "bytes=0-0") }
                .build()
            try {
                val resp = client.newCall(req).awaitResponse()
                if (resp.code in RETRYABLE && attempt < maxRetries) { resp.close(); last = Fx.Err(LinkState.TEMP_UNAVAILABLE, "Server ne HTTP ${resp.code} diya"); continue }
                return Fx.Ok(Fetched(resp, if (useHead) "HEAD" else "GET-RANGE"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val err = classify(e)
                if (err.state == LinkState.TLS_ERROR || err.state == LinkState.UNSUPPORTED) return err
                last = err
            }
        }
        return last ?: Fx.Err(LinkState.NETWORK_ERROR, "Server se jawab nahi mila")
    }

    private fun classify(e: IOException): Fx.Err {
        val msg = e.message.orEmpty()
        return when {
            e is SSLException || e.cause is CertificateException -> Fx.Err(LinkState.TLS_ERROR, msg.ifBlank { "SSL/certificate galti" })
            msg.contains("Cleartext", true) -> Fx.Err(LinkState.UNSUPPORTED, "Is app ko HTTP (bina HTTPS) links ki ijazat nahi mili.")
            e is UnknownHostException -> Fx.Err(LinkState.NETWORK_ERROR, "Domain nahi mila ya internet nahi hai (${e.message})")
            e is SocketTimeoutException || msg.contains("timeout", true) -> Fx.Err(LinkState.TEMP_UNAVAILABLE, "Server ne samay par jawab nahi diya (timeout)")
            else -> Fx.Err(LinkState.NETWORK_ERROR, "Network galti: ${msg.ifBlank { e.javaClass.simpleName }}")
        }
    }

    // ---------- report ----------

    private fun build(input: String, url: HttpUrl, f: Fetched, hops: List<RedirectHop>, attempts: Int, notes: List<String>): LinkReport {
        val r = f.response
        val code = r.code
        val mime = FileTypes.cleanMime(r.header("Content-Type"))
        val disp = r.header("Content-Disposition")
        val finalUrl = url.toString()

        // Size aur Range support
        var size: Long? = null
        var ranges: Boolean? = null
        if (code == 206) {
            size = r.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
            ranges = true
        } else if (code in 200..299) {
            size = r.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
            ranges = if (f.method == "GET-RANGE") false else r.header("Accept-Ranges")?.contains("bytes", true) == true
        }

        val name = pickName(input, finalUrl, disp)
        val ext = FileTypes.extensionOf(name)
        val (state, detail) = stateFor(code, input, finalUrl)

        val allNotes = notes.toMutableList()
        if (state == LinkState.VALID && code == 200 && f.method == "GET-RANGE") allNotes += "Server ne Range ignore karke poori file (HTTP 200) dena chaha: connection turant band kiya."
        return LinkReport(
            inputUrl = input, finalUrl = finalUrl, normalizedUrl = UrlTools.normalize(finalUrl.ifBlank { input }),
            state = state, stateDetail = detail, httpStatus = code,
            fileName = name, extension = ext, category = FileTypes.categoryOf(ext, mime),
            mime = mime, contentDisposition = disp, server = r.header("Server"),
            sizeBytes = size, acceptRanges = ranges, etag = r.header("ETag"), lastModified = r.header("Last-Modified"),
            contentHash = hashOf(r), redirects = hops, methodUsed = f.method, attempts = attempts,
            looksLikeWebPage = state == LinkState.VALID && FileTypes.looksLikeWebPage(mime, disp),
            notes = allNotes,
        )
    }

    private fun stateFor(code: Int, input: String, finalUrl: String): Pair<LinkState, String> {
        if (code in 200..299) return LinkState.VALID to "Server ne HTTP $code diya: link chal raha hai."
        // Expiry sirf tab jab URL ke andar saaf likhi ho aur beet chuki ho: andaza nahi.
        if (code in listOf(401, 403, 404, 410)) {
            val exp = listOf(finalUrl, input).mapNotNull { UrlTools.signedExpiryMillis(it) }.minOrNull()
            if (exp != null && exp < now()) {
                val on = DateFormat.getDateTimeInstance().format(Date(exp))
                return LinkState.EXPIRED_EVIDENCE to "Link ke andar likhi expiry ($on) beet chuki hai aur server ne HTTP $code diya."
            }
        }
        return when (code) {
            401, 407 -> LinkState.AUTH_REQUIRED to "Server login maang raha hai (HTTP $code)."
            403 -> LinkState.ACCESS_DENIED to "Server ne access mana kiya (HTTP 403). Wajah server ne nahi batayi."
            404 -> LinkState.INVALID to "Server ne bataya: file nahi mili (HTTP 404)."
            410 -> LinkState.INVALID to "Server ne bataya: file hata di gayi (HTTP 410 Gone)."
            429 -> LinkState.TEMP_UNAVAILABLE to "Server ne bahut zyada requests ka kaha (HTTP 429). Thodi der baad try karo."
            in 500..599 -> LinkState.TEMP_UNAVAILABLE to "Server abhi kaam nahi kar raha (HTTP $code)."
            416 -> LinkState.INCONCLUSIVE to "Server ne Range request mana ki (HTTP 416): file ka size jaanch nahi paye."
            else -> LinkState.INVALID to "Server ne HTTP $code diya."
        }
    }

    private fun pickName(input: String, finalUrl: String, disposition: String?): String {
        FileTypes.parseContentDisposition(disposition)?.let { return it }
        val fromFinal = FileTypes.resolveName(finalUrl, null)
        if (FileTypes.extensionOf(fromFinal) != null) return fromFinal
        val fromInput = FileTypes.resolveName(input, null)
        return if (FileTypes.extensionOf(fromInput) != null) fromInput else fromFinal
    }

    /** Hash sirf tab jab server khud de: "Digest: sha-256=..." ya "Content-MD5". Hum kuch compute nahi karte. */
    private fun hashOf(r: Response): String? {
        val digest = r.header("Digest")?.split(',')?.firstOrNull()?.trim()
        if (!digest.isNullOrBlank() && '=' in digest) return digest.substringBefore('=').lowercase() + ":" + digest.substringAfter('=')
        return r.header("Content-MD5")?.trim()?.takeIf { it.isNotBlank() }?.let { "md5:$it" }
    }

    private fun fail(
        input: String, at: String, state: LinkState, detail: String,
        hops: List<RedirectHop>, attempts: Int, notes: List<String> = emptyList(),
    ): LinkReport = LinkReport.fromUrlOnly(input, state, detail).copy(
        finalUrl = at, normalizedUrl = UrlTools.normalize(at), redirects = hops, attempts = attempts, notes = notes,
        methodUsed = if (attempts == 0) "none" else "failed",
    )

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { response.close() }
            }
        })
    }

    private companion object {
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val RETRYABLE = setOf(429, 502, 503, 504)
        val HEAD_FALLBACK = setOf(400, 403, 404, 405, 406, 501)
    }
}
