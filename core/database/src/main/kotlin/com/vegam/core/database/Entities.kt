package com.vegam.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Download ki lifecycle. QUEUED = line mein wait, RUNNING = slot mil gaya (worker enqueue ho chuka),
 * PAUSED = user ne roka, baaki terminal states.
 */
enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

/** Priority: 0 = low, 1 = normal, 2 = high (QueuePolicy isi level se sort karta hai). */
@Entity(tableName = "downloads", indices = [Index("status")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val targetPath: String,
    val status: String = DownloadStatus.QUEUED.name,   // String store: enum converter ki zaroorat nahi
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val priority: Int = 1,
    val sortOrder: Long = 0,                            // drag & drop order
    val maxSegments: Int = 8,
    val wifiOnly: Boolean = false,
    val etag: String? = null,                           // resume validators
    val lastModified: String? = null,
    val error: String? = null,
    val createdAt: Long = 0,
    val scheduledAt: Long? = null,                      // null = abhi, warna is time ke baad
    val completedAt: Long? = null,
    val destUri: String? = null,                        // SAF folder (tree uri); null = app ka private folder
    val headers: String? = null,                        // page se pakda download: "User-Agent: ..\nReferer: ..\nCookie: .." (RequestHeaders)
)

/** Segment ka progress. Column names `start`/`end` SQL keywords se bachne ke liye rangeStart/rangeEnd hain. */
@Entity(
    tableName = "segments",
    primaryKeys = ["downloadId", "idx"],
    foreignKeys = [ForeignKey(
        entity = DownloadEntity::class,
        parentColumns = ["id"],
        childColumns = ["downloadId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SegmentEntity(
    val downloadId: Long,
    val idx: Int,
    val rangeStart: Long,
    val rangeEnd: Long,
    val downloaded: Long,
)

/** Stats screen ke liye: har status ke kitne downloads. */
data class StatusCount(val status: String, val count: Int)

/**
 * Smart Detection history (sirf phone par). Har link jo Vegam ne jaancha: kya mila, kya faisla hua.
 * downloadId = agar user ne Download dabaya (downloads table se status/error join hota hai).
 */
@Entity(tableName = "detections", indices = [Index("normalizedUrl"), Index("detectedAt")])
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val normalizedUrl: String,
    val domain: String,
    val fileName: String,
    val fileType: String,            // category naam: VIDEO, ARCHIVE...
    val mime: String? = null,
    val sizeBytes: Long = -1,        // -1 = pata nahi
    val linkState: String,           // LinkState naam
    val linkDetail: String = "",
    val duplicate: String = "NONE",  // DuplicateKind naam, ya "OFF"
    val securityLevel: String = "OFF",   // GREEN / YELLOW / RED / OFF
    val warnings: String = "",       // '\n' se alag
    val source: String = "",         // CLIPBOARD / SHARE / OPEN_WITH / MANUAL
    val decision: String = "PENDING",// PENDING / DOWNLOAD / CANCEL / BLOCKED / ABANDONED
    val downloadId: Long? = null,
    val detectedAt: Long = 0,
)

/** History row + (agar download hua) uska ab ka status. */
data class DetectionRow(
    @androidx.room.Embedded val d: DetectionEntity,
    val downloadStatus: String?,
    val downloadError: String?,
)
