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

    /** يمنح WRITE_SECURE_SETTINGS عبر Shizuku. يرجع نص فاضي عند النجاح، أو سبب الفشل. */
    fun grantSecureSettings(pkg: String): String {
        return try {
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
            val err = p.errorStream.bufferedReader().readText()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            if (code == 0) "" else "فشل (code=$code): ${err.trim()} ${out.trim()}"
        } catch (e: Throwable) {
            "فشل: " + e.javaClass.simpleName + " - " + (e.cause?.message ?: e.message)
        }
    }
}
