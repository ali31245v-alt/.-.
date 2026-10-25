package com.hemaya.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class WatchdogJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (Prefs.isProtecting(this)) {
            DnsWatcher.start(this)
            Watchdog.heal(this)
            KeepAliveService.start(this)
        }
        params?.let { jobFinished(it, false) }
        return false
    }
    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 42107
        fun schedule(ctx: Context) {
            val scheduler = ctx.getSystemService(JobScheduler::class.java) ?: return
            val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, WatchdogJobService::class.java))
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000L)
                .build()
            scheduler.schedule(info)
        }
    }
}
