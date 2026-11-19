package com.vegam.core.download

import com.vegam.core.database.VegamDatabase
import com.vegam.core.database.toSegment
import com.vegam.engine.FileInfo
import com.vegam.engine.Segment
import com.vegam.engine.SegmentedDownloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** ViewModels ka ekmatra darwaza: DB + scheduler + live speed ek jagah. */
class DownloadRepository(
    private val db: VegamDatabase,
    private val scheduler: DownloadScheduler,
    private val live: LiveProgress,
    private val downloader: SegmentedDownloader,
    private val settings: DownloadSettings,
    val saveLocation: SaveLocationStore,
    private val settingsStore: SettingsStore,
) {
    val defaultSegments: Int get() = settings.defaultSegments
    val wifiOnlyDefault: Boolean get() = settings.wifiOnlyDefault
    val appSettings: Flow<AppSettings> get() = settingsStore.flow
    suspend fun updateSettings(change: (AppSettings) -> AppSettings) = settingsStore.update(change)

    /** DB badle ya live speed badle, dono par naya list. */
    fun observeAll(): Flow<List<DownloadItem>> =
        combine(db.downloads().observeAll(), live.speeds) { rows, speeds ->
            rows.map { it.toItem(speeds[it.id] ?: 0L) }
        }

    fun observe(id: Long): Flow<DownloadItem?> =
        combine(db.downloads().observe(id), live.speeds) { row, speeds -> row?.toItem(speeds[id] ?: 0L) }

    fun observeSegments(id: Long): Flow<List<Segment>> =
        db.segments().observe(id).map { rows -> rows.map { it.toSegment() } }

    /** Pichhle 60 second ki speed (1 sample/sec), Detail ke graph ke liye. */
    fun observeSpeedHistory(id: Long): Flow<List<Long>> = live.history.map { it[id] ?: emptyList() }

    /** Add screen: server se naam/size/Range support. Network error par Result.failure. */
    suspend fun probe(url: String): Result<FileInfo> = runCatching { downloader.probe(url) }

    suspend fun add(
        url: String, fileName: String, priority: Int = 1, wifiOnly: Boolean = false,
        maxSegments: Int? = null, scheduledAt: Long? = null,
    ): Long = scheduler.enqueue(url, fileName, priority, wifiOnly, maxSegments, scheduledAt, saveLocation.treeUri)

    suspend fun pause(id: Long) = scheduler.pause(id)
    suspend fun resume(id: Long) = scheduler.resume(id)
    suspend fun cancel(id: Long) = scheduler.cancel(id)
    suspend fun delete(id: Long) = scheduler.delete(id)
}
