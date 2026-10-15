package com.masselis.tpmsadvanced.feature.background.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.masselis.tpmsadvanced.core.common.appContext

internal class DisableMonitorBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MonitorService.stop(context)
    }

    internal companion object {
        fun intent() = Intent(appContext, DisableMonitorBroadcastReceiver::class.java)
    }
}
