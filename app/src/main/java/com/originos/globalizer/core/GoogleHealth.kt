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

        fun bucketDescription(value: String): String {
            val number = value.trim().toIntOrNull()
            return when {
                number == null -> value.ifBlank { "未知" }
                number <= 10 -> "$number（≤10，沒有 App Standby 節流）"
                number <= 20 -> "$number（Working Set 級別）"
                number <= 30 -> "$number（Frequent 級別）"
                number <= 40 -> "$number（Rare 級別）"
                else -> "$number（高限制級別）"
            }
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
            "系統回傳：${bucketDescription(gmsBucket)}"
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
            if (gmsOpsBlocked) "GMS 的背景 AppOps 出現限制" else "沒有看到背景 deny / ignore，預設為 allow"
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
                "Standby：${bucketDescription(gmailBucket)}；" +
                    if (blocked) "背景 AppOps 有限制" else "背景 AppOps 沒有看到限制"
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
        val assistantIsGoogle =
            assistant.contains(googleComponent) && assistantRole.contains(googleComponent)
        val voiceIsGoogle = voiceInteraction.contains(googleComponent)

        items += GoogleDiagnosticItem(
            "Google Assistant 框架",
            if (assistantIsGoogle && voiceIsGoogle) Status.OK else Status.WARNING,
            if (assistantIsGoogle && voiceIsGoogle) {
                "ASSISTANT、ROLE_ASSISTANT、VoiceInteractionService 都已由 Google 接管"
            } else {
                "Assistant / Role / VoiceInteraction 尚未全部指向 Google"
            }
        )

        items += GoogleDiagnosticItem(
            "系統預設 SpeechRecognizer",
            Status.UNKNOWN,
            if (voiceRecognition.contains("com.vivo", true)) {
                "目前是 vivo：$voiceRecognition。這是一般語音辨識的預設服務，與 VoiceInteraction hotword 並非同一層；本版不自動修改。"
            } else {
                "目前為：${voiceRecognition.ifBlank { "null/空" }}"
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
            if (googleVisComponent.isNotBlank()) {
                "GsaVoiceInteractionService 存在"
            } else {
                "未在 package dump 中找到 GsaVoiceInteractionService"
            }
        )

        val voiceDump = run(
            "voiceinteraction selected",
            "dumpsys voiceinteraction 2>/dev/null | " +
                "grep -E 'mCur|Interactor|VoiceInteraction|Recognition service|Hotword detection service|mBound|com\\\\.google|com\\\\.vivo|jovi' | head -n 120 || true"
        )

        val googleHotwordService =
            voiceDump.contains("GsaHotwordDetectionService") && voiceDump.contains("mBound=true")
        items += GoogleDiagnosticItem(
            "Google Hotword Detection Service",
            if (googleHotwordService) Status.OK else Status.WARNING,
            if (googleHotwordService) {
                "Google GsaHotwordDetectionService 已綁定（mBound=true）"
            } else {
                "尚未看到 Google hotword detection service 處於已綁定狀態"
            }
        )

        val hotwordPermissions = run(
            "Google hotword permissions",
            "dumpsys package com.google.android.googlequicksearchbox 2>/dev/null | " +
                "grep -E 'RECORD_AUDIO|CAPTURE_AUDIO_HOTWORD|MANAGE_VOICE_KEYPHRASES|SOUND_TRIGGER_RUN_IN_BATTERY_SAVER|HOTWORD' | head -n 160 || true"
        )
        val recordAudioGranted =
            hotwordPermissions.contains("RECORD_AUDIO") && hotwordPermissions.contains("granted=true")
        items += GoogleDiagnosticItem(
            "Google App 麥克風 / Hotword 權限",
            if (recordAudioGranted) Status.OK else Status.WARNING,
            if (recordAudioGranted) {
                "Google App 的 package dump 中可看到 RECORD_AUDIO 已授權；其餘 hotword 權限請看診斷報告。"
            } else {
                "未能從 package dump 明確確認 RECORD_AUDIO granted=true；請查看診斷報告中的權限行。"
            }
        )

        val hotwordState = run(
            "Hotword detector state",
            "dumpsys voiceinteraction 2>/dev/null | " +
                "grep -iE 'hotword|keyphrase|sound.?trigger|detector|always.?on|dsp|software|enroll|model|mBound' | head -n 260 || true"
        )

        val soundTriggerServices = run(
            "SoundTrigger services",
            "service list 2>/dev/null | grep -iE 'soundtrigger|voiceinteraction' || true"
        )

        val soundTriggerDump = run(
            "SoundTrigger middleware",
            "(dumpsys soundtrigger_middleware 2>&1 || true; dumpsys soundtrigger 2>&1 || true) | head -n 260"
        )

        val hasDspSignal =
            hotwordState.contains("dsp", true) ||
                soundTriggerDump.contains("module", true) ||
                soundTriggerDump.contains("soundtrigger", true)

        items += GoogleDiagnosticItem(
            "SoundTrigger / DSP 層",
            if (hasDspSignal) Status.OK else Status.UNKNOWN,
            if (hasDspSignal) {
                "系統有回傳 SoundTrigger / DSP 相關資訊；是否已載入 Google keyphrase model 仍需看原始診斷內容。"
            } else {
                "沒有取得足夠的 SoundTrigger / DSP 資訊；這一層可能由 vivo HAL 隱藏或未提供給 shell dump。"
            }
        )

        items += GoogleDiagnosticItem(
            "Hey Google / OK Google 判斷",
            when {
                !assistantIsGoogle || !voiceIsGoogle -> Status.WARNING
                !googleHotwordService -> Status.WARNING
                else -> Status.UNKNOWN
            },
            when {
                !assistantIsGoogle || !voiceIsGoogle ->
                    "Google 尚未完全接管 Assistant framework。"
                !googleHotwordService ->
                    "Google Assistant 已接管，但 HotwordDetectionService 尚未正常綁定。"
                else ->
                    "Android framework 與 Google hotword service 都已就緒。若實際仍無法用 Hey Google 喚醒，嫌疑集中在 keyphrase enrollment、SoundTrigger/DSP 或 vivo vendor hotword policy，而不是預設助理設定。"
            }
        )

        raw.append("## Summary-safe metadata\n")
            .append("assistant=").append(assistant).append('\n')
            .append("voice_interaction_service=").append(voiceInteraction).append('\n')
            .append("voice_recognition_service=").append(voiceRecognition).append('\n')
            .append("assistant_role=").append(assistantRole).append('\n')
            .append("voiceinteraction_filtered=").append(voiceDump).append('\n')
            .append("hotword_state_filtered=").append(hotwordState).append('\n')
            .append("soundtrigger_services=").append(soundTriggerServices).append('\n')

        GoogleHealthReport(items, raw.toString().trim())
    }

    fun showAssistantSession(): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }
        val result = shell.exec("cmd voiceinteraction show")
        check(result.ok) { "系統喚起 Assistant 失敗：\n${result.diagnostic()}" }
        "已要求 Android VoiceInteractionManager 顯示目前的預設 Assistant session。"
    }

    fun restartHotwordDetection(): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }
        val result = shell.exec("cmd voiceinteraction restart-detection")
        check(result.ok) { "重啟 Hotword detection 失敗：\n${result.diagnostic()}" }
        "已要求 Android VoiceInteractionManager 重新啟動 Hotword Detection Service。這不會修改永久設定。"
    }
}
