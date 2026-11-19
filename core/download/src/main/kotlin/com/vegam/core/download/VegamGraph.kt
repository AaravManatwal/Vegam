package com.vegam.core.download

import android.content.Context
import com.vegam.core.database.VegamDatabase
import com.vegam.engine.SegmentedDownloader
import com.vegam.engine.TokenBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** DataStore ki settings ka live copy: worker/scheduler (Hilt ke bina) har baar yahan se padhte hain. */
class DownloadSettings {
    @Volatile var maxParallel: Int = 3
    @Volatile var defaultSegments: Int = 8
    @Volatile var chargingOnly: Boolean = false
    @Volatile var wifiOnlyDefault: Boolean = false
}

/**
 * Chhota service locator. Hilt Phase 3 mein aayega (jab ViewModels bante hain); tab tak
 * Worker ko dependencies yahin se milti hain, kyunki WorkManager workers ko khud banata hai.
 */
class VegamGraph private constructor(context: Context) {
    val db: VegamDatabase = VegamDatabase.create(context)
    val settings = DownloadSettings()
    val live = LiveProgress()
    val saveLocation = SaveLocationStore(context)
    val settingsStore = SettingsStore(context)
    /** Global speed limit: saari downloads ek hi bucket share karti hain. 0 = unlimited. */
    val rateLimiter = TokenBucket(0)
    /** Ek hi client = ek connection pool saari downloads ke liye. */
    val downloader = SegmentedDownloader(rateLimiter = rateLimiter)
    val scheduler = DownloadScheduler(context.applicationContext, db, settings)
    private val smartScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Smart Detection: link jaanch + history. Download wahi purane scheduler se hota hai. */
    val smart = SmartCoordinator(
        context.applicationContext, db, settingsStore, smartScope,
        enqueue = { url, name -> scheduler.enqueue(url, name, 1, settings.wifiOnlyDefault, null, null, saveLocation.treeUri) },
    )

    /**
     * DataStore badle to engine ko turant batao: speed limit, max parallel (queue dobara pump), charging.
     * App start par ek baar bulao; scope app ki zindagi tak.
     */
    fun bindSettings(scope: CoroutineScope) {
        scope.launch {
            var lastParallel = -1
            settingsStore.flow.collect { s ->
                settings.maxParallel = s.maxParallel
                settings.defaultSegments = s.defaultSegments
                settings.chargingOnly = s.chargingOnly
                settings.wifiOnlyDefault = s.wifiOnlyDefault
                rateLimiter.setRate(s.speedLimitBytesPerSec)
                // max parallel badhne par queue ke ruke items turant chalne chahiye (ghatne par koi preempt nahi).
                if (lastParallel != -1 && s.maxParallel != lastParallel) scheduler.pump()
                lastParallel = s.maxParallel
            }
        }
    }

    companion object {
        @Volatile private var instance: VegamGraph? = null

        fun get(context: Context): VegamGraph = instance ?: synchronized(this) {
            instance ?: VegamGraph(context.applicationContext).also { instance = it }
        }

        /** App-specific folder: permission nahi chahiye, uninstall par saaf. SAF folder Phase 3 mein. */
        fun downloadDir(context: Context): File =
            (context.getExternalFilesDir("Vegam") ?: File(context.filesDir, "Vegam")).also { it.mkdirs() }
    }
}
