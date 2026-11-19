package com.vegam.engine

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * VegamEngine: segmented multi-connection downloader.
 *
 * Flow: probe (size/Range/ETag) -> segments banao (ya resume state lo) -> N workers.
 * Har worker ek segment claim karta hai, Range request bhejta hai aur apne exact offset par
 * file mein likhta hai. Khali worker sabse bade bache segment ka aadha hissa steal karta hai.
 * Cancel/pause = coroutine cancel; `onSegments` ka aakhri snapshot resume ke liye kaafi hai.
 */
class SegmentedDownloader(
    private val client: OkHttpClient = VegamHttp.newClient(),
    private val retry: RetryPolicy = RetryPolicy(),
    private val rateLimiter: TokenBucket = TokenBucket(0),
) {
    /** Add screen ke liye: size, Range support aur validators bina download shuru kiye. */
    suspend fun probe(url: String): FileInfo = withContext(Dispatchers.IO) {
        val p = Prober.probe(client, url)
        FileInfo(p.totalBytes, p.acceptRanges, p.validators.etag, p.validators.lastModified)
    }

    suspend fun download(
        url: String,
        target: File,
        maxSegments: Int = 8,
        resume: ResumeState? = null,
        listener: DownloadListener = DownloadListener.NONE,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val probe = Prober.probe(client, url)
        val multi = probe.acceptRanges && probe.totalBytes >= SegmentPlanner.SMALL_FILE_BYTES
        // Resume tabhi jab server wahi file de raha ho (ETag/Last-Modified/length match).
        val saved = resume?.takeIf { multi && ResumeValidator.canResume(it.validators, probe.validators) }
        if (resume != null && saved == null) listener.onEvent("Resume possible nahi, zero se restart")
        val segments = saved?.segments ?: SegmentPlanner.split(probe.totalBytes, maxSegments, multi)
        val workers = if (multi) maxSegments.coerceIn(1, SegmentPlanner.MAX_SEGMENTS) else 1

        RandomAccessFile(target, "rw").use { raf ->
            if (saved == null) raf.setLength(0)                       // stale data saaf
            if (raf.length() != probe.totalBytes) raf.setLength(probe.totalBytes)  // pehle se space reserve
            Run(url, probe, multi, workers, segments, raf.channel, listener).run()
        }
    }

    /** Ek segment ki live state. `seg` har badlaav par naya immutable Segment hota hai. */
    private class SegState(@Volatile var seg: Segment) {
        @Volatile var claimed = false
    }

    private inner class Run(
        val url: String,
        val probe: Probe,
        val multi: Boolean,
        val workers: Int,
        initial: List<Segment>,
        val channel: FileChannel,
        val listener: DownloadListener,
    ) {
        private val lock = Any()                         // states + active + adaptive ke liye
        private val states = initial.map { SegState(it) }.toMutableList()
        private var nextIndex = (initial.maxOfOrNull { it.index } ?: -1) + 1
        private var active = 0
        private val adaptive = AdaptiveConcurrency(max = workers)
        private var lastSignal = Long.MIN_VALUE / 2
        private val downloaded = AtomicLong(initial.sumOf { it.downloaded })
        private val monitor = SpeedMonitor()

        suspend fun run(): DownloadOutcome {
            listener.onStart(probe.validators, probe.totalBytes)
            listener.onSegments(snapshot())       // shuru mein hi rows bane rahein, taaki jaldi kill par bhi resume ho
            try {
                coroutineScope {
                    val ticker = launch { while (isActive) { delay(500); tick() } }
                    (0 until workers).map { launch { worker() } }.joinAll()
                    ticker.cancel()
                }
            } finally {
                listener.onSegments(snapshot())   // cancel/fail par bhi: progress save hone ke liye
            }
            val got = snapshot().sumOf { it.downloaded }
            if (got != probe.totalBytes) throw DownloadException("Size mismatch: $got / ${probe.totalBytes}")
            return DownloadOutcome(probe.validators, probe.totalBytes, snapshot().size)
        }

        private fun snapshot(): List<Segment> = synchronized(lock) { states.map { it.seg } }

        // ---------- scheduling ----------

        private suspend fun worker() {
            while (true) {
                currentCoroutineContext().ensureActive()
                val st = claimNext()
                if (st == null) {
                    if (allDone()) return
                    delay(100)   // koi segment free nahi / throttle ki wajah se wait
                    continue
                }
                try { runWithRetry(st) } finally { release(st) }
            }
        }

        private fun allDone() = synchronized(lock) { states.all { it.seg.isDone } }

        private fun claimNext(): SegState? = synchronized(lock) {
            if (active >= adaptive.current) return null
            val st = states.firstOrNull { !it.claimed && !it.seg.isDone } ?: stealLocked() ?: return null
            st.claimed = true
            active++
            st
        }

        /** Lock (global) pakde hue chalta hai. Victim ka state-lock sirf ek pal ke liye. */
        private fun stealLocked(): SegState? {
            if (!multi) return null
            val victim = states.filter { !it.seg.isDone }.maxByOrNull { it.seg.remaining } ?: return null
            return synchronized(victim) {
                // Victim ka latest `downloaded` isi lock ke andar padho, warna progress overwrite ho jaata.
                val steal = SegmentPlanner.steal(listOf(victim.seg), nextIndex) ?: return null
                victim.seg = steal.victim
                nextIndex++
                SegState(steal.thief).also { states.add(it) }
            }
        }

        private fun release(st: SegState) = synchronized(lock) {
            if (st.claimed) { st.claimed = false; active-- }
        }

        /** Throttle ke baad zyada workers ho gaye to extra wala segment chhod deta hai (progress safe rehta hai). */
        private fun shouldYield(st: SegState): Boolean = synchronized(lock) {
            if (active > adaptive.current && st.claimed) { st.claimed = false; active--; true } else false
        }

        // ---------- retry ----------

        private suspend fun runWithRetry(st: SegState) {
            var attempt = 0
            while (true) {
                try {
                    streamSegment(st)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpStatusException) {
                    if (AdaptiveConcurrency.isThrottleStatus(e.code)) onThrottle()
                    if (!RetryPolicy.isRetryableStatus(e.code)) throw DownloadException("Server ne HTTP ${e.code} diya", e)
                    backoff(st, attempt++, e)
                } catch (e: IOException) {
                    currentCoroutineContext().ensureActive()   // cancel se aaya IOException ho to yahin ruk jao
                    backoff(st, attempt++, e)
                }
            }
        }

        private suspend fun backoff(st: SegState, attempt: Int, cause: Exception) {
            val wait = retry.delayMs(attempt)
                ?: throw DownloadException("Segment #${st.seg.index}: ${retry.maxRetries} retries ke baad fail", cause)
            listener.onEvent("Segment #${st.seg.index} retry ${attempt + 1} (${wait}ms baad): ${cause.message}")
            delay(wait)
        }

        private fun onThrottle() {
            val now = System.currentTimeMillis()
            var msg: String? = null
            synchronized(lock) {
                if (now - lastSignal > 1500) {
                    lastSignal = now
                    val prev = adaptive.current
                    adaptive.throttled(now)
                    msg = "Server throttle: connections $prev -> ${adaptive.current}"
                }
            }
            msg?.let { listener.onEvent(it) }
        }

        private fun tick() {
            val now = System.currentTimeMillis()
            var msg: String? = null
            synchronized(lock) {
                val before = adaptive.current
                if (adaptive.onHealthy(now) != before) msg = "Network theek: connections ${adaptive.current} tak wapas"
            }
            msg?.let { listener.onEvent(it) }
            listener.onProgress(downloaded.get(), probe.totalBytes, monitor.bytesPerSec(now))
            listener.onSegments(snapshot())
        }

        // ---------- network + disk ----------

        private suspend fun streamSegment(st: SegState) {
            val s = st.seg
            if (s.isDone) return
            val req = Request.Builder().url(url)
                .header("Accept-Encoding", "identity")   // gzip nahi, warna Content-Length/Range ka hisaab bigad jaata hai
                .header("Range", s.rangeHeader)
            // Resume par: agar file badal gayi ho to server 200 dega, hum use pakad lenge (neeche).
            if (s.nextByte > 0 && probe.validators.etag != null) req.header("If-Range", probe.validators.etag)
            val call = client.newCall(req.build())
            val cancelHook = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { resp ->
                    if (resp.code != 206) {
                        val fullStreamOk = resp.code == 200 && s.nextByte == 0L && !multi
                        if (!fullStreamOk) {
                            if (resp.isSuccessful) throw DownloadException("Server ne Range ignore kiya ya file badal gayi (HTTP ${resp.code})")
                            throw HttpStatusException(resp.code)
                        }
                    }
                    val body = resp.body ?: throw IOException("Empty response body")
                    val src = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (shouldYield(st)) return
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buf)
                        if (n < 0) break
                        val written = write(st, buf, n)
                        if (written > 0) {
                            downloaded.addAndGet(written.toLong())
                            monitor.record(written.toLong(), System.currentTimeMillis())
                            val wait = rateLimiter.acquire(written.toLong(), System.nanoTime())
                            if (wait > 0) delay(wait)
                        }
                        if (st.seg.isDone) return
                    }
                    if (!st.seg.isDone) throw IOException("Stream beech mein khatam ho gaya")
                }
            } finally {
                cancelHook.dispose()
            }
        }

        /** Positional write: har segment apne exact offset par likhta hai, koi seek/shared position nahi. */
        private fun write(st: SegState, buf: ByteArray, n: Int): Int = synchronized(st) {
            val s = st.seg
            // Steal ho chuka ho to end chhota ho gaya hoga: us se aage ke bytes thief ke hain, hum nahi likhte.
            val allowed = minOf(n.toLong(), s.remaining).toInt()
            if (allowed > 0) {
                val bb = ByteBuffer.wrap(buf, 0, allowed)
                var pos = s.nextByte
                while (bb.hasRemaining()) pos += channel.write(bb, pos)
                st.seg = s.copy(downloaded = s.downloaded + allowed)
            }
            allowed
        }
    }
}
