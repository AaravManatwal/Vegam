package com.vegam.core.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live speed (bytes/sec) jo DB mein save nahi hoti (har 500ms DB write bekaar hoti).
 * Worker `update` karta hai; UI is flow ko DB ke saath combine karti hai.
 * Saath mein har download ki pichhle 60 second ki speed (1 sample/second) Detail ke graph ke liye.
 */
class LiveProgress(private val sampleEveryMs: Long = 1_000L, private val maxSamples: Int = 60) {
    private val state = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val speeds: StateFlow<Map<Long, Long>> = state.asStateFlow()

    private val historyState = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val history: StateFlow<Map<Long, List<Long>>> = historyState.asStateFlow()
    private val lastSampleAt = HashMap<Long, Long>()   // update() alag threads se aa sakta hai: synchronized

    fun update(id: Long, bytesPerSec: Long, nowMs: Long = System.currentTimeMillis()) {
        state.update { it + (id to bytesPerSec) }
        synchronized(lastSampleAt) {
            val last = lastSampleAt[id]
            if (last != null && nowMs - last < sampleEveryMs) return
            lastSampleAt[id] = nowMs
        }
        historyState.update { map ->
            val list = (map[id] ?: emptyList()) + bytesPerSec
            map + (id to if (list.size > maxSamples) list.takeLast(maxSamples) else list)
        }
    }

    fun clear(id: Long) {
        state.update { it - id }
        // History rehne do: pause ke baad bhi graph dikhta rahe. Naya run shuru hone par purani chalti rahegi.
        synchronized(lastSampleAt) { lastSampleAt.remove(id) }
    }

    fun forget(id: Long) {
        clear(id)
        historyState.update { it - id }
    }
}
