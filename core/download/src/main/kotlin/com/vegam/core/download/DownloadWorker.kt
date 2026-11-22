package com.vegam.core.download

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vegam.core.database.DownloadStatus
import com.vegam.core.database.resumeState
import com.vegam.core.database.toEntity
import com.vegam.engine.DownloadException
import com.vegam.engine.DownloadListener
import com.vegam.engine.Segment
import com.vegam.engine.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Ek download = ek DownloadWorker. VegamEngine ko chalata hai, progress DB mein save karta hai
 * (resume ke liye), aur foreground notification update karta hai.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private class Progress(val downloaded: Long, val total: Long, val bytesPerSec: Long)

    /** Expedited work ke liye (Android 11 ya kam par WorkManager yahi maangta hai). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getLong(KEY_ID, -1L)
        val name = VegamGraph.get(applicationContext).db.downloads().get(id)?.fileName ?: "Download"
        return Notifications.foregroundInfo(applicationContext, id, name, 0, 0, 0)
    }

    override suspend fun doWork(): Result = coroutineScope {
        val id = inputData.getLong(KEY_ID, -1L)
        val graph = VegamGraph.get(applicationContext)
        val dao = graph.db.downloads()
        val segDao = graph.db.segments()

        val entity = dao.get(id) ?: return@coroutineScope Result.failure()
        // User ne start hone se pehle hi pause/cancel kar diya ho to kuch mat karo.
        if (entity.status in STOP_STATES) return@coroutineScope Result.success()

        dao.setStatus(id, DownloadStatus.RUNNING.name)
        val resume = entity.resumeState(segDao.get(id))

        // DB writes cancel se bachni chahiye (pause par aakhri progress save hona zaroori hai),
        // isliye writer worker ke scope se alag, aur finally mein NonCancellable join.
        val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val snapshots = Channel<List<Segment>>(Channel.CONFLATED)
        val writer = dbScope.launch {
            for (segs in snapshots) {
                segDao.replaceAll(id, segs.map { it.toEntity(id) })
                dao.setProgress(id, segs.sumOf { it.downloaded })
            }
        }
        val progress = Channel<Progress>(Channel.CONFLATED)
        val notifier = launch {
            for (p in progress) {
                try {
                    setForeground(Notifications.foregroundInfo(applicationContext, id, entity.fileName, p.downloaded, p.total, p.bytesPerSec))
                } catch (e: IllegalStateException) {
                    // Android 12+ par background se foreground service start na ho paaye: normal work ki tarah chalte raho.
                }
                delay(1_000)   // notification 1 second mein ek baar
            }
        }

        val listener = object : DownloadListener {
            override fun onStart(validators: Validators, totalBytes: Long) {
                // Ek baar, turant: ETag save na ho to kill ke baad resume validate nahi hoga.
                runBlocking(Dispatchers.IO) { dao.setValidators(id, totalBytes, validators.etag, validators.lastModified) }
            }
            override fun onSegments(segments: List<Segment>) { snapshots.trySend(segments) }
            override fun onProgress(downloaded: Long, total: Long, bytesPerSec: Long) {
                graph.live.update(id, bytesPerSec)
                progress.trySend(Progress(downloaded, total, bytesPerSec))
            }
        }

        try {
            graph.downloader.download(
                entity.url, File(entity.targetPath), entity.maxSegments, resume, listener,
                com.vegam.engine.RequestHeaders.decode(entity.headers),
            )
            withContext(NonCancellable) {
                snapshots.close(); writer.join()
                // Chuna hua folder (SAF) ho to ab copy karo; fail ho to file private folder mein safe rehti hai.
                val dest = entity.destUri
                var note: String? = null
                if (dest != null) {
                    val ok = runCatching { SafExporter.export(applicationContext, File(entity.targetPath), Uri.parse(dest), entity.fileName) }
                        .getOrDefault(false)
                    if (ok) File(entity.targetPath).delete()
                    else note = "Chune hue folder mein copy nahi hui, file app ke private folder mein hai."
                }
                dao.markCompleted(id, System.currentTimeMillis())
                if (note != null) dao.setError(id, note)
            }
            graph.scheduler.pump()
            Result.success()
        } catch (e: CancellationException) {
            throw e   // pause/cancel: status scheduler ne pehle hi set kar diya hai
        } catch (e: DownloadException) {
            dao.markFailed(id, e.message ?: "Download fail")
            graph.scheduler.pump()
            Result.failure()
        } catch (e: IOException) {
            // Probe/network ka ajeeb error: WorkManager backoff ke saath 3 baar dobara try karega.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else { dao.markFailed(id, e.message ?: "Network error"); graph.scheduler.pump(); Result.failure() }
        } finally {
            graph.live.clear(id)
            progress.close(); notifier.cancel()
            withContext(NonCancellable) { snapshots.close(); writer.join() }   // cancel par aakhri snapshot flush
            dbScope.cancel()
        }
    }

    companion object {
        const val KEY_ID = "download_id"
        private const val MAX_ATTEMPTS = 3
        private val STOP_STATES = setOf(
            DownloadStatus.PAUSED.name, DownloadStatus.CANCELLED.name, DownloadStatus.COMPLETED.name,
        )
    }
}
