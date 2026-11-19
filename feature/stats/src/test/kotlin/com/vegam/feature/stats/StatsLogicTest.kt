package com.vegam.feature.stats

import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsLogicTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 11, 18).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun item(name: String, size: Long, status: DownloadStatus, doneAt: Long?) = DownloadItem(
        id = name.hashCode().toLong(), name = name, url = "", status = status, totalBytes = size, downloadedBytes = size,
        bytesPerSec = 0, priority = 1, wifiOnly = false, maxSegments = 8, error = null, createdAt = 0, completedAt = doneAt,
    )
    private val kind = { n: String -> n.substringAfterLast('.').uppercase() }

    @Test fun `only completed files count`() {
        val s = buildStats(listOf(
            item("a.zip", 100, DownloadStatus.COMPLETED, now),
            item("b.zip", 900, DownloadStatus.RUNNING, null),
            item("c.zip", 50, DownloadStatus.CANCELLED, null),
            item("d.zip", 10, DownloadStatus.FAILED, null),
        ), now, zone, kind)
        assertEquals(100, s.totalBytes)
        assertEquals(1, s.completedCount)
        assertEquals(1, s.failedCount)
        assertEquals(100, s.averageBytes)
    }

    @Test fun `week has 7 bars ending today with the right day`() {
        val s = buildStats(listOf(
            item("a.zip", 100, DownloadStatus.COMPLETED, now),
            item("b.mp4", 300, DownloadStatus.COMPLETED, now - 2 * day),
            item("old.iso", 999, DownloadStatus.COMPLETED, now - 9 * day),   // hafte se bahar
        ), now, zone, kind)
        assertEquals(7, s.week.size)
        assertTrue(s.week.last().isToday)
        assertEquals("Wed", s.week.last().label)
        assertEquals(100, s.week[6].bytes)
        assertEquals(300, s.week[4].bytes)
        assertEquals(100, s.todayBytes)
        assertEquals(1399, s.totalBytes)   // total mein purani file bhi
    }

    @Test fun `types are sorted by size and fractions add up to one`() {
        val s = buildStats(listOf(
            item("a.zip", 100, DownloadStatus.COMPLETED, now),
            item("b.mp4", 300, DownloadStatus.COMPLETED, now),
            item("c.mp4", 100, DownloadStatus.COMPLETED, now),
        ), now, zone, kind)
        assertEquals("MP4", s.types.first().label)
        assertEquals(400, s.types.first().bytes)
        assertEquals(1f, s.types.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
    }

    @Test fun `empty list gives zeros not a crash`() {
        val s = buildStats(emptyList(), now, zone, kind)
        assertEquals(0, s.totalBytes); assertEquals(0, s.averageBytes); assertTrue(s.types.isEmpty()); assertEquals(7, s.week.size)
    }
}
