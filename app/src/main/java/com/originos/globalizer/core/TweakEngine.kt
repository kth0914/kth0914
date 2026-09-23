package com.originos.globalizer.core

import com.originos.globalizer.shizuku.ShizukuShell

class TweakEngine(
    private val shell: ShizukuShell,
    private val snapshots: SnapshotStore
) {
    fun apply(tweaks: List<Tweak>): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }

        val entries = snapshots.load().toMutableList()
        val log = StringBuilder()

        for (tweak in tweaks) {
            val primaryOld = shell.exec(tweak.readCommand)
            val primary = shell.exec(tweak.applyCommand)

            val entry = if (primary.ok) {
                log.append("✓ ").append(tweak.title).append("：已完整停用/套用\n")
                SnapshotEntry(
                    tweakId = tweak.id,
                    oldValue = primaryOld.output.trim(),
                    timestamp = System.currentTimeMillis(),
                    appliedMode = "primary"
                )
            } else {
                val fallbackCommand = tweak.fallbackApplyCommand
                    ?: error(
                        "${tweak.title} 失敗\n\n系統回傳：\n${primary.diagnostic()}"
                    )

                val fallbackOld = tweak.fallbackReadCommand
                    ?.let { shell.exec(it).output.trim() }
                    .orEmpty()

                val fallback = shell.exec(fallbackCommand)
                if (!fallback.ok) {
                    error(
                        "${tweak.title} 失敗\n\n" +
                            "標準停用：\n${primary.diagnostic()}\n\n" +
                            "${tweak.fallbackLabel}：\n${fallback.diagnostic()}"
                    )
                }

                log.append("⚠ ")
                    .append(tweak.title)
                    .append("：OriginOS 將此套件設為 root-only，無法真正 Disabled；已改用")
                    .append(tweak.fallbackLabel)
                    .append("。App 仍可手動開啟，但背景活動會被限制。\n")

                SnapshotEntry(
                    tweakId = tweak.id,
                    oldValue = fallbackOld,
                    timestamp = System.currentTimeMillis(),
                    appliedMode = "fallback"
                )
            }

            entries.removeAll { it.tweakId == tweak.id }
            entries += entry
            snapshots.save(entries)
        }

        log.toString().trim()
    }

    fun restore(tweaks: List<Tweak>): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }
        val saved = snapshots.load()
        val snapshotMap = saved.associateBy { it.tweakId }
        require(snapshotMap.isNotEmpty()) { "沒有可用的 Snapshot" }

        val log = StringBuilder()
        tweaks.filter { it.id in snapshotMap }.asReversed().forEach { tweak ->
            val entry = snapshotMap.getValue(tweak.id)

            if (entry.appliedMode == "fallback") {
                val template = tweak.fallbackRestoreTemplate
                    ?: error("${tweak.title} 沒有替代模式的復原指令")
                val command = renderFallbackRestore(template, entry.oldValue)
                val result = shell.exec(command)
                check(result.ok) {
                    "復原 ${tweak.title} 的限制模式失敗\n\n系統回傳：\n${result.diagnostic()}"
                }
                log.append("↩ ").append(tweak.title).append("：背景限制已復原\n")
            } else {
                val old = entry.oldValue
                val shouldRestore = when (tweak.restorePolicy) {
                    RestorePolicy.ALWAYS -> true
                    RestorePolicy.ONLY_IF_OLD_EMPTY -> old.isBlank()
                }

                if (shouldRestore) {
                    val result = shell.exec(tweak.restoreTemplate)
                    check(result.ok) {
                        "復原 ${tweak.title} 失敗\n\n系統回傳：\n${result.diagnostic()}"
                    }
                    log.append("↩ ").append(tweak.title).append('\n')
                } else {
                    log.append("＝ ").append(tweak.title).append("（修改前就已是此狀態）\n")
                }
            }
        }

        snapshots.clear()
        log.toString().trim()
    }

    private fun renderFallbackRestore(template: String, snapshot: String): String {
        val allowedModes = setOf("allow", "ignore", "deny", "default", "foreground", "errored")
        val values = snapshot.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null
                else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()

        var result = template
        listOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND").forEach { key ->
            val mode = values[key]?.takeIf { it in allowedModes } ?: "default"
            result = result.replace("{{$key}}", mode)
        }
        return result
    }
}
