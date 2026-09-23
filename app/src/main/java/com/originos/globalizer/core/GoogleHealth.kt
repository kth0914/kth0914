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

        fun permissionCheck(permission: String): Pair<Boolean, String> {
            val out = run(
                "Permission $permission",
                "dumpsys package check-permission $permission com.google.android.googlequicksearchbox 0 2>&1"
            )
            val granted = out.lineSequence().map { it.trim() }.any { it == "0" }
            return granted to out
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

        val googleSystemApp = run(
            "Google app system flag",
            "pm list packages -s com.google.android.googlequicksearchbox 2>&1 || true"
        )
        val isGoogleSystemApp = googleSystemApp.contains("com.google.android.googlequicksearchbox")
        items += GoogleDiagnosticItem(
            "Google App 安裝層級",
            if (isGoogleSystemApp) Status.OK else Status.WARNING,
            if (isGoogleSystemApp) {
                "Google App 具有 system app 標記"
            } else {
                "Google App 不是 system app。若 hotword 權限屬 signature/privileged，單靠使用者安裝版本通常拿不到。"
            }
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
                "目前是 vivo：$voiceRecognition。這是一般語音辨識預設服務，與 VoiceInteraction hotword 並非同一層；本版不自動修改。"
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
            if (googleVisComponent.isNotBlank()) "GsaVoiceInteractionService 存在"
            else "未在 package dump 中找到 GsaVoiceInteractionService"
        )

        val voiceDump = run(
            "voiceinteraction selected",
            "dumpsys voiceinteraction 2>/dev/null | " +
                "grep -E 'mCur|Interactor|VoiceInteraction|Recognition service|Hotword detection service|mBound|No Hotword detection connection|com\\\\.google|com\\\\.vivo|jovi' | head -n 140 || true"
        )

        val googleHotwordService =
            voiceDump.contains("GsaHotwordDetectionService") && voiceDump.contains("mBound=true")
        val noHotwordConnection = voiceDump.contains("No Hotword detection connection", true)

        items += GoogleDiagnosticItem(
            "Google Hotword Detection Service",
            when {
                !googleHotwordService -> Status.WARNING
                noHotwordConnection -> Status.WARNING
                else -> Status.OK
            },
            when {
                !googleHotwordService ->
                    "尚未看到 Google GsaHotwordDetectionService 正常綁定"
                noHotwordConnection ->
                    "GsaHotwordDetectionService 已綁定，但 Android 顯示 No Hotword detection connection：服務存在，detector 尚未真正建立。"
                else ->
                    "Google HotwordDetectionService 已綁定，而且存在 active detector connection"
            }
        )

        val perms = listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.MANAGE_HOTWORD_DETECTION",
            "android.permission.CAPTURE_AUDIO_HOTWORD",
            "android.permission.MANAGE_VOICE_KEYPHRASES",
            "android.permission.SOUND_TRIGGER_RUN_IN_BATTERY_SAVER"
        )
        val permissionResults = linkedMapOf<String, Boolean>()
        perms.forEach { p ->
            val (granted, _) = permissionCheck(p)
            permissionResults[p] = granted
        }

        val recordAudioGranted = permissionResults["android.permission.RECORD_AUDIO"] == true
        val manageHotwordGranted = permissionResults["android.permission.MANAGE_HOTWORD_DETECTION"] == true
        val captureHotwordGranted = permissionResults["android.permission.CAPTURE_AUDIO_HOTWORD"] == true
        val manageKeyphrasesGranted = permissionResults["android.permission.MANAGE_VOICE_KEYPHRASES"] == true
        val batterySaverGranted = permissionResults["android.permission.SOUND_TRIGGER_RUN_IN_BATTERY_SAVER"] == true

        val privilegedGrantedCount = listOf(
            manageHotwordGranted,
            captureHotwordGranted,
            manageKeyphrasesGranted,
            batterySaverGranted
        ).count { it }

        items += GoogleDiagnosticItem(
            "Google Hotword 核心權限",
            if (recordAudioGranted && manageHotwordGranted && captureHotwordGranted) Status.OK else Status.WARNING,
            buildString {
                append("RECORD_AUDIO=").append(if (recordAudioGranted) "granted" else "denied")
                append("\nMANAGE_HOTWORD_DETECTION=").append(if (manageHotwordGranted) "granted" else "denied")
                append("\nCAPTURE_AUDIO_HOTWORD=").append(if (captureHotwordGranted) "granted" else "denied")
                append("\nMANAGE_VOICE_KEYPHRASES=").append(if (manageKeyphrasesGranted) "granted" else "denied")
                append("\nSOUND_TRIGGER_RUN_IN_BATTERY_SAVER=").append(if (batterySaverGranted) "granted" else "denied")
            }
        )

        val hotwordState = run(
            "Hotword detector state",
            "dumpsys voiceinteraction 2>/dev/null | " +
                "grep -iE 'hotword|keyphrase|sound.?trigger|detector|always.?on|dsp|software|enroll|model|mBound|connection' | head -n 300 || true"
        )

        val soundTriggerServices = run(
            "SoundTrigger services",
            "service list 2>/dev/null | grep -iE 'soundtrigger|voiceinteraction' || true"
        )

        val soundTriggerSummary = run(
            "SoundTrigger ownership summary",
            "dumpsys soundtrigger_middleware 2>/dev/null | " +
                "grep -E 'Properties\\\\{|maxSoundModels|client: Identity|ACTIVE|PhraseSoundModel|text:|com\\\\.vivo\\\\.voicewakeup|com\\\\.google\\\\.android\\\\.googlequicksearchbox' | head -n 220 || true"
        )

        val vivoHardwareModelActive =
            soundTriggerSummary.contains("com.vivo.voicewakeup") &&
                soundTriggerSummary.contains("ACTIVE")
        val googleHardwareSession =
            soundTriggerSummary.contains("com.google.android.googlequicksearchbox")
        val multiModelCapable =
            Regex("maxSoundModels:\\\\s*([2-9]|[1-9][0-9]+)").containsMatchIn(soundTriggerSummary)

        items += GoogleDiagnosticItem(
            "Qualcomm SoundTrigger 硬體模型",
            when {
                googleHardwareSession -> Status.OK
                vivoHardwareModelActive -> Status.WARNING
                else -> Status.UNKNOWN
            },
            buildString {
                if (vivoHardwareModelActive) {
                    append("目前觀察到 vivo voicewakeup 的 ACTIVE phrase model。")
                } else {
                    append("目前沒看到 vivo ACTIVE phrase model。")
                }
                append("\n")
                if (googleHardwareSession) {
                    append("已看到 Google 連入 SoundTrigger middleware。")
                } else {
                    append("尚未看到 Google 的 SoundTrigger hardware session / phrase model。")
                }
                if (multiModelCapable) {
                    append("\nHAL 宣告可同時容納多個 sound model，因此 vivo 模型存在本身不等於證明它獨佔 DSP。")
                }
            }
        )

        val likelyPrivilegedPermissionBlock =
            !isGoogleSystemApp &&
                privilegedGrantedCount == 0 &&
                recordAudioGranted &&
                noHotwordConnection &&
                !googleHardwareSession

        items += GoogleDiagnosticItem(
            "Hey Google / OK Google 綜合判斷",
            when {
                !assistantIsGoogle || !voiceIsGoogle -> Status.WARNING
                likelyPrivilegedPermissionBlock -> Status.WARNING
                noHotwordConnection -> Status.WARNING
                googleHardwareSession -> Status.OK
                else -> Status.UNKNOWN
            },
            when {
                !assistantIsGoogle || !voiceIsGoogle ->
                    "Google 尚未完全接管 Assistant framework。"
                likelyPrivilegedPermissionBlock ->
                    "Google 已是預設 Assistant，也有麥克風權限，但 Google App 不是 system app、Hotword privileged permissions 未授權，且沒有建立 detector / hardware session。這非常像陸版 ROM 缺少系統級 hotword 授權，而不是一般設定問題。"
                noHotwordConnection ->
                    "Google Hotword service 已存在，但 detector connection 尚未建立；先到 Google Voice Match / Hotword 設定完成啟用與語音模型註冊。"
                googleHardwareSession ->
                    "已看到 Google hardware SoundTrigger session；若仍無法喚醒，下一步再檢查 keyphrase enrollment 與 vendor policy。"
                else ->
                    "Assistant framework 正常，但目前證據不足以確認硬體 hotword 是否真正啟動。"
            }
        )

        raw.append("## Summary-safe metadata\n")
            .append("assistant=").append(assistant).append('\n')
            .append("voice_interaction_service=").append(voiceInteraction).append('\n')
            .append("voice_recognition_service=").append(voiceRecognition).append('\n')
            .append("assistant_role=").append(assistantRole).append('\n')
            .append("google_system_app=").append(isGoogleSystemApp).append('\n')
            .append("voiceinteraction_filtered=").append(voiceDump).append('\n')
            .append("hotword_state_filtered=").append(hotwordState).append('\n')
            .append("soundtrigger_services=").append(soundTriggerServices).append('\n')
            .append("soundtrigger_summary=").append(soundTriggerSummary).append('\n')

        GoogleHealthReport(items, raw.toString().trim())
    }

    fun restartHotwordDetection(): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }

        val command = shell.exec("cmd voiceinteraction restart-detection")
        check(command.ok) { "送出 Hotword restart 指令失敗：\n${command.diagnostic()}" }

        Thread.sleep(700)

        val state = shell.exec(
            "dumpsys voiceinteraction 2>/dev/null | " +
                "grep -E 'Hotword detection service|mBound|No Hotword detection connection|Hotword detection connection' | head -n 80 || true"
        )

        if (state.output.contains("No Hotword detection connection", true)) {
            "系統已接受 restart-detection 指令，但目前沒有 active Hotword detection connection，因此實際沒有可重啟的 detector。這表示問題發生在 detector 建立之前。"
        } else {
            "Hotword Detection Service 已送出重啟要求，重新檢查時沒有再看到 No Hotword detection connection。"
        }
    }
}
