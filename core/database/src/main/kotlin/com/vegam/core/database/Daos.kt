package com.vegam.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY priority DESC, sortOrder ASC, createdAt ASC")
    suspend fun byStatus(status: String): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'RUNNING'")
    suspend fun runningCount(): Int

    @Query("SELECT targetPath FROM downloads")
    suspend fun allTargetPaths(): List<String>

    @Query("UPDATE downloads SET error = :error WHERE id = :id")
    suspend fun setError(id: Long, error: String?)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM downloads")
    suspend fun nextSortOrder(): Long

    @Query("UPDATE downloads SET status = :status, error = NULL WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE downloads SET status = 'FAILED', error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE downloads SET status = 'COMPLETED', downloadedBytes = totalBytes, completedAt = :now, error = NULL WHERE id = :id")
    suspend fun markCompleted(id: Long, now: Long)

    @Query("UPDATE downloads SET totalBytes = :total, etag = :etag, lastModified = :lastModified WHERE id = :id")
    suspend fun setValidators(id: Long, total: Long, etag: String?, lastModified: String?)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long)

    @Query("UPDATE downloads SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)

    @Query("UPDATE downloads SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long)

    /** App kill / reboot ke baad: jo RUNNING reh gaye the unhe wapas line mein daalo. */
    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'RUNNING'")
    suspend fun requeueRunning()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    // ---- Stats screen ----
    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    fun observeTotalDownloaded(): Flow<Long>

    @Query("SELECT status, COUNT(*) AS count FROM downloads GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCount>>
}

@Dao
abstract class SegmentDao {
    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY idx")
    abstract suspend fun get(downloadId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY idx")
    abstract fun observe(downloadId: Long): Flow<List<SegmentEntity>>

    @Query("DELETE FROM segments WHERE downloadId = :downloadId")
    abstract suspend fun deleteFor(downloadId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(list: List<SegmentEntity>)

    /** Poora snapshot ek transaction mein badlo: beech mein crash ho to purana ya naya, adha-adhoora nahi. */
    @Transaction
    open suspend fun replaceAll(downloadId: Long, list: List<SegmentEntity>) {
        deleteFor(downloadId)
        insertAll(list)
    }
}

@Dao
interface DetectionDao {
    @Insert
    suspend fun insert(e: DetectionEntity): Long

    @Query("UPDATE detections SET decision = :decision, downloadId = :downloadId WHERE id = :id")
    suspend fun setDecision(id: Long, decision: String, downloadId: Long?)

    @Query(
        "SELECT d.*, dl.status AS downloadStatus, dl.error AS downloadError FROM detections d " +
            "LEFT JOIN downloads dl ON dl.id = d.downloadId ORDER BY d.detectedAt DESC LIMIT 500"
    )
    fun observeAll(): Flow<List<DetectionRow>>

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM detections")
    suspend fun clear()

    /** Retention: sirf pichhli 500 entries rakho. */
    @Query("DELETE FROM detections WHERE id NOT IN (SELECT id FROM detections ORDER BY detectedAt DESC LIMIT 500)")
    suspend fun trim()
}
