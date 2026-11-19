package com.vegam.core.download

import com.vegam.core.database.DownloadEntity
import com.vegam.core.database.DownloadStatus

/** UI ko dikhne wala domain model: DB row + live speed. UI ko DB ya Room ke baare mein kuch nahi pata hona chahiye. */
data class DownloadItem(
    val id: Long,
    val name: String,
    val url: String,
    val status: DownloadStatus,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val bytesPerSec: Long,
    val priority: Int,
    val wifiOnly: Boolean,
    val maxSegments: Int,
    val error: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val targetPath: String = "",
    val destUri: String? = null,
) {
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    /** Speed 0 ya size unknown ho to null (UI "--" dikhata hai). */
    val etaSeconds: Long? get() =
        if (bytesPerSec > 0 && totalBytes > 0) ((totalBytes - downloadedBytes) / bytesPerSec).coerceAtLeast(0) else null

    val isRunning: Boolean get() = status == DownloadStatus.RUNNING
}

fun DownloadEntity.toItem(bytesPerSec: Long): DownloadItem = DownloadItem(
    id = id, name = fileName, url = url,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    totalBytes = totalBytes, downloadedBytes = downloadedBytes, bytesPerSec = bytesPerSec,
    priority = priority, wifiOnly = wifiOnly, maxSegments = maxSegments, error = error,
    createdAt = createdAt, completedAt = completedAt, targetPath = targetPath, destUri = destUri,
)
