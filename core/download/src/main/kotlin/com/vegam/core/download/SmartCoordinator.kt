package com.vegam.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vegam.core.database.DetectionEntity
import com.vegam.core.database.DownloadStatus
import com.vegam.core.database.VegamDatabase
import com.vegam.engine.UrlNames
import com.vegam.engine.smart.AnalysisStep
import com.vegam.engine.smart.KnownDownload
import com.vegam.engine.smart.KnownState
import com.vegam.engine.smart.LinkReport
import com.vegam.engine.smart.LinkState
import com.vegam.engine.smart.SmartAnalyzer
import com.vegam.engine.smart.SmartResult
import com.vegam.engine.smart.SmartToggles
import com.vegam.engine.smart.UrlTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SmartSource { CLIPBOARD, SHARE, OPEN_WITH, MANUAL }

/** Smart dialog ki halat: jaanch chal rahi hai, ya nateeja taiyaar. */
sealed interface SmartSession {
    val url: String
    val source: SmartSource

    data class Analyzing(override val url: String, override val source: SmartSource, val step: AnalysisStep) : SmartSession
    data class Ready(
        override val url: String, override val source: SmartSource,
        val result: SmartResult, val historyId: Long?,
    ) : SmartSession
}

/**
 * Smart Detection ka dimaag (Android side). Engine ka SmartAnalyzer chalata hai, history likhta hai,
 * notification deta hai. Download khud tabhi hota hai jab user dialog mein Download dabaye ([download]).
 *
 * - Ek hi link ke liye ek hi jaanch (normalized URL se pehchaan): duplicate job nahi.
 * - Cancel ho sakti hai ([cancel], ya background mein jaate hi agar Background Analysis OFF ho).
 * - Jaanch process ke andar chalti hai (coroutine, ~20 sec se kam). Process OS maar de to jaanch band: user ko
 *   dobara link bhejna padega. WorkManager is liye nahi liya ki ye chhota kaam hai aur foreground service ke bina
 *   Android 12+ par background start ki ijazat nahi.
 */
