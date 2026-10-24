package com.hemaya.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "إيقاف مشرف الجهاز يسمح بحذف تطبيق حماية. هل أنت متأكد؟"
}
