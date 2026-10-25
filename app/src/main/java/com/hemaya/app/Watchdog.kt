package com.hemaya.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object Watchdog {

    private const val CHANNEL = "hemaya_alert"
    private const val ALERT_ID = 2

    /** يثبّت الـ DNS، وإذا انطفت خدمة الوصول ينبّهك بإشعار لتعيد تفعيلها بنفسك. */
    fun heal(ctx: Context) {
        val app = ctx.applicationContext
        if (!Prefs.isProtecting(app)) return
        DnsGuard.enforce(app)
        warnIfA11yOff(app)
    }

    fun a11yOn(ctx: Context): Boolean {
        val s = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(ctx, GuardAccessibilityService::class.java)
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString())
    }

    private fun warnIfA11yOff(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (a11yOn(ctx)) {
                nm.cancel(ALERT_ID)
                return
            }
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "تنبيه حماية", NotificationManager.IMPORTANCE_HIGH)
            )
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("حماية: خدمة إمكانية الوصول متوقفة")
                .setContentText("اضغط هنا لإعادة تفعيلها")
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            nm.notify(ALERT_ID, n)
        } catch (e: Throwable) {
        }
    }
}
