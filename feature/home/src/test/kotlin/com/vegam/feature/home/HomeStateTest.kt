package com.vegam.feature.home

import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeStateTest {
    private fun item(
        id: Long, name: String = "f$id.bin", status: DownloadStatus = DownloadStatus.QUEUED,
        pri: Int = 1, total: Long = 1_000, done: Long = 0, speed: Long = 0, completedAt: Long? = null,
    ) = DownloadItem(
        id, name, "https://x/$name", status, total, done, speed, pri, false, 8, null, createdAt = id, completedAt = completedAt,
    )

    @Test fun `running first then priority then oldest`() {
        val items = listOf(
            item(1, pri = 1), item(2, pri = 2), item(3, status = DownloadStatus.RUNNING, pri = 0), item(4, pri = 1),
        )
        assertEquals(listOf(3L, 2L, 1L, 4L), buildHomeState(items, "").active.map { it.id })
    }

    @Test fun `failed shows in active, cancelled is hidden`() {
        val items = listOf(item(1, status = DownloadStatus.FAILED), item(2, status = DownloadStatus.CANCELLED))
        val s = buildHomeState(items, "")
        assertEquals(listOf(1L), s.active.map { it.id })
        assertEquals(0, s.completed.size)
    }

    @Test fun `completed newest first`() {
        val items = listOf(
            item(1, status = DownloadStatus.COMPLETED, completedAt = 100),
            item(2, status = DownloadStatus.COMPLETED, completedAt = 300),
            item(3, status = DownloadStatus.COMPLETED, completedAt = 200),
        )
        assertEquals(listOf(2L, 3L, 1L), buildHomeState(items, "").completed.map { it.id })
    }

    @Test fun `search filters lists but not the stats`() {
        val items = listOf(
            item(1, name = "ubuntu.iso", status = DownloadStatus.COMPLETED, total = 500, done = 500),
            item(2, name = "movie.mkv", status = DownloadStatus.RUNNING, total = 900, done = 300, speed = 40),
        )
        val s = buildHomeState(items, "ubn")
        assertEquals(listOf(1L), s.completed.map { it.id })
        assertEquals(0, s.active.size)
        assertEquals(500L, s.stats.totalBytes)
        assertEquals(40L, s.stats.speedNow)
        assertEquals(800L, s.stats.storageBytes)
    }

    @Test fun `cancelled partial bytes do not count as storage`() {
        val items = listOf(item(1, status = DownloadStatus.CANCELLED, done = 700), item(2, done = 100))
        assertEquals(100L, buildHomeState(items, "").stats.storageBytes)
    }
}
