package com.thisko.qringprint.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 全局"发现新版本"弹窗. 在任意 Composable 里调用即可, 内部读 [UpdateManager.promptVisible].
 * 用户点了"立即更新"会触发下载; 下载进度通过 [UpdateManager.state] 反馈到同一弹窗.
 */
@Composable
fun UpdatePromptHost() {
    val visible by UpdateManager.promptVisible.collectAsState()
    val state by UpdateManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (!visible) return
    when (val s = state) {
        is UpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.dismissPrompt() },
                title = { Text("正在检查更新…") },
                text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
                confirmButton = {},
            )
        }
        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = { UpdateManager.dismissPrompt() },
                title = { Text("已是最新版本") },
                text = { Text("当前版本：${s.current}") },
                confirmButton = {
                    TextButton(onClick = { UpdateManager.dismissPrompt() }) { Text("好的") }
                },
            )
        }
        is UpdateUiState.Available -> {
            val manifest = s.manifest
            AlertDialog(
                onDismissRequest = { UpdateManager.dismissPrompt() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("发现新版本 ${manifest.versionName}")
                    }
                },
                text = {
                    Column {
                        Text(
                            "当前版本：${s.currentVersionName}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (manifest.title.isNotBlank()) {
                            Text(manifest.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (manifest.changelog.isNotBlank()) {
                            Text(manifest.changelog, fontSize = 13.sp)
                        }
                        if (manifest.force) {
                            Spacer(Modifier.height(8.dp))
                            Text("本次为强制更新", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        UpdateManager.dismissPrompt()
                        scope.launch { UpdateManager.downloadAndInstall(context, manifest.url) }
                    }) { Text("立即更新") }
                },
                dismissButton = {
                    if (!manifest.force) {
                        Row {
                            TextButton(onClick = { UpdateManager.skipThisVersion(context, manifest.versionCode) }) {
                                Text("跳过该版本")
                            }
                            TextButton(onClick = { UpdateManager.dismissPrompt() }) {
                                Text("稍后")
                            }
                        }
                    }
                },
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("正在下载新版本…") },
                text = {
                    Column {
                        LinearProgressIndicator(progress = s.progress / 100f, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${s.progress}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {},
            )
        }
        is UpdateUiState.ReadyToInstall -> {
            LaunchedEffect(Unit) { UpdateManager.dismissPrompt() }
        }
        is UpdateUiState.Failed -> {
            if (s.reason.isBlank()) {
                LaunchedEffect(Unit) { UpdateManager.dismissPrompt() }
            } else {
                AlertDialog(
                    onDismissRequest = { UpdateManager.dismissPrompt() },
                    title = { Text("检查更新失败") },
                    text = { Text(s.reason) },
                    confirmButton = { TextButton(onClick = { UpdateManager.dismissPrompt() }) { Text("好的") } },
                )
            }
        }
        else -> Unit
    }
}
