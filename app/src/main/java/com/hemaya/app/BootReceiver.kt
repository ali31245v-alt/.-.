package com.hemaya.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isProtecting(context)) return
        DnsWatcher.start(context)
        Watchdog.heal(context)
        WatchdogJobService.schedule(context)
        KeepAliveService.start(context)
    }
}
