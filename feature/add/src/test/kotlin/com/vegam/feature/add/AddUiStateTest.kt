package com.vegam.feature.add

import com.vegam.engine.FileInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddUiStateTest {
    private val ok = AddUiState(url = "https://example.com/f.zip", name = "f.zip")
    private fun ready(ranges: Boolean) = MetaState.Ready(FileInfo(1_000_000, ranges, null, null))

    @Test fun `valid link and name can start`() = assertTrue(ok.canStart)
    @Test fun `bad link cannot start`() = assertFalse(ok.copy(url = "hello").canStart)
    @Test fun `empty name cannot start`() = assertFalse(ok.copy(name = " ").canStart)
    @Test fun `cannot start while metadata is loading`() = assertFalse(ok.copy(meta = MetaState.Loading).canStart)
    @Test fun `metadata error does not block start`() = assertTrue(ok.copy(meta = MetaState.Error("HEAD blocked")).canStart)

    @Test fun `range support follows the server`() {
        assertTrue(ok.rangeSupported)                                   // abhi pata nahi: slider chalne do
        assertTrue(ok.copy(meta = ready(true)).rangeSupported)
        assertFalse(ok.copy(meta = ready(false)).rangeSupported)
    }
}
