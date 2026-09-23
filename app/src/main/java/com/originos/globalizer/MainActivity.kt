package com.originos.globalizer

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.originos.globalizer.core.*
import com.originos.globalizer.shizuku.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val shell = ShizukuShell()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GlobalizerApp(shell)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shell.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalizerApp(shell: ShizukuShell) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val inspector = remember { GoogleInspector(context) }
    val snapshotStore = remember { SnapshotStore(context) }
    val engine = remember { TweakEngine(shell, snapshotStore) }
    val healthChecker = remember { GoogleHealth(shell) }
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf(inspector.inspect()) }
    var health by remember { mutableStateOf<GoogleHealthReport?>(null) }
    var shizukuAlive by remember { mutableStateOf(shell.binderAlive()) }
    var shizukuGranted by remember { mutableStateOf(shell.permissionGranted()) }
    var shellConnected by remember { mutableStateOf(shell.isConnected()) }
    var busy by remember { mutableStateOf(false) }
    var diagnosticBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val binderListener = Shizuku.OnBinderReceivedListener {
            shizukuAlive = shell.binderAlive()
            shizukuGranted = shell.permissionGranted()
            shell.ensureConnected()
            shellConnected = shell.isConnected()
        }
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
            shizukuGranted = result == 0
            if (shizukuGranted) shell.ensureConnected()
        }
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    fun refresh() {
        report = inspector.inspect()
        shizukuAlive = shell.binderAlive()
        shizukuGranted = shell.permissionGranted()
        if (shizukuGranted) shell.ensureConnected()
        shellConnected = shell.isConnected()
    }

    fun launchDefaultAssistant() {
        val result = runCatching {
            val intent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        message = result.fold(
            { "已透過 Android ACTION_ASSIST 呼叫預設數位助理。若跳出 Google / Gemini，代表一般 Assistant 啟動路徑正常。" },
            { "無法透過 ACTION_ASSIST 啟動助理：${it.message}" }
        )
    }

    fun openGoogleHotwordSettings() {
        val googleIntent = Intent("com.google.android.googlequicksearchbox.action.HOTWORD_DETECTION_SETTINGS").apply {
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val result = runCatching {
            context.startActivity(googleIntent)
        }.recoverCatching {
            val fallback = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }

        message = result.fold(
            { "已開啟 Google Hotword / Voice Match 設定；若 Google 專用頁不存在，會退回 Android 語音輸入設定。" },
            { "找不到可開啟的 Hotword / Voice Match 設定頁：${it.message}" }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("OriginOS Globalizer v0.2.3") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "${report.manufacturer} ${report.model}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text("${report.androidVersion} · ${report.originOsVersion}")

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google 相容度", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { report.score / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${report.score}%")
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shizuku", fontWeight = FontWeight.Bold)
                    Text("服務：${if (shizukuAlive) "已啟動" else "未啟動"}")
                    Text("權限：${if (shizukuGranted) "已授權" else "未授權"}")
                    Text("Shell：${if (shellConnected) "已連線" else "未連線"}")
                    shell.shellUid()?.let { Text("Shell UID：$it") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { shell.requestPermission() },
                            enabled = shizukuAlive && !shizukuGranted
                        ) {
                            Text("授權")
                        }
                        OutlinedButton(onClick = { refresh() }) { Text("重新檢查") }
                    }
                }
            }

            Text(
                "Google 深度診斷",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "檢查 FCM 推播基礎、GMS/Gmail 背景狀態、Assistant Role、VoiceInteractionService 與 Hey Google 軟體層條件。",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuGranted && !diagnosticBusy,
                onClick = {
                    diagnosticBusy = true
                    message = null
                    shell.ensureConnected()
                    scope.launch {
                        delay(500)
                        shellConnected = shell.isConnected()
                        val result = if (shellConnected) {
                            withContext(Dispatchers.IO) { healthChecker.collect() }
                        } else {
                            Result.failure(IllegalStateException("Shizuku 已授權，但 Shell User Service 尚未連線；請按一次重新檢查後再試。"))
                        }
                        health = result.getOrNull()
                        message = result.exceptionOrNull()?.message?.let { "診斷失敗：$it" }
                        diagnosticBusy = false
                    }
                }
            ) {
                Text(if (diagnosticBusy) "診斷中…" else "執行 Google 深度診斷")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy && !diagnosticBusy,
                    onClick = { launchDefaultAssistant() }
                ) {
                    Text("測試喚起 Assistant")
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !busy && !diagnosticBusy,
                    onClick = { openGoogleHotwordSettings() }
                ) {
                    Text("Voice Match 設定")
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuGranted && !busy && !diagnosticBusy,
                onClick = {
                    busy = true
                    message = null
                    shell.ensureConnected()
                    scope.launch {
                        delay(300)
                        val restart = withContext(Dispatchers.IO) {
                            healthChecker.restartHotwordDetection()
                        }
                        if (restart.isSuccess) {
                            delay(900)
                            val recheck = withContext(Dispatchers.IO) {
                                healthChecker.collect()
                            }
                            health = recheck.getOrNull() ?: health
                        }
                        message = restart.fold({ it }, { "重啟失敗：${it.message}" })
                        busy = false
                    }
                }
            ) {
                Text("重啟 Hotword 並驗證")
            }

            health?.let { h ->
                Card {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        h.items.forEach { GoogleDiagnosticRow(it) }
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        clipboard.setText(AnnotatedString(h.rawReport))
                        message = "已複製診斷報告。報告刻意不收集帳號、電話、IMEI、序號、Wi‑Fi 或位置資料。"
                    }
                ) {
                    Text("複製診斷報告")
                }
            }

            Text("Google 服務", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            report.checks.forEach { ServiceRow(it) }

            Text("🌏 Google 海外模式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TweakCatalog.overseasMode.forEach { TweakRow(it) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuGranted && !busy,
                onClick = {
                    busy = true
                    message = null
                    shell.ensureConnected()
                    scope.launch {
                        delay(300)
                        val result = withContext(Dispatchers.IO) {
                            engine.apply(TweakCatalog.overseasMode)
                        }
                        message = result.fold({ it }, { "失敗：${it.message}" })
                        busy = false
                        refresh()
                    }
                }
            ) { Text(if (busy) "處理中…" else "套用 Google 海外模式") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuGranted && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        delay(300)
                        val result = withContext(Dispatchers.IO) {
                            engine.restore(TweakCatalog.overseasMode + TweakCatalog.debloat)
                        }
                        message = result.fold({ it }, { "失敗：${it.message}" })
                        busy = false
                        refresh()
                    }
                }
            ) { Text("↩ 復原最近一次修改") }

            Text("OriginOS 精簡 / 限制", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TweakCatalog.debloat.forEach { tweak ->
                TweakRow(tweak)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = shizukuGranted && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            delay(300)
                            val result = withContext(Dispatchers.IO) {
                                engine.apply(listOf(tweak))
                            }
                            message = result.fold({ it }, { "失敗：${it.message}" })
                            busy = false
                            refresh()
                        }
                    }
                ) { Text("套用：${tweak.title}") }
            }

            message?.let {
                Card { Text(it, Modifier.padding(16.dp)) }
            }

            Text(
                "v0.2.3 原則：先診斷、後修改。若 Hotword 核心權限被 ROM 拒絕，會直接判定 no-root always-on Hey Google 不可行；Assistant 測試仍使用一般 ACTION_ASSIST；不 Root、不改 /system、不碰 Verified Boot；Hey Google / hotword 本版只判斷軟體層條件，不會直接改寫 OEM hotword 設定。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ServiceRow(check: ServiceCheck) {
    val icon = when (check.status) {
        Status.OK -> Icons.Default.CheckCircle
        Status.WARNING -> Icons.Default.Warning
        Status.MISSING, Status.UNKNOWN -> Icons.Default.Error
    }
    ListItem(
        headlineContent = { Text(check.title) },
        supportingContent = { Text(check.detail) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
    HorizontalDivider()
}

@Composable
private fun GoogleDiagnosticRow(item: GoogleDiagnosticItem) {
    val icon = when (item.status) {
        Status.OK -> Icons.Default.CheckCircle
        Status.WARNING -> Icons.Default.Warning
        Status.MISSING, Status.UNKNOWN -> Icons.Default.Error
    }
    ListItem(
        headlineContent = { Text(item.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(item.detail) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}

@Composable
private fun TweakRow(tweak: Tweak) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tweak.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (tweak.risk) {
                                Risk.LOW -> "低風險"
                                Risk.MEDIUM -> "中風險"
                                Risk.HIGH -> "高風險"
                            }
                        )
                    }
                )
            }
            Text(tweak.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
