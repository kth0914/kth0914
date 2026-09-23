package com.originos.globalizer.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class GoogleInspector(private val context: Context) {

    private data class App(val title: String, val pkg: String)

    private val apps = listOf(
        App("Google Play Services", "com.google.android.gms"),
        App("Google Services Framework", "com.google.android.gsf"),
        App("Google Play Store", "com.android.vending"),
        App("Google App", "com.google.android.googlequicksearchbox"),
        App("Gemini", "com.google.android.apps.bard"),
        App("Google Maps", "com.google.android.apps.maps"),
        App("Android Auto", "com.google.android.projection.gearhead"),
        App("Google Wallet", "com.google.android.apps.walletnfcrel"),
        App("Contacts Sync", "com.google.android.syncadapters.contacts"),
        App("Calendar Sync", "com.google.android.syncadapters.calendar")
    )

    fun inspect(): DeviceReport {
        val pm = context.packageManager
        val checks = apps.map { app ->
            val info = runCatching {
                pm.getApplicationInfo(app.pkg, PackageManager.ApplicationInfoFlags.of(0))
            }.getOrNull()
            when {
                info == null -> ServiceCheck(app.title, app.pkg, Status.MISSING, "未安裝或系統不可見")
                !info.enabled -> ServiceCheck(app.title, app.pkg, Status.WARNING, "已安裝，但目前停用")
                else -> ServiceCheck(app.title, app.pkg, Status.OK, "已安裝並啟用")
            }
        }

        val origin = listOf(
            "ro.vivo.os.version",
            "ro.vivo.os.build.display.id",
            "ro.build.display.id"
        ).firstNotNullOfOrNull { key -> systemProperty(key) } ?: "未知"

        return DeviceReport(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            originOsVersion = origin,
            checks = checks
        )
    }

    private fun systemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}
