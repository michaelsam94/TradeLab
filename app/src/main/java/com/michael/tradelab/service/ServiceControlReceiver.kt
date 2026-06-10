package com.michael.tradelab.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Stop action on the position-monitor notification. */
class ServiceControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            PositionMonitorService.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP = "com.michael.tradelab.action.STOP_MONITOR"
    }
}
