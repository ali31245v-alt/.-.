package com.hemaya.app

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    fun available(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(100)
        } catch (e: Throwable) {
        }
    }

    /** يمنح التطبيق صلاحية WRITE_SECURE_SETTINGS عبر Shizuku. */
    fun grantSecureSettings(pkg: String): Boolean = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(
            null,
            arrayOf("sh", "-c", "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"),
            null,
            null
        ) as Process
        p.waitFor() == 0
    } catch (e: Throwable) {
        false
    }
}
