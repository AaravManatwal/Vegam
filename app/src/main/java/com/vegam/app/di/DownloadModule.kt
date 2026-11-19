package com.vegam.app.di

import android.content.Context
import com.vegam.core.download.DownloadRepository
import com.vegam.core.download.VegamGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt aur VegamGraph ka pul. WorkManager worker Hilt se nahi banta, isliye DB/scheduler ka asli
 * maalik VegamGraph hai; Hilt wahi same instances ViewModels ko deta hai (do DB instance kabhi nahi).
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides @Singleton
    fun graph(@ApplicationContext context: Context): VegamGraph = VegamGraph.get(context)

    @Provides @Singleton
    fun smart(graph: VegamGraph): com.vegam.core.download.SmartCoordinator = graph.smart

    @Provides @Singleton
    fun detectionDao(graph: VegamGraph): com.vegam.core.database.DetectionDao = graph.db.detections()

    @Provides @Singleton
    fun repository(graph: VegamGraph): DownloadRepository =
        DownloadRepository(graph.db, graph.scheduler, graph.live, graph.downloader, graph.settings, graph.saveLocation, graph.settingsStore)
}
