package com.xieguiawu.picturetrans.ui

import android.provider.Settings
import com.xieguiawu.picturetrans.media.MediaPermissions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xieguiawu.picturetrans.MainViewModel
import com.xieguiawu.picturetrans.qr.QrCode
import com.xieguiawu.picturetrans.server.ServerState
import com.xieguiawu.picturetrans.transfer.Direction

@Composable
fun MainScreen(vm: MainViewModel) {
    val serverState by vm.serverState.collectAsState()
    val mediaGranted by vm.mediaGranted.collectAsState()
    val active by vm.activeTransfers.collectAsState()
    val history by vm.history.collectAsState()
    var portText by remember { mutableStateOf(vm.defaultPort().toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Picture Trans",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (!mediaGranted) {
            PermissionCard(onGranted = { vm.refreshPermission() })
        }

        when (val s = serverState) {
            is ServerState.Stopped -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("手机开热点或连同一 WiFi 后启动", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                                label = { Text("端口") },
                                singleLine = true,
                                modifier = Modifier.width(140.dp),
                            )
                            Button(onClick = { vm.startServer(portText.toIntOrNull() ?: 8765) }) {
                                Text("启动")
                            }
                        }
                    }
                }
            }
            is ServerState.Starting -> {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("正在启动…")
                    }
                }
            }
            is ServerState.Error -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.startServer(portText.toIntOrNull() ?: 8765) }) { Text("重试") }
                            OutlinedButton(onClick = { vm.stopServer() }) { Text("返回") }
                        }
                    }
                }
            }
            is ServerState.Running -> RunningCard(s, onStop = { vm.stopServer() })
        }

        if (active.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("传输中", fontWeight = FontWeight.SemiBold)
                    active.forEach { p ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (p.direction == Direction.UPLOAD) "⬇ 收自电脑" else "⬆ 发往电脑",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    p.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progressOf(p) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                            Text(
                                formatBytes(p.bytesDone) + if (p.bytesTotal > 0) " / " + formatBytes(p.bytesTotal) else "" +
                                    " · " + formatSpeed(p.speedBps),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("最近记录", fontWeight = FontWeight.SemiBold)
                    history.take(8).forEach { h ->
                        Text(
                            (if (h.ok) "✓ " else "✗ ") +
                                (if (h.direction == Direction.UPLOAD) "收自电脑 " else "发往电脑 ") +
                                h.fileName + " · " + formatBytes(h.bytes) +
                                " · " + formatTime(h.finishedAtMs) +
                                (h.message?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (h.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Text(
            "电脑浏览器打开上方网址即可互传；图片视频存相册，其他文件存下载目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun progressOf(p: com.xieguiawu.picturetrans.transfer.TransferProgress): Float =
    if (p.bytesTotal > 0) (p.bytesDone.toFloat() / p.bytesTotal).coerceIn(0f, 1f) else 0f

@Composable
private fun PermissionCard(onGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onGranted() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("需要授权访问照片和视频", fontWeight = FontWeight.SemiBold)
            Text(
                "不授权时仍可在电脑与手机下载目录之间传文件。",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(MediaPermissions.required()) }) {
                    Text("授权")
                }
                TextButton(onClick = {
                    // Android 11+ 半开设置页，便于手动处理「仅此一次」等限制授权
                }) { Text("忽略") }
            }
        }
    }
}

@Composable
private fun RunningCard(s: ServerState.Running, onStop: () -> Unit) {
    val first = s.urls.firstOrNull()
    val qr = remember(s.port) { QrCode.encode(first ?: "", 512) }
    val clipboard = LocalClipboardManager.current

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("运行中 · 端口 ${s.port}", fontWeight = FontWeight.SemiBold)
            if (first != null) {
                qr.let { r ->
                    when (r) {
                        is com.xieguiawu.picturetrans.qr.ImageBitmapResult.Success -> {
                            Image(
                                bitmap = r.bitmap,
                                contentDescription = "地址二维码",
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                            )
                        }
                        is com.xieguiawu.picturetrans.qr.ImageBitmapResult.Failed ->
                            Text("二维码生成失败：" + r.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    first,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(first)) }) { Text("复制网址") }
                    OutlinedButton(onClick = onStop) { Text("停止") }
                }
                if (s.urls.size > 1) {
                    Text("其他地址：" + s.urls.drop(1).joinToString("  "), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text("未发现局域网地址，请确认 WiFi 已连接", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onStop) { Text("停止") }
            }
        }
    }
}
