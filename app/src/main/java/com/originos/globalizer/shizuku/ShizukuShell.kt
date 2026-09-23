package com.originos.globalizer.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

class ShizukuShell {
    private var service: IShellService? = null
    private var connecting = false

    private val args = Shizuku.UserServiceArgs(
        ComponentName("com.originos.globalizer", ShellUserService::class.java.name)
    )
        .processNameSuffix("shell")
        .daemon(false)
        .tag("globalizer-shell")
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShellService.Stub.asInterface(binder)
            connecting = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connecting = false
        }
    }

    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun permissionGranted(): Boolean = binderAlive() &&
        runCatching { Shizuku.checkSelfPermission() == 0 }.getOrDefault(false)

    fun requestPermission(requestCode: Int = 42) {
        if (binderAlive()) Shizuku.requestPermission(requestCode)
    }

    fun ensureConnected() {
        if (!permissionGranted() || service != null || connecting) return
        connecting = true
        Shizuku.bindUserService(args, connection)
    }

    fun isConnected(): Boolean = service != null

    fun shellUid(): Int? = runCatching { service?.uid() }.getOrNull()

    fun exec(command: String): ShellResult {
        val raw = service?.exec(command) ?: return ShellResult(-999, "Shizuku shell service is not connected")
        val first = raw.lineSequence().firstOrNull().orEmpty()
        val code = first.substringAfter("__EXIT__=", "-1").toIntOrNull() ?: -1
        return ShellResult(code, raw.substringAfter('\n', ""))
    }

    fun close() {
        runCatching { Shizuku.unbindUserService(args, connection, true) }
        service = null
    }
}

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}
