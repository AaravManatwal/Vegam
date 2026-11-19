package com.vegam.core.download

import com.vegam.core.database.DownloadEntity
import com.vegam.core.database.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadItemTest {
    private val row = DownloadEntity(
        id = 1, url = "https://x/f.iso", fileName = "f.iso", targetPath = "/t/f.iso",
        status = "RUNNING", totalBytes = 1_000, downloadedBytes = 250,
    )

    @Test fun `progress and eta come from bytes and live speed`() {
        val item = row.toItem(bytesPerSec = 50)
        assertEquals(0.25f, item.progress)
        assertEquals(15L, item.etaSeconds)   // 750 bytes baaki / 50 per sec
        assertTrue(item.isRunning)
    }

    @Test fun `no speed means no eta`() = assertNull(row.toItem(0).etaSeconds)

    @Test fun `unknown size means zero progress and no eta`() {
        val item = row.copy(totalBytes = 0).toItem(100)
        assertEquals(0f, item.progress)
        assertNull(item.etaSeconds)
    }

    @Test fun `unknown status string falls back to FAILED`() =
        assertEquals(DownloadStatus.FAILED, row.copy(status = "WAT").toItem(0).status)

    @Test fun `progress never exceeds 100 percent`() =
        assertEquals(1f, row.copy(downloadedBytes = 5_000).toItem(0).progress)
}
