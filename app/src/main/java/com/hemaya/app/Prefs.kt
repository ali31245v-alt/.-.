package com.hemaya.app

import android.content.Context

object Prefs {
    const val COOLDOWN_MS = 30L * 60L * 1000L

    private fun sp(c: Context) = c.getSharedPreferences("hemaya", Context.MODE_PRIVATE)

    /** الحماية فعّالة؟ وإذا انتهت مهلة طلب الإيقاف تتوقف هنا. */
    fun isProtecting(c: Context): Boolean {
        val s = sp(c)
        if (!s.getBoolean("enabled", false)) return false
        val req = s.getLong("disable_req", 0L)
        if (req > 0L && System.currentTimeMillis() - req >= COOLDOWN_MS) {
            s.edit().putBoolean("enabled", false).putLong("disable_req", 0L).apply()
            return false
        }
        return true
    }

    fun enable(c: Context) {
        sp(c).edit().putBoolean("enabled", true).putLong("disable_req", 0L).apply()
    }

    fun requestDisable(c: Context) {
        val s = sp(c)
        if (s.getLong("disable_req", 0L) == 0L) {
            s.edit().putLong("disable_req", System.currentTimeMillis()).apply()
        }
    }

    fun cancelDisable(c: Context) {
        sp(c).edit().putLong("disable_req", 0L).apply()
    }

    /** المدة المتبقية لإيقاف الحماية، أو null إذا لم يُطلب الإيقاف. */
    fun disableRemainingMs(c: Context): Long? {
        val req = sp(c).getLong("disable_req", 0L)
        if (req == 0L) return null
        return (COOLDOWN_MS - (System.currentTimeMillis() - req)).coerceAtLeast(0L)
    }
}
