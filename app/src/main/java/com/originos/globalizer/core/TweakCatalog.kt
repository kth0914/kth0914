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
            title = "vivo 瀏覽器（停用／限制）",
            description = "先嘗試完整停用；若 X Fold5 的 OriginOS root-only 保護拒絕，改限制背景執行並立即停止程序。",
            risk = Risk.MEDIUM,
            readCommand = "pm list packages -d com.vivo.browser | grep -F 'com.vivo.browser' || true",
            applyCommand = "pm disable-user --user 0 com.vivo.browser",
            restoreTemplate = "pm enable --user 0 com.vivo.browser",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY,
            fallbackReadCommand = "for op in RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND; do v=\$(cmd appops get com.vivo.browser \$op 2>/dev/null | grep \"\$op:\" | head -n 1 | cut -d: -f2 | cut -d';' -f1 | tr -d ' '); [ -n \"\$v\" ] || v=default; echo \"\$op=\$v\"; done",
            fallbackApplyCommand = "cmd appops set com.vivo.browser RUN_IN_BACKGROUND ignore && cmd appops set com.vivo.browser RUN_ANY_IN_BACKGROUND ignore && am force-stop --user 0 com.vivo.browser",
            fallbackRestoreTemplate = "cmd appops set com.vivo.browser RUN_IN_BACKGROUND {{RUN_IN_BACKGROUND}} && cmd appops set com.vivo.browser RUN_ANY_IN_BACKGROUND {{RUN_ANY_IN_BACKGROUND}}",
            fallbackLabel = "背景限制模式"
        ),
        Tweak(
            id = "disable-vivo-appstore",
            title = "vivo 應用商店（停用／限制）",
            description = "先嘗試完整停用；若 OriginOS 將 App Store 設為 root-only，改限制背景執行並立即停止程序。",
            risk = Risk.MEDIUM,
            readCommand = "pm list packages -d com.vivo.appstore | grep -F 'com.vivo.appstore' || true",
            applyCommand = "pm disable-user --user 0 com.vivo.appstore",
            restoreTemplate = "pm enable --user 0 com.vivo.appstore",
            restorePolicy = RestorePolicy.ONLY_IF_OLD_EMPTY,
            fallbackReadCommand = "for op in RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND; do v=\$(cmd appops get com.vivo.appstore \$op 2>/dev/null | grep \"\$op:\" | head -n 1 | cut -d: -f2 | cut -d';' -f1 | tr -d ' '); [ -n \"\$v\" ] || v=default; echo \"\$op=\$v\"; done",
            fallbackApplyCommand = "cmd appops set com.vivo.appstore RUN_IN_BACKGROUND ignore && cmd appops set com.vivo.appstore RUN_ANY_IN_BACKGROUND ignore && am force-stop --user 0 com.vivo.appstore",
            fallbackRestoreTemplate = "cmd appops set com.vivo.appstore RUN_IN_BACKGROUND {{RUN_IN_BACKGROUND}} && cmd appops set com.vivo.appstore RUN_ANY_IN_BACKGROUND {{RUN_ANY_IN_BACKGROUND}}",
            fallbackLabel = "背景限制模式"
        )
    )
}