class SmartCoordinator(
    private val context: Context,
    private val db: VegamDatabase,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val enqueue: suspend (url: String, fileName: String) -> Long,
    private val analyzer: SmartAnalyzer = SmartAnalyzer(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _session = MutableStateFlow<SmartSession?>(null)
    val session: StateFlow<SmartSession?> = _session.asStateFlow()

    private val lock = Any()
    private val inFlight = HashMap<String, Job>()
    private val recent = LinkedHashMap<String, SmartSession.Ready>()   // notification tap ke liye
    @Volatile private var foreground = true

    /** Link do. Jaanch shuru hoti hai (ya same link pehle se chal raha ho to kuch nahi). */
    fun submit(rawUrl: String, source: SmartSource) {
        val url = rawUrl.trim()
        if (!UrlNames.isHttpUrl(url)) return
        val key = UrlTools.normalize(url)
        val job: Job
        synchronized(lock) {
            if (inFlight.containsKey(key)) return
            val cur = _session.value
            if (cur != null && UrlTools.normalize(cur.url) == key) return   // dialog isi link ka khula hai
            job = scope.launch(start = CoroutineStart.LAZY) { run(url, key, source) }
            inFlight[key] = job
        }
        job.invokeOnCompletion { synchronized(lock) { inFlight.remove(key) } }
        job.start()
    }

    /** Notification tap: us link ka taiyaar nateeja dialog mein wapas. */
    fun reopen(url: String) {
        val r = synchronized(lock) { recent[UrlTools.normalize(url)] } ?: return
        _session.value = r
    }

    /** User ne jaanch/dialog band ki. */
    fun cancel() {
        val s = _session.value
        synchronized(lock) { inFlight.values.toList() }.forEach { it.cancel() }
        _session.value = null
        if (s is SmartSession.Ready) scope.launch { decide(s, "CANCEL", null) }
    }

    /** MainActivity onStart/onStop se. Background Analysis OFF ho to background jaate hi jaanch ruk jaati hai. */
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            scope.launch {
                if (!settingsStore.flow.first().backgroundAnalysis) {
                    synchronized(lock) { inFlight.values.toList() }.forEach { it.cancel() }
                    _session.update { if (it is SmartSession.Analyzing) null else it }
                }
            }
        }
    }

    /**
     * User ne Download dabaya. Sirf tab chalta hai jab faisla ise allow kare (RED/NOT_POSSIBLE par kuch nahi hota).
     * Wahi purana engine: enqueue -> DownloadWorker. Original link bheji jaati hai (redirect engine khud follow karta hai).
     */
    suspend fun download(s: SmartSession.Ready): Long? {
        if (!s.result.decision.canDownload) return null
        val name = s.result.report.fileName.ifBlank { UrlNames.fileName(s.url) }
        val id = enqueue(s.url, name)
        decide(s, "DOWNLOAD", id)
        _session.value = null
        return id
    }

    private suspend fun decide(s: SmartSession.Ready, decision: String, downloadId: Long?) {
        val hid = s.historyId ?: return
        runCatching { db.detections().setDecision(hid, decision, downloadId) }
    }

    private suspend fun run(url: String, key: String, source: SmartSource) {
        val st = settingsStore.flow.first()
        _session.value = SmartSession.Analyzing(url, source, AnalysisStep.PARSING)
        val toggles = SmartToggles(st.smartAnalysis, st.duplicateDetection, st.linkValidation, st.securityCheck)
        val result = try {
            analyzer.run(url, loadKnown(), toggles) { step ->
                _session.update { if (it is SmartSession.Analyzing && it.url == url) it.copy(step = step) else it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Jaanch khud fail: block nahi, bas bataate hain. User chahe to phir bhi download kar sakta hai.
            val rep = LinkReport.fromUrlOnly(url, LinkState.INCONCLUSIVE, "Jaanch poori nahi ho saki: ${e.message ?: e.javaClass.simpleName}")
            analyzer.run(url, emptyList(), SmartToggles(linkAnalysis = false, duplicateDetection = false, linkValidation = false, securityCheck = st.securityCheck))
                .copy(report = rep)
        }
        val historyId = if (st.detectionHistory) saveHistory(url, key, source, result) else null
        val ready = SmartSession.Ready(url, source, result, historyId)
        synchronized(lock) {
            recent[key] = ready
            while (recent.size > 10) recent.remove(recent.keys.first())
        }
        _session.value = ready
        if (!foreground && st.smartNotifications) notifyReady(key, ready)
    }

    private suspend fun saveHistory(url: String, key: String, source: SmartSource, r: SmartResult): Long? = runCatching {
        val rep = r.report
        val warnings = buildList {
            r.security?.warnings?.forEach { add(it.message) }
            r.duplicate?.takeIf { it.isDuplicateWarning }?.let { add(it.explanation) }
            r.decision.blockReason?.let { add(it) }
        }
        val id = db.detections().insert(
            DetectionEntity(
                url = url, normalizedUrl = key, domain = rep.host.orEmpty(), fileName = rep.fileName,
                fileType = rep.category.name, mime = rep.mime, sizeBytes = rep.sizeBytes ?: -1,
                linkState = rep.state.name, linkDetail = rep.stateDetail,
                duplicate = r.duplicate?.kind?.name ?: "OFF",
                securityLevel = r.security?.level?.name ?: "OFF",
                warnings = warnings.joinToString("\n"), source = source.name,
                decision = if (r.decision.canDownload) "PENDING" else "BLOCKED",
                detectedAt = clock(),
            )
        )
        db.detections().trim()
        id
    }.getOrNull()

    private suspend fun loadKnown(): List<KnownDownload> = db.downloads().observeAll().first().map { d ->
        KnownDownload(
            id = d.id, normalizedUrl = UrlTools.normalize(d.url), fileName = d.fileName,
            sizeBytes = d.totalBytes.takeIf { it > 0 }, etag = d.etag, lastModified = d.lastModified, hash = null,
            state = when (d.status) {
                DownloadStatus.COMPLETED.name -> KnownState.COMPLETED
                DownloadStatus.FAILED.name, DownloadStatus.CANCELLED.name -> KnownState.ENDED
                else -> KnownState.ACTIVE
            },
            // SAF folder mein file ko seedha check nahi kar sakte (copy ke baad private file hat jaati hai): maan lete hain hai.
            fileExists = d.status != DownloadStatus.COMPLETED.name || d.destUri != null || File(d.targetPath).exists(),
        )
    }

    private fun notifyReady(key: String, s: SmartSession.Ready) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return    // permission nahi: chupchaap chhod do, dialog app khulne par milega
        ensureChannel(context)
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_REOPEN_URL, s.url)
        }
        val pi = PendingIntent.getActivity(
            context, key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val rep = s.result.report
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download mila: ${rep.fileName}")
            .setContentText("${rep.host.orEmpty()} · tap karke dekho")
            .setContentIntent(pi).setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFY_BASE + (key.hashCode() and 0xFFFF), n) }   // SecurityException = permission chali gayi
    }

    companion object {
        const val CHANNEL_ID = "vegam_smart"
        const val EXTRA_REOPEN_URL = "vegam.smart.reopen_url"
        private const val NOTIFY_BASE = 5000

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Smart Detection", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Link jaanch poori hone par"
                    }
                )
            }
        }
    }
}
