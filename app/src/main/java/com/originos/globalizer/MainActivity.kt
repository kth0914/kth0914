package com.originos.globalizer

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.originos.globalizer.core.*
import com.originos.globalizer.shizuku.ShizukuShell
import kotlinx.coroutines.Dispatchers
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
    val inspector = remember { GoogleInspector(context) }
    val snapshotStore = remember { SnapshotStore(context) }
    val engine = remember { TweakEngine(shell, snapshotStore) }
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf(inspector.inspect()) }
    var shizukuAlive by remember { mutableStateOf(shell.binderAlive()) }
    var shizukuGranted by remember { mutableStateOf(shell.permissionGranted()) }
    var shellConnected by remember { mutableStateOf(shell.isConnected()) }
    var busy by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("OriginOS Globalizer v0.1.2") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("${report.manufacturer} ${report.model}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                        Button(onClick = { shell.requestPermission() }, enabled = shizukuAlive && !shizukuGranted) {
                            Text("授權")
                        }
                        OutlinedButton(onClick = { refresh() }) { Text("重新檢查") }
                    }
                }
            }

            Text("Google 服務", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            report.checks.forEach { ServiceRow(it) }

            Text("🌏 海外模式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TweakCatalog.overseasMode.forEach { TweakRow(it) }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuGranted && !busy,
                onClick = {
                    busy = true
                    message = null
                    shell.ensureConnected()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { engine.apply(TweakCatalog.overseasMode) }
                        message = result.fold({ it }, { "失敗：${it.message}" })
                        busy = false
                        refresh()
                    }
                }
            ) { Text(if (busy) "處理中…" else "啟用海外模式") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuGranted && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { engine.restore(TweakCatalog.overseasMode + TweakCatalog.debloat) }
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
                            val result = withContext(Dispatchers.IO) { engine.apply(listOf(tweak)) }
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
                "v0.1.2 原則：不 Root、不改 /system、不碰 Verified Boot。vivo 對部分自家套件採 root-only 保護時，本 App 只做可逆背景限制，不宣稱完整停用。Hey Google / hotword 尚不在本版強改。",
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
