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
            entries += SnapshotEntry(tweak.id, old.output.trim(), System.currentTimeMillis())
            val result = shell.exec(tweak.applyCommand)
            check(result.ok) { "${tweak.title} 失敗: ${result.output}" }
            log.append("✓ ").append(tweak.title).append('\n')
        }

        snapshots.save(entries)
        log.toString().trim()
    }

    fun restore(tweaks: List<Tweak>): Result<String> = runCatching {
        require(shell.isConnected()) { "Shizuku shell 尚未連線" }
        val saved = snapshots.load()
        val snapshotMap = saved.associateBy { it.tweakId }
        require(snapshotMap.isNotEmpty()) { "沒有可用的 Snapshot" }

        val log = StringBuilder()
        tweaks.filter { it.id in snapshotMap }.asReversed().forEach { tweak ->
            val old = snapshotMap.getValue(tweak.id).oldValue
            val shouldRestore = when (tweak.restorePolicy) {
                RestorePolicy.ALWAYS -> true
                RestorePolicy.ONLY_IF_OLD_EMPTY -> old.isBlank()
            }
            if (shouldRestore) {
                val result = shell.exec(tweak.restoreTemplate)
                check(result.ok) { "復原 ${tweak.title} 失敗: ${result.output}" }
                log.append("↩ ").append(tweak.title).append('\n')
            } else {
                log.append("＝ ").append(tweak.title).append("（原本即為此狀態）\n")
            }
        }
        snapshots.clear()
        log.toString().trim()
    }
}
