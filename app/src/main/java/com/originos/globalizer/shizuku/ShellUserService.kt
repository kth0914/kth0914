package com.originos.globalizer.shizuku

import android.content.Context
import android.system.Os
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellUserService() : IShellService.Stub() {
    @Keep
    constructor(context: Context) : this()

    override fun uid(): Int = Os.getuid()

    override fun exec(command: String): String {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val code = process.waitFor()
        return "__EXIT__=$code\n$output"
    }

    override fun destroy() {
        System.exit(0)
    }
}
