package com.originos.globalizer.core

object TweakCatalog {
    val overseasMode = listOf(
        Tweak(
            id = "gms-doze-whitelist",
            title = "Google Play Services 背景保活",
            description = "將 GMS 加入 Device Idle 白名單，改善推播延遲。",
            risk = Risk.LOW,
            readCommand = "dumpsys deviceidle whitelist | grep -F 'com.google.android.gms' || true",
            applyCommand = "cmd deviceidle whitelist +com.google.android.gms",
            restoreTemplate = "cmd deviceidle whitelist -com.google.android.gms",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY
        ),
        Tweak(
            id = "googleapp-doze-whitelist",
            title = "Google App 背景保活",
            description = "降低 Assistant / Gemini 背景被凍結的機率。",
            risk = Risk.LOW,
            readCommand = "dumpsys deviceidle whitelist | grep -F 'com.google.android.googlequicksearchbox' || true",
            applyCommand = "cmd deviceidle whitelist +com.google.android.googlequicksearchbox",
            restoreTemplate = "cmd deviceidle whitelist -com.google.android.googlequicksearchbox",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY
        ),
        Tweak(
            id = "maps-doze-whitelist",
            title = "Google Maps 背景保活",
            description = "有助於時間軸與背景定位持續運作，但仍受定位權限控制。",
            risk = Risk.LOW,
            readCommand = "dumpsys deviceidle whitelist | grep -F 'com.google.android.apps.maps' || true",
            applyCommand = "cmd deviceidle whitelist +com.google.android.apps.maps",
            restoreTemplate = "cmd deviceidle whitelist -com.google.android.apps.maps",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY
        )
    )

    val debloat = listOf(
        Tweak(
            id = "disable-vivo-browser",
            title = "停用 vivo 瀏覽器",
            description = "先嘗試標準 disable-user；若 X Fold5 的 OriginOS 拒絕，改用可逆的 App 暫停模式。",
            risk = Risk.MEDIUM,
            readCommand = "pm list packages -d com.vivo.browser | grep -F 'com.vivo.browser' || true",
            applyCommand = "pm disable-user --user 0 com.vivo.browser",
            restoreTemplate = "pm enable --user 0 com.vivo.browser",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY,
            fallbackApplyCommand = "pm suspend --user 0 com.vivo.browser",
            fallbackRestoreTemplate = "pm unsuspend --user 0 com.vivo.browser",
            fallbackLabel = "App 暫停模式"
        ),
        Tweak(
            id = "disable-vivo-appstore",
            title = "停用 vivo 應用商店",
            description = "先嘗試標準 disable-user；若 OriginOS 保護應用商店，改用可逆的 App 暫停模式。",
            risk = Risk.MEDIUM,
            readCommand = "pm list packages -d com.vivo.appstore | grep -F 'com.vivo.appstore' || true",
            applyCommand = "pm disable-user --user 0 com.vivo.appstore",
            restoreTemplate = "pm enable --user 0 com.vivo.appstore",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY,
            fallbackApplyCommand = "pm suspend --user 0 com.vivo.appstore",
            fallbackRestoreTemplate = "pm unsuspend --user 0 com.vivo.appstore",
            fallbackLabel = "App 暫停模式"
        )
    )
}
