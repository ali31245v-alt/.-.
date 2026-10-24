package com.hemaya.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object DnsGuard {
    const val HOST = "family.adguard-dns.com"

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnforced(ctx: Context): Boolean {
        val cr = ctx.contentResolver
        val mode = Settings.Global.getString(cr, "private_dns_mode")
        val spec = Settings.Global.getString(cr, "private_dns_specifier")
        return mode == "hostname" && spec == HOST
    }

    /** يثبّت DNS الخاص. يرجع true إذا كان مثبتاً بعد الاستدعاء. */
    fun enforce(ctx: Context): Boolean {
        if (isEnforced(ctx)) return true
        if (!canWrite(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            Settings.Global.putString(cr, "private_dns_specifier", HOST)
            Settings.Global.putString(cr, "private_dns_mode", "hostname")
            isEnforced(ctx)
        } catch (e: Exception) {
            false
        }
    }
}
