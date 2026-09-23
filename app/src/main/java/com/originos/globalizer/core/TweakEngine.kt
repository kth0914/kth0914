package com.originos.globalizer.core

import com.originos.globalizer.shizuku.ShizukuShell

class TweakEngine(
    private val shell: ShizukuShell,
    private val snapshots: SnapshotStore
) {
    fun apply(tweaks: List<Tweak>): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }
        val entries = mutableListOf<SnapshotEntry>()
        val log = StringBuilder()

        for (tweak in tweaks) {
            val old = shell.exec(tweak.readCommand)
            val primary = shell.exec(tweak.applyCommand)

            val appliedMode = if (primary.ok) {
                "primary"
            } else {
                val fallbackCommand = tweak.fallbackApplyCommand
                    ?: error(
                        "${tweak.title} 失敗\n\n系統回傳：\n${primary.diagnostic()}"
                    )

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
                    .append("：OriginOS 拒絕標準停用，已改用")
                    .append(tweak.fallbackLabel)
                    .append('\n')
                "fallback"
            }

            entries += SnapshotEntry(
                tweakId = tweak.id,
                oldValue = old.output.trim(),
                timestamp = System.currentTimeMillis(),
                appliedMode = appliedMode
            )

            snapshots.save(entries)

            if (appliedMode == "primary") {
                log.append("✓ ").append(tweak.title).append('\n')
            }
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
            val old = entry.oldValue
            val shouldRestore = when (tweak.restorePolicy) {
                RestorePolicy.ALWAYS -> true
                RestorePolicy.ONLY_IF_OLD_EMPTY -> old.isBlank()
            }

            if (shouldRestore) {
                val command = if (entry.appliedMode == "fallback") {
                    tweak.fallbackRestoreTemplate ?: tweak.restoreTemplate
                } else {
                    tweak.restoreTemplate
                }

                val result = shell.exec(command)
                check(result.ok) {
                    "復原 ${tweak.title} 失敗\n\n系統回傳：\n${result.diagnostic()}"
                }
                log.append("↩ ").append(tweak.title).append('\n')
            } else {
                log.append("＝ ").append(tweak.title).append("（修改前就已是此狀態）\n")
            }
        }

        snapshots.clear()
        log.toString().trim()
    }
}
