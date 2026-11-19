package com.vegam.app

import android.app.Application
import com.vegam.core.download.Notifications
import com.vegam.core.download.VegamGraph
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VegamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        com.vegam.core.download.SmartCoordinator.ensureChannel(this)
        // Process kill / reboot ke baad adhoori downloads wapas line mein.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val graph = VegamGraph.get(this)
        graph.bindSettings(scope)   // DataStore -> engine (speed limit, parallel)
        scope.launch { graph.scheduler.recover() }
    }
}
