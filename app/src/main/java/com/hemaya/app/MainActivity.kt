package com.hemaya.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0B1220)
private val CardBg = Color(0xFF131D33)
private val Accent = Color(0xFF2DD4BF)
private val Good = Color(0xFF22C55E)
private val Bad = Color(0xFFEF4444)
private val Muted = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Bg,
                    surface = Bg,
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
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

/** يحوّل الأرقام العربية-الهندية إلى لاتينية ويبقي الأرقام فقط. */
private fun cleanDigits(v: String): String = v
    .map { if (it in '٠'..'٩') '0' + (it - '٠') else it }
    .filter { it in '0'..'9' }
    .joinToString("")
    .take(12)

@Composable
fun HemayaScreen(act: Activity) {
    var tick by remember { mutableIntStateOf(0) }
    var shizukuMsg by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val t = tick

    val clip = LocalClipboardManager.current
    val dpm = act.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(act, AdminReceiver::class.java)

    val a11y = a11yEnabled(act)
    val adminOn = dpm.isAdminActive(admin)
    val secure = DnsGuard.canWrite(act)
    val dnsOk = DnsGuard.isEnforced(act)
    val protecting = Prefs.isProtecting(act)
    val hasPin = Prefs.hasPin(act)

    val lastAt = GuardAccessibilityService.lastEventAt
    val aliveText = if (lastAt == 0L) {
        "الخدمة لسا ما استقبلت أي حدث."
    } else {
        "آخر حدث قبل ${(System.currentTimeMillis() - lastAt) / 1000} ثانية (${GuardAccessibilityService.lastPkg})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F766E), Bg)))
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛡️", fontSize = 56.sp)
                Text("حماية", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (protecting) "الحماية مفعّلة" else "الحماية غير مفعّلة",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (protecting) Good else Bad, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepCard("١", "خدمة إمكانية الوصول", "تحجب شاشة DNS الخاص وشاشات الحذف والإيقاف.", a11y) {
                if (!a11y) {
                    Note("إذا الخيار رمادي: افتح معلومات التطبيق ← النقاط الثلاث فوق ← «السماح بالإعدادات المقيّدة»، ثم فعّل الخدمة.")
                    Primary("فتح معلومات التطبيق") {
                        act.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + act.packageName)
                            )
                        )
                    }
                    Secondary("فتح إعدادات إمكانية الوصول") {
                        act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                } else {
                    Note(aliveText)
                }
            }

            StepCard("٢", "مشرف الجهاز", "يمنع حذف التطبيق مباشرة.", adminOn) {
                if (!adminOn) {
                    Primary("تفعيل مشرف الجهاز") {
                        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                            .putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "لمنع حذف تطبيق حماية."
                            )
                        act.startActivity(i)
                    }
                }
            }

            StepCard(
                "٣",
                "صلاحية قفل DNS",
                "بتخلي التطبيق يثبّت الـ DNS ويرجّعه تلقائياً. بتنمنح مرة وحدة عبر Shizuku.",
                secure
            ) {
                if (!secure) {
                    if (!ShizukuHelper.available()) {
                        Note("Shizuku مو شغّال. افتح تطبيق Shizuku وشغّله عبر Wireless debugging ثم ارجع.")
                    } else if (!ShizukuHelper.hasPermission()) {
                        Primary("طلب إذن Shizuku") { ShizukuHelper.requestPermission() }
                    } else {
                        Primary("منح الصلاحية") {
                            shizukuMsg = ShizukuHelper
                                .grantSecureSettings(act.packageName)
                                .ifEmpty { "تم منح الصلاحية ✓" }
                        }
                    }
                    if (shizukuMsg.isNotEmpty()) Note(shizukuMsg)
                    Note("بديل بدون Shizuku: ثبّت الـ DNS يدوياً باسم ${DnsGuard.HOST}، والحجب بيضل شغال.")
                    Secondary("نسخ اسم الـ DNS") { clip.setText(AnnotatedString(DnsGuard.HOST)) }
                    Secondary("فتح إعدادات الاتصال") {
                        act.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    }
                }
            }

            StepCard(
                "٤",
                "DNS الخاص",
                if (dnsOk) "مثبّت على ${DnsGuard.HOST}" else "غير مثبّت على ${DnsGuard.HOST}",
                dnsOk
            ) {
                if (!dnsOk && secure) {
                    Primary("تثبيت الآن") { DnsGuard.enforce(act) }
                }
            }

            StepCard(
                "٥",
                "رمز الحماية",
                if (hasPin) "الرمز مضبوط. بيُطلب لإيقاف الحماية ولحذف التطبيق."
                else "اضبط رمزاً من ٤ إلى ١٢ رقم. الأفضل يضبطه شخص تثق فيه.",
                hasPin
            ) {
                if (!hasPin) {
                    Primary("ضبط الرمز") { dialog = "set" }
                } else if (!protecting) {
                    Secondary("تغيير الرمز") { dialog = "change" }
                }
            }

            if (!protecting) {
                Primary("تفعيل الحماية", enabled = a11y && adminOn && hasPin) {
                    Prefs.enable(act)
                    DnsGuard.enforce(act)
                }
                if (!(a11y && adminOn && hasPin)) Note("فعّل الخطوات ١ و٢ و٥ أولاً.")
            } else {
                Primary("إيقاف الحماية (يحتاج الرمز)") { dialog = "disable" }
            }

            StepCard(
                "🔧",
                "التشخيص",
                "شغّله والحماية مطفية، افتح شاشة DNS الخاص بالإعدادات، ارجع وانسخ النص وابعته لي.",
                false
            ) {
                Primary(if (GuardAccessibilityService.diag) "إيقاف التشخيص" else "تشغيل التشخيص") {
                    GuardAccessibilityService.diag = !GuardAccessibilityService.diag
                }
                Note(aliveText)
                Note("آخر سبب حجب: " + GuardAccessibilityService.lastBlockReason.ifEmpty { "—" })
                Text(
                    GuardAccessibilityService.lastText.ifEmpty { "—" },
                    maxLines = 12,
                    color = Muted,
                    fontSize = 12.sp
                )
                Secondary("نسخ نص التشخيص") {
                    clip.setText(
                        AnnotatedString(
                            "pkg=" + GuardAccessibilityService.lastPkg +
                                "\nblock=" + GuardAccessibilityService.lastBlockReason +
                                "\ntext=" + GuardAccessibilityService.lastText
                        )
                    )
                }
            }
        }
    }

    when (dialog) {
        "set" -> PinDialog(
            "ضبط رمز الحماية",
            listOf("الرمز (٤ إلى ١٢ رقم)", "تأكيد الرمز"),
            { dialog = "" }
        ) { v ->
            if (v[0].length < 4) {
                "الرمز لازم يكون ٤ أرقام على الأقل"
            } else if (v[0] != v[1]) {
                "الرمزان غير متطابقين"
            } else {
                Prefs.setPin(act, v[0])
                dialog = ""
                null
            }
        }

        "change" -> PinDialog(
            "تغيير الرمز",
            listOf("الرمز الحالي", "الرمز الجديد", "تأكيد الرمز الجديد"),
            { dialog = "" }
        ) { v ->
            if (!Prefs.checkPin(act, v[0])) {
                "الرمز الحالي خاطئ"
            } else if (v[1].length < 4) {
                "الرمز الجديد لازم يكون ٤ أرقام على الأقل"
            } else if (v[1] != v[2]) {
                "الرمزان الجديدان غير متطابقين"
            } else {
                Prefs.setPin(act, v[1])
                dialog = ""
                null
            }
        }

        "disable" -> PinDialog(
            "إيقاف الحماية",
            listOf("أدخل الرمز"),
            { dialog = "" }
        ) { v ->
            if (!Prefs.checkPin(act, v[0])) {
                "الرمز خاطئ"
            } else {
                Prefs.disable(act)
                dialog = ""
                null
            }
        }

        else -> {}
    }
}

@Composable
private fun PinDialog(
    title: String,
    labels: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> String?
) {
    val values = remember {
        mutableStateListOf<String>().apply { repeat(labels.size) { add("") } }
    }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { v -> values[i] = cleanDigits(v) },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
                if (err.isNotEmpty()) Text(err, color = Bad, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = onConfirm(values.toList())
                if (r != null) err = r
            }) { Text("تأكيد", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = Muted) }
        }
    )
}

@Composable
private fun StepCard(
    num: String,
    title: String,
    desc: String,
    ok: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (ok) Good else Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (ok) "✓" else num, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                Text(desc, color = Muted, fontSize = 13.sp)
                content()
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Muted, fontSize = 13.sp)
}

@Composable
private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Secondary(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text, color = Accent) }
}
