package com.vegam.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vegam.core.database.DownloadEntity
import com.vegam.core.database.DownloadStatus
import com.vegam.core.database.VegamDatabase
import com.vegam.engine.QueueItem
import com.vegam.engine.QueuePolicy
import com.vegam.engine.UniqueName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Queue ka "manager": DB mein download likhta hai, QueuePolicy se faisla leta hai ki kaun shuru ho,
 * aur WorkManager mein ek unique work (`vegam-dl-<id>`) enqueue karta hai.
 * Sach (source of truth) DB ka `status` column hai; WorkManager sirf chalane ka tarika hai.
 */
class DownloadScheduler(
    private val context: Context,
    private val db: VegamDatabase,
    private val settings: DownloadSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val pumpLock = Mutex()   // do jagah se ek saath pump() chale to slots double na ban jaayein

    suspend fun enqueue(
        url: String, fileName: String, priority: Int = 1, wifiOnly: Boolean = false,
        maxSegments: Int? = null, scheduledAt: Long? = null, destUri: String? = null, headers: String? = null,
    ): Long {
        val dao = db.downloads()
        val dir = VegamGraph.downloadDir(context)
        // Same naam do baar aaye to "file (1).zip": warna doosri download pehli ko overwrite kar deti.
        val taken = dao.allTargetPaths().map { File(it).name }.toSet()
        val name = UniqueName.pick(fileName) { it in taken || File(dir, it).exists() }
        val id = dao.insert(
            DownloadEntity(
                url = url, fileName = name, targetPath = File(dir, name).path, priority = priority,
                sortOrder = dao.nextSortOrder(), maxSegments = (maxSegments ?: settings.defaultSegments).coerceIn(1, 16),
                wifiOnly = wifiOnly, createdAt = clock(), scheduledAt = scheduledAt, destUri = destUri, headers = headers,
            )
        )
        pump()
        return id
    }

    /** Khali slots bharo. Har state badlaav (add, finish, pause, resume, settings) ke baad bulao. */
    suspend fun pump() = pumpLock.withLock {
        val dao = db.downloads()
        val now = clock()
        val queued = dao.byStatus(DownloadStatus.QUEUED.name)
        val picks = QueuePolicy.pickNext(
            queued = queued.map { QueueItem(it.id, it.priority, it.sortOrder, it.createdAt, it.scheduledAt) },
            runningCount = dao.runningCount(),
            maxParallel = settings.maxParallel,
            now = now,
        )
        for (pick in picks) {
            val entity = queued.first { it.id == pick.id }
            dao.setStatus(entity.id, DownloadStatus.RUNNING.name)
            workManager.enqueueUniqueWork(workName(entity.id), ExistingWorkPolicy.KEEP, request(entity))
        }
        // Schedule ka time abhi nahi aaya hua items ke liye: us waqt dobara pump karwao.
        queued.mapNotNull { it.scheduledAt }.filter { it > now }.minOrNull()?.let { at ->
            workManager.enqueueUniqueWork(
                "vegam-pump", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(PumpWorker::class.java).setInitialDelay(at - now, TimeUnit.MILLISECONDS).build(),
            )
        }
    }

    /** Pause: pehle DB (taaki worker cancel hone par status na bigde), phir work cancel, phir agla slot. */
    suspend fun pause(id: Long) {
        db.downloads().setStatus(id, DownloadStatus.PAUSED.name)
        workManager.cancelUniqueWork(workName(id))
        pump()
    }

    suspend fun resume(id: Long) {
        db.downloads().setStatus(id, DownloadStatus.QUEUED.name)
        pump()
    }

    /** Cancel: row rehti hai (history/stats ke liye), partial file aur segments hat jaate hain. */
    suspend fun cancel(id: Long) {
        val dao = db.downloads()
        val entity = dao.get(id) ?: return
        dao.setStatus(id, DownloadStatus.CANCELLED.name)
        workManager.cancelUniqueWork(workName(id))
        db.segments().deleteFor(id)
        File(entity.targetPath).delete()
        pump()
    }

    /** List se hatana (swipe-to-delete): work cancel, row aur file dono saaf. */
    suspend fun delete(id: Long) {
        val entity = db.downloads().get(id) ?: return
        workManager.cancelUniqueWork(workName(id))
        db.downloads().delete(id)          // segments FK CASCADE se apne aap jaate hain
        VegamGraph.get(context).live.forget(id)
        File(entity.targetPath).delete()
        pump()
    }

    /** App start par: process kill se RUNNING reh gayi rows ko wapas line mein daalo. */
    suspend fun recover() {
        db.downloads().requeueRunning()
        pump()
    }

    private fun request(e: DownloadEntity): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (e.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(settings.chargingOnly)
            .build()
        return OneTimeWorkRequest.Builder(DownloadWorker::class.java)
            .setInputData(workDataOf(DownloadWorker.KEY_ID to e.id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            // Expedited = app background mein ho tab bhi foreground service start ki ijaazat milti hai.
            // Charging constraint ke saath expedited ka support pakka nahi, isliye tab skip.
            .apply { if (!settings.chargingOnly) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
    }

    companion object {
        fun workName(id: Long) = "vegam-dl-$id"
    }
}

/** Scheduled download ka waqt aane par queue ko jagata hai. */
class PumpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        VegamGraph.get(applicationContext).scheduler.pump()
        return Result.success()
    }
}
