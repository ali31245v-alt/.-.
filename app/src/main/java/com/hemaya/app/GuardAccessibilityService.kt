package com.hemaya.app

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 3000)
        }
    }

    private fun tick() {
        if (Prefs.isProtecting(this)) DnsGuard.enforce(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
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

        // 1) النقر على خانة DNS الخاص
        if (protecting &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            pkg in SETTINGS_PKGS
        ) {
            val clickParts = ArrayList<String>()
            event.text?.forEach { clickParts.add(it.toString()) }
            collect(event.source, clickParts, 0, 3)
            if (hasAny(norm(clickParts.joinToString(" ")), DNS_CLICK)) {
                block("نقر على خانة DNS")
                return
            }
        }

        val now = System.currentTimeMillis()
        val urgent = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!urgent && now - lastScan < 250) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val parts = ArrayList<String>()
        collect(root, parts, 0, 30)
        val text = norm(parts.joinToString(" | "))
        if (diag) lastText = text.take(1500)
        if (!protecting) return

        // 2) شاشة DNS الخاص نفسها
        if (pkg in SETTINGS_PKGS && hasAny(text, DNS_SCREEN)) {
            block("شاشة DNS الخاص")
            return
        }

        // 3) شاشات حذف/إيقاف/تعطيل تطبيق حماية
        if (text.contains(APP) && hasAny(text, DANGER)) {
            block("شاشة حذف/إيقاف حماية")
        }
    }

    private fun block(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlock < 500) return
        lastBlock = now
        lastBlockReason = reason
        performGlobalAction(GLOBAL_ACTION_HOME)
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
