package com.hemaya.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class KeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            if (!Prefs.isProtecting(this@KeepAliveService)) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            Watchdog.heal(this@KeepAliveService)
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "حماية DNS", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("حماية تعمل")
            .setContentText("فحص DNS مفعّل أثناء تشغيل الحماية")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        handler.post(check)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.isProtecting(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        DnsWatcher.start(this)
        WatchdogJobService.schedule(this)
        Watchdog.heal(this)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "hemaya_service"
        private const val NOTIFICATION_ID = 1
        fun start(ctx: Context) {
            if (!Prefs.isProtecting(ctx)) return
            val intent = Intent(ctx, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
                else ctx.startService(intent)
            } catch (_: Throwable) {}
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }
    }
}
