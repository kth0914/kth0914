package com.originos.globalizer.core

import com.originos.globalizer.shizuku.ShizukuShell

data class GoogleDiagnosticItem(
    val title: String,
    val status: Status,
    val detail: String
)

data class GoogleHealthReport(
    val items: List<GoogleDiagnosticItem>,
    val rawReport: String
)

class GoogleHealth(private val shell: ShizukuShell) {

    fun collect(): Result<GoogleHealthReport> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }

        val items = mutableListOf<GoogleDiagnosticItem>()
        val raw = StringBuilder()

        fun run(label: String, command: String): String {
            val result = shell.exec(command)
            val out = result.output.trim()
            raw.append("## ").append(label).append('\n')
                .append("exit=").append(result.exitCode).append('\n')
                .append(if (out.isBlank()) "(無輸出)" else out)
                .append("\n\n")
            return out
        }

        val gmsPath = run("GMS package", "pm path com.google.android.gms")
        val gmsEnabled = gmsPath.contains("package:")
        items += GoogleDiagnosticItem(
            "Google Play Services",
            if (gmsEnabled) Status.OK else Status.MISSING,
            if (gmsEnabled) "已安裝，可由 shell 正常查到" else "系統查不到 com.google.android.gms"
        )

        val gmsDoze = run(
            "GMS deviceidle",
            "dumpsys deviceidle whitelist | grep -F 'com.google.android.gms' || true"
        )
        items += GoogleDiagnosticItem(
            "FCM 推播基礎：Doze",
            if (gmsDoze.contains("com.google.android.gms")) Status.OK else Status.WARNING,
            if (gmsDoze.contains("com.google.android.gms")) {
                "Google Play Services 已在 Device Idle 白名單"
            } else {
                "GMS 尚未出現在 Device Idle 白名單；可能增加待機時推播延遲"
            }
        )

        val gmsBucket = run(
            "GMS standby bucket",
            "am get-standby-bucket com.google.android.gms 2>&1 || true"
        )
        items += GoogleDiagnosticItem(
            "FCM 推播基礎：Standby Bucket",
            if (gmsBucket.isNotBlank()) Status.OK else Status.UNKNOWN,
            if (gmsBucket.isBlank()) "系統沒有回傳 bucket" else "系統回傳：$gmsBucket"
        )

        val gmsOps = run(
            "GMS background appops",
            "cmd appops get com.google.android.gms RUN_IN_BACKGROUND 2>&1; " +
                "cmd appops get com.google.android.gms RUN_ANY_IN_BACKGROUND 2>&1"
        )
        val gmsOpsBlocked = gmsOps.contains("ignore", true) || gmsOps.contains("deny", true)
        items += GoogleDiagnosticItem(
            "FCM 推播基礎：背景執行",
            if (gmsOpsBlocked) Status.WARNING else Status.OK,
            if (gmsOpsBlocked) "GMS 的背景 AppOps 出現限制" else "沒有看到明確的背景 deny / ignore"
        )

        val googleAppPath = run(
            "Google app package",
            "pm path com.google.android.googlequicksearchbox"
        )
        items += GoogleDiagnosticItem(
            "Google App / Gemini 基礎",
            if (googleAppPath.contains("package:")) Status.OK else Status.MISSING,
            if (googleAppPath.contains("package:")) "Google App 已安裝" else "Google App 未安裝或不可見"
        )

        val googleAppDoze = run(
            "Google app deviceidle",
            "dumpsys deviceidle whitelist | grep -F 'com.google.android.googlequicksearchbox' || true"
        )
        items += GoogleDiagnosticItem(
            "Google App 背景存活",
            if (googleAppDoze.contains("com.google.android.googlequicksearchbox")) Status.OK else Status.WARNING,
            if (googleAppDoze.contains("com.google.android.googlequicksearchbox")) {
                "Google App 已在 Device Idle 白名單"
            } else {
                "Google App 未在 Device Idle 白名單"
            }
        )

        val gmailPath = run("Gmail package", "pm path com.google.android.gm 2>&1 || true")
        if (gmailPath.contains("package:")) {
            val gmailBucket = run(
                "Gmail standby bucket",
                "am get-standby-bucket com.google.android.gm 2>&1 || true"
            )
            val gmailOps = run(
                "Gmail background appops",
                "cmd appops get com.google.android.gm RUN_IN_BACKGROUND 2>&1; " +
                    "cmd appops get com.google.android.gm RUN_ANY_IN_BACKGROUND 2>&1"
            )
            val blocked = gmailOps.contains("ignore", true) || gmailOps.contains("deny", true)
            items += GoogleDiagnosticItem(
                "Gmail 背景狀態",
                if (blocked) Status.WARNING else Status.OK,
                "Standby bucket：${gmailBucket.ifBlank { "未知" }}；" +
                    if (blocked) "背景 AppOps 有限制" else "沒有看到明確背景限制"
            )
        }

        val mapsPath = run("Maps package", "pm path com.google.android.apps.maps 2>&1 || true")
        if (mapsPath.contains("package:")) {
            val mapsDoze = run(
                "Maps deviceidle",
                "dumpsys deviceidle whitelist | grep -F 'com.google.android.apps.maps' || true"
            )
            items += GoogleDiagnosticItem(
                "Google Maps / 時間軸背景",
                if (mapsDoze.contains("com.google.android.apps.maps")) Status.OK else Status.WARNING,
                if (mapsDoze.contains("com.google.android.apps.maps")) {
                    "Maps 已在 Device Idle 白名單"
                } else {
                    "Maps 未在 Device Idle 白名單；背景定位仍會受系統與定位權限共同影響"
                }
            )
        }

        val assistant = run(
            "secure assistant",
            "settings get --user 0 secure assistant 2>&1 || true"
        )
        val voiceInteraction = run(
            "voice_interaction_service",
            "settings get --user 0 secure voice_interaction_service 2>&1 || true"
        )
        val voiceRecognition = run(
            "voice_recognition_service",
            "settings get --user 0 secure voice_recognition_service 2>&1 || true"
        )
        val assistantRole = run(
            "assistant role",
            "cmd role get-role-holders --user 0 android.app.role.ASSISTANT 2>&1 || true"
        )

        val googleComponent = "com.google.android.googlequicksearchbox"
        val assistantIsGoogle = assistant.contains(googleComponent) || assistantRole.contains(googleComponent)
        items += GoogleDiagnosticItem(
            "預設助理",
            if (assistantIsGoogle) Status.OK else Status.WARNING,
            buildString {
                append("secure assistant：").append(assistant.ifBlank { "null/空" })
                append("\nROLE_ASSISTANT：").append(assistantRole.ifBlank { "空" })
            }
        )

        val voiceIsGoogle = voiceInteraction.contains(googleComponent)
        items += GoogleDiagnosticItem(
            "VoiceInteractionService",
            if (voiceIsGoogle) Status.OK else Status.WARNING,
            if (voiceIsGoogle) {
                "目前指向 Google VoiceInteractionService"
            } else {
                "目前不是 Google VoiceInteractionService：${voiceInteraction.ifBlank { "null/空" }}"
            }
        )

        val googleVisComponent = run(
            "Google VoiceInteraction component",
            "dumpsys package com.google.android.googlequicksearchbox 2>/dev/null | " +
                "grep -F 'com.google.android.voiceinteraction.GsaVoiceInteractionService' | head -n 5 || true"
        )
        items += GoogleDiagnosticItem(
            "Google VoiceInteraction 元件",
            if (googleVisComponent.isNotBlank()) Status.OK else Status.WARNING,
            if (googleVisComponent.isNotBlank()) "Google 的 GsaVoiceInteractionService 元件存在" else "未在 package dump 中找到 Google VoiceInteraction 元件"
        )

        val voiceDump = run(
            "voiceinteraction selected",
            "dumpsys voiceinteraction 2>/dev/null | " +
                "grep -E 'mCur|Interactor|VoiceInteraction|com\\.google|com\\.vivo|jovi' | head -n 80 || true"
        )

        items += GoogleDiagnosticItem(
            "Hey Google / OK Google",
            Status.WARNING,
            if (voiceIsGoogle && googleVisComponent.isNotBlank()) {
                "軟體層已具備 Google VoiceInteraction 基礎，但這仍不能證明 OEM DSP / hotword provider 已允許常駐語音喚醒；需再看實機行為。"
            } else {
                "目前連 VoiceInteraction 基礎都還沒有完全指向 Google，因此先不建議直接改 hotword。"
            }
        )

        raw.append("## Summary-safe metadata\n")
            .append("assistant=").append(assistant).append('\n')
            .append("voice_interaction_service=").append(voiceInteraction).append('\n')
            .append("voice_recognition_service=").append(voiceRecognition).append('\n')
            .append("assistant_role=").append(assistantRole).append('\n')
            .append("voiceinteraction_filtered=").append(voiceDump).append('\n')

        GoogleHealthReport(items, raw.toString().trim())
    }
}
