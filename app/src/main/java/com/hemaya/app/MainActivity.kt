package com.hemaya.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        HemayaScreen(this@MainActivity)
                    }
                }
            }
        }
    }
}

private fun a11yEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val cn = ComponentName(ctx, GuardAccessibilityService::class.java)
    return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString())
}

@Composable
fun HemayaScreen(act: Activity) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val t = tick

    val dpm = act.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(act, AdminReceiver::class.java)

    val a11y = a11yEnabled(act)
    val adminOn = dpm.isAdminActive(admin)
    val secure = DnsGuard.canWrite(act)
    val dnsOk = DnsGuard.isEnforced(act)
    val protecting = Prefs.isProtecting(act)
    val remaining = Prefs.disableRemainingMs(act)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("حماية", style = MaterialTheme.typography.headlineLarge)
        Text(
            if (protecting) "حالة الحماية: مفعّلة" else "حالة الحماية: غير مفعّلة",
            style = MaterialTheme.typography.titleMedium
        )

        StepCard(
            title = "١. خدمة إمكانية الوصول",
            desc = "تحجب شاشة DNS الخاص وشاشات حذف أو إيقاف التطبيق.",
            ok = a11y
        ) {
            if (!a11y) {
                Button(onClick = {
                    act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("فتح إعدادات إمكانية الوصول") }
            }
        }

        StepCard(
            title = "٢. مشرف الجهاز",
            desc = "يمنع حذف التطبيق مباشرة.",
            ok = adminOn
        ) {
            if (!adminOn) {
                Button(onClick = {
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "لمنع حذف تطبيق حماية."
                        )
                    act.startActivity(i)
                }) { Text("تفعيل مشرف الجهاز") }
            }
        }

        StepCard(
            title = "٣. صلاحية قفل DNS (اختياري)",
            desc = "تسمح للتطبيق بإرجاع DNS الخاص تلقائياً إذا غيّرته. تُمنح مرة واحدة عبر Shizuku.",
            ok = secure
        ) {
            if (!secure) {
                if (!ShizukuHelper.available()) {
                    Text("شغّل تطبيق Shizuku أولاً (Wireless debugging) ثم ارجع هنا.")
                } else if (!ShizukuHelper.hasPermission()) {
                    Button(onClick = { ShizukuHelper.requestPermission() }) {
                        Text("طلب إذن Shizuku")
                    }
                } else {
                    Button(onClick = {
                        val ok = ShizukuHelper.grantSecureSettings(act.packageName)
                        Toast.makeText(
                            act,
                            if (ok) "تم منح الصلاحية" else "فشل منح الصلاحية",
                            Toast.LENGTH_LONG
                        ).show()
                    }) { Text("منح الصلاحية") }
                }
            }
        }

        StepCard(
            title = "٤. DNS الخاص",
            desc = if (dnsOk) "مثبّت على ${DnsGuard.HOST}" else "غير مثبّت على ${DnsGuard.HOST}",
            ok = dnsOk
        ) {
            if (!dnsOk && secure) {
                Button(onClick = { DnsGuard.enforce(act) }) { Text("تثبيت الآن") }
            }
        }

        if (!protecting) {
            Button(
                onClick = {
                    Prefs.enable(act)
                    DnsGuard.enforce(act)
                },
                enabled = a11y && adminOn,
                modifier = Modifier.fillMaxWidth()
            ) { Text("تفعيل الحماية") }
            if (!(a11y && adminOn)) {
                Text("فعّل الخطوتين ١ و٢ أولاً.")
            }
        } else if (remaining == null) {
            Button(
                onClick = { Prefs.requestDisable(act) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("طلب إيقاف الحماية (بعد ٣٠ دقيقة)") }
        } else {
            val mins = (remaining / 60000L) + 1
            Text("ستتوقف الحماية بعد حوالي $mins دقيقة.")
            Button(
                onClick = { Prefs.cancelDisable(act) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("إلغاء طلب الإيقاف") }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    desc: String,
    ok: Boolean,
    actions: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                (if (ok) "✅ " else "⬜ ") + title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(desc)
            actions()
        }
    }
}
