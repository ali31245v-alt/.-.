package com.hemaya.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object Prefs {
    const val UNLOCK_MS = 5L * 60L * 1000L
    private fun sp(c: Context) = c.getSharedPreferences("hemaya", Context.MODE_PRIVATE)
    fun isProtecting(c: Context): Boolean = sp(c).getBoolean("enabled", false)

    fun enable(c: Context) {
        sp(c).edit().putBoolean("enabled", true).putLong("unlock_until", 0L).apply()
        DnsGuard.enforce(c)
        DnsWatcher.start(c)
        WatchdogJobService.schedule(c)
        KeepAliveService.start(c)
    }

    fun disable(c: Context) {
        sp(c).edit().putBoolean("enabled", false).putLong("unlock_until", 0L).apply()
        DnsWatcher.stop(c)
        KeepAliveService.stop(c)
        c.getSystemService(android.app.job.JobScheduler::class.java)?.cancel(42107)
    }

    fun hasPin(c: Context): Boolean = sp(c).getString("pin_hash", null) != null
    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val h = hash(salt, pin)
        sp(c).edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(h, Base64.NO_WRAP))
            .apply()
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val s = sp(c)
        val saltS = s.getString("pin_salt", null) ?: return false
        val hashS = s.getString("pin_hash", null) ?: return false
        val h = hash(Base64.decode(saltS, Base64.NO_WRAP), pin)
        return MessageDigest.isEqual(h, Base64.decode(hashS, Base64.NO_WRAP))
    }

    private fun hash(salt: ByteArray, pin: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8))
    }
    fun unlockFor(c: Context) {
        sp(c).edit().putLong("unlock_until", System.currentTimeMillis() + UNLOCK_MS).apply()
    }
    fun isUnlocked(c: Context): Boolean =
        System.currentTimeMillis() < sp(c).getLong("unlock_until", 0L)
}
