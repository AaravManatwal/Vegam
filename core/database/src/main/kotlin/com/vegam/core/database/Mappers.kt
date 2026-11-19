package com.vegam.core.database

import com.vegam.engine.ResumeState
import com.vegam.engine.Segment
import com.vegam.engine.Validators

/** Engine ke Segment <-> DB row. */
fun Segment.toEntity(downloadId: Long) = SegmentEntity(downloadId, index, start, end, downloaded)

fun SegmentEntity.toSegment() = Segment(index = idx, start = rangeStart, end = rangeEnd, downloaded = downloaded)

/**
 * DB se resume state banao. Segments ya total size nahi hain to null (matlab fresh download).
 * Validators (ETag/Last-Modified) engine ko diye jaate hain taaki wo server se match karke tabhi resume kare.
 */
fun DownloadEntity.resumeState(segments: List<SegmentEntity>): ResumeState? {
    if (segments.isEmpty() || totalBytes <= 0) return null
    return ResumeState(
        segments = segments.map { it.toSegment() },
        validators = Validators(etag = etag, lastModified = lastModified, contentLength = totalBytes),
    )
}
