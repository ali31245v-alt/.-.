package com.hemaya.app

import android.view.accessibility.AccessibilityNodeInfo

/** يفحص شريط العنوان وخانات الكتابة بالمتصفحات فقط (لا يقرأ محتوى الصفحات). */
object WebGuard {

    val BROWSERS: Set<String> = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.mi.globalbrowser",
        "com.android.browser",
        "com.kiwibrowser.browser"
    )

    /** كلمات ودومينات محجوبة. عدّل القائمة كما تريد (بحروف صغيرة، وبدون تشكيل بالعربي). */
    private val TERMS: List<String> = listOf(
        "porn", "xxx", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "hentai", "brazzers", "سكس", "اباحي", "اباحيه"
    )

    private val DIACRITICS = Regex("[\\u064B-\\u0652\\u0640]")

    private fun norm(s: String): String = s.lowercase()
        .replace(DIACRITICS, "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')

    private val NORMED: List<String> = TERMS.map { norm(it) }

    /** يرجع الكلمة المطابقة، أو null إذا ما في شي محجوب. */
    fun matches(root: AccessibilityNodeInfo): String? {
        val fields = ArrayList<String>()
        gather(root, fields, 0)
        for (f in fields) {
            val t = norm(f)
            val hit = NORMED.firstOrNull { t.contains(it) }
            if (hit != null) return hit
        }
        return null
    }

    private fun gather(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (n == null || depth > 30 || out.size > 40) return
        val id = n.viewIdResourceName ?: ""
        val isField = n.isEditable ||
            id.contains("url_bar") ||
            id.contains("search_box") ||
            id.contains("url_field") ||
            id.contains("mozac_browser_toolbar_url")
        if (isField) {
            n.text?.let { out.add(it.toString()) }
        }
        for (i in 0 until n.childCount) {
            gather(n.getChild(i), out, depth + 1)
        }
    }
}
