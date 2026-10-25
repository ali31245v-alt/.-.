package com.hemaya.app

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Watches system DNS/accessibility settings while protection is enabled. */
object DnsWatcher {
    @Volatile private var observer: ContentObserver? = null

    @Synchronized fun start(ctx: Context) {
        if (observer != null) return
        val app = ctx.applicationContext
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (Prefs.isProtecting(app)) Watchdog.heal(app)
            }
        }
        observer = obs
        val cr: ContentResolver = app.contentResolver
        cr.registerContentObserver(Settings.Global.getUriFor("private_dns_mode"), false, obs)
        cr.registerContentObserver(Settings.Global.getUriFor("private_dns_specifier"), false, obs)
        cr.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, obs)
    }

    @Synchronized fun stop(ctx: Context) {
        val obs = observer ?: return
        try { ctx.applicationContext.contentResolver.unregisterContentObserver(obs) } catch (_: Throwable) {}
        observer = null
    }
}
