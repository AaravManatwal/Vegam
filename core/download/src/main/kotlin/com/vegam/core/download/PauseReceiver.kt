package com.vegam.core.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Notification ke Pause button ka kaam: scheduler.pause(id). */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()   // receiver ko DB kaam khatam hone tak zinda rakho
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { VegamGraph.get(context).scheduler.pause(id) } finally { pending.finish() }
        }
    }

    companion object { const val EXTRA_ID = "download_id" }
}
