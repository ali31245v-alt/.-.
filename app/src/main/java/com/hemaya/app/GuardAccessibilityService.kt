package com.hemaya.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private val DIACRITICS = Regex("[\\u064B-\\u0652\\u0640]")

private fun norm(s: String): String = s.lowercase()
    .replace(DIACRITICS, "")
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
    .replace('ى', 'ي')
    .replace('ة', 'ه')

class GuardAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastBlock = 0L
    private var lastScan = 0L
    private var overlay: View? = null
    private var pinOverlay: View? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1500)
        }
    }

    private fun tick() {
        if (Prefs.isProtecting(this)) Watchdog.heal(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        DnsWatcher.start(this)
        WatchdogJobService.schedule(this)
        if (Prefs.isProtecting(this)) KeepAliveService.start(this)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        hideOverlay()
        hidePin()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        lastPkg = pkg
        lastEventAt = System.currentTimeMillis()
        if (pkg == packageName || pkg !in WATCHED) return

        val protecting = Prefs.isProtecting(this)
        if (!protecting && !diag) return

        // 0) فحص فوري لنص الحدث نفسه قبل مسح الشاشة (شاشات DNS: حجب مطلق)
        if (protecting && pkg in SETTINGS_PKGS) {
            val quick = ArrayList<String>()
            event.text?.forEach { quick.add(it.toString()) }
            event.contentDescription?.let { quick.add(it.toString()) }
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                collect(event.source, quick, 0, 3)
            }
            val q = norm(quick.joinToString(" "))
            if (hasAny(q, DNS_CLICK) || hasAny(q, DNS_SCREEN)) {
                block("DNS (فحص سريع)")
                return
            }
        }

        val now = System.currentTimeMillis()
        val urgent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!urgent && now - lastScan < 150) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val parts = ArrayList<String>()
        collect(root, parts, 0, 30)
        val text = norm(parts.joinToString(" | "))
        if (diag) lastText = text.take(1500)
        if (!protecting) return

        // 1) شاشة DNS الخاص: حجب مطلق بدون رمز
        if (pkg in SETTINGS_PKGS && hasAny(text, DNS_SCREEN)) {
            block("شاشة DNS الخاص")
            return
        }

        // 2) شاشات حذف/إيقاف/تعطيل حماية: تحتاج الرمز
        if (text.contains(APP) && (hasAny(text, DANGER) || (pkg in SETTINGS_PKGS && hasAny(text, APPINFO)))) {
            if (!Prefs.isUnlocked(this)) {
                lastBlockReason = "طلب الرمز (حذف/إيقاف)"
                showPin()
            }
        }
    }

    private fun block(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlock < 300) return
        lastBlock = now
        lastBlockReason = reason
        showOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 120)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 350)
        handler.postDelayed({ hideOverlay() }, 1000)
    }

    private fun overlayParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE
    )

    private fun showOverlay() {
        if (overlay != null || pinOverlay != null) return
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val root = FrameLayout(this)
            root.setBackgroundColor(0xFF0B1220.toInt())
            val tv = TextView(this)
            tv.text = "🛡️ حماية\nهذه الشاشة محظورة"
            tv.textSize = 22f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(0xFFFFFFFF.toInt())
            root.addView(
                tv,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            wm.addView(root, overlayParams())
            overlay = root
        } catch (e: Throwable) {
            overlay = null
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Throwable) {
        }
    }

    /** لوحة أرقام فوق الشاشة لإدخال الرمز، بدون الحاجة للوحة مفاتيح النظام. */
    private fun showPin() {
        if (pinOverlay != null) return
        hideOverlay()
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val d = resources.displayMetrics.density
            val entered = StringBuilder()

            val root = LinearLayout(this)
            root.orientation = LinearLayout.VERTICAL
            root.gravity = Gravity.CENTER
            root.setBackgroundColor(0xFF0B1220.toInt())
            root.setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())

            val title = TextView(this)
            title.text = "🛡️ حماية\nأدخل الرمز للمتابعة"
            title.textSize = 20f
            title.gravity = Gravity.CENTER
            title.setTextColor(0xFFFFFFFF.toInt())
            root.addView(title)

            val dots = TextView(this)
            dots.text = " "
            dots.textSize = 28f
            dots.gravity = Gravity.CENTER
            dots.setTextColor(0xFF2DD4BF.toInt())
            dots.setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            root.addView(dots)

            val refresh = {
                dots.text = if (entered.isEmpty()) " " else "●".repeat(entered.length)
            }

            val submit = {
                if (Prefs.checkPin(this, entered.toString())) {
                    Prefs.unlockFor(this)
                    hidePin()
                } else {
                    entered.clear()
                    refresh()
                    title.text = "🛡️ حماية\nالرمز خاطئ، جرّب مرة ثانية"
                }
            }

            val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
            for (r in 0 until 4) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER
                for (c in 0 until 3) {
                    val k = labels[r * 3 + c]
                    val b = Button(this)
                    b.text = k
                    b.textSize = 22f
                    b.setOnClickListener {
                        when (k) {
                            "⌫" -> {
                                if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                                refresh()
                            }
                            "✓" -> submit()
                            else -> {
                                if (entered.length < 12) entered.append(k)
                                refresh()
                            }
                        }
                    }
                    row.addView(b, LinearLayout.LayoutParams((92 * d).toInt(), (64 * d).toInt()))
                }
                root.addView(row)
            }

            val cancel = Button(this)
            cancel.text = "إلغاء"
            cancel.setOnClickListener {
                hidePin()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            root.addView(
                cancel,
                LinearLayout.LayoutParams(
                    (200 * d).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            wm.addView(root, overlayParams())
            pinOverlay = root

            // إغلاق تلقائي بعد دقيقة حتى لا تبقى اللوحة فوق الشاشة
            handler.postDelayed({
                if (pinOverlay === root) {
                    hidePin()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, 60000)
        } catch (e: Throwable) {
            pinOverlay = null
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun hidePin() {
        val v = pinOverlay ?: return
        pinOverlay = null
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Throwable) {
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int, maxDepth: Int) {
        if (n == null || depth > maxDepth || out.size > 500) return
        n.text?.let { out.add(it.toString()) }
        n.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until n.childCount) {
            collect(n.getChild(i), out, depth + 1, maxDepth)
        }
    }

    private fun hasAny(text: String, keys: List<String>) = keys.any { text.contains(it) }

    companion object {
        @Volatile var diag: Boolean = false
        @Volatile var lastPkg: String = ""
        @Volatile var lastEventAt: Long = 0L
        @Volatile var lastText: String = ""
        @Volatile var lastBlockReason: String = ""

        private val SETTINGS_PKGS = setOf("com.android.settings", "com.miui.securitycenter")

        private val WATCHED = SETTINGS_PKGS + setOf(
            "com.miui.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.home"
        )

        private val APP = norm("حماية")
        private val APPINFO = listOf("app info", "معلومات التطبيق", "autostart", "التشغيل التلقائي").map(::norm)

        private val DNS_CLICK = listOf(
            "private dns", "dns الخاص", "dns خاص", "dns private"
        ).map(::norm)

        private val DNS_SCREEN = listOf(
            "hostname", "اسم مضيف", "اسم المضيف"
        ).map(::norm)

        private val DANGER = listOf(
            "uninstall", "إلغاء التثبيت", "الغاء التثبيت", "إزالة التثبيت",
            "force stop", "فرض الإيقاف", "فرض الايقاف", "إيقاف إجباري",
            "clear data", "مسح البيانات",
            "deactivate", "إلغاء التنشيط", "الغاء التفعيل",
            "device admin", "مشرف الجهاز", "مسؤول الجهاز", "تطبيقات مشرف",
            "accessibility", "إمكانية الوصول", "امكانية الوصول"
        ).map(::norm)
    }
}
