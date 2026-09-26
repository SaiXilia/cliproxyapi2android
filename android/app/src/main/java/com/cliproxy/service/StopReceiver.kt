package com.cliproxy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val stopIntent = Intent(context, CLIProxyForegroundService::class.java).apply {
            action = CLIProxyForegroundService.ACTION_STOP
        }
        context.startService(stopIntent)
    }
}
