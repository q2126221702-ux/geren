package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.BuildConfig
import com.xuzheng.tiyuengine.data.AppUpdater
import com.xuzheng.tiyuengine.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdateSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appUpdater = remember(context) { AppUpdater(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("当前版本 ${BuildConfig.VERSION_NAME}") }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateCacheBytes by remember { mutableStateOf(appUpdater.cachedUpdateBytes()) }

    SettingsScaffold(
        title = "检查更新",
        subtitle = "从 GitHub Releases 获取最新版本",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前版本", color = Color(0xFF64748B), fontSize = 13.sp)
                Text(BuildConfig.VERSION_NAME, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF123A70))
                Text(updateStatus, color = Color(0xFF64748B), fontSize = 13.sp)
            }
        }
        Button(
            enabled = !checkingUpdate,
            onClick = {
                checkingUpdate = true
                updateStatus = "正在检查…"
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { appUpdater.checkForUpdate() } }
                        .onSuccess { update ->
                            availableUpdate = update
                            updateStatus = if (update == null) {
                                "已是最新版本"
                            } else {
                                "发现新版本 ${update.versionName}"
                            }
                        }
                        .onFailure {
                            updateStatus = "检查失败"
                            snackbarHostState.showSnackbar(it.message ?: "检查更新失败，请稍后重试")
                        }
                    checkingUpdate = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(if (checkingUpdate) "正在检查…" else "检查更新") }

        if (updateCacheBytes > 0L) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (appUpdater.clearCachedUpdates()) {
                            updateCacheBytes = 0L
                            snackbarHostState.showSnackbar("安装包缓存已清理")
                        } else {
                            snackbarHostState.showSnackbar("缓存清理失败，请稍后重试")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("清理安装包缓存（${formatStorageSize(updateCacheBytes)}）")
            }
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("发现新版本 ${update.versionName}", fontWeight = FontWeight.Bold) },
            text = { Text(update.notes, lineHeight = 22.sp) },
            confirmButton = {
                Button(onClick = {
                    availableUpdate = null
                    checkingUpdate = true
                    updateStatus = "正在下载安装包…"
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { appUpdater.download(update) } }
                            .onSuccess { apk ->
                                updateCacheBytes = apk.length()
                                val installerOpened = appUpdater.install(apk)
                                updateStatus = if (installerOpened) {
                                    "安装包已下载"
                                } else {
                                    "请允许安装未知应用"
                                }
                                snackbarHostState.showSnackbar(
                                    if (installerOpened) "请按系统提示完成更新" else "请允许安装未知应用后重试",
                                )
                            }
                            .onFailure {
                                updateStatus = "下载失败"
                                snackbarHostState.showSnackbar(it.message ?: "更新下载失败，请稍后重试")
                            }
                        checkingUpdate = false
                    }
                }) { Text("下载并安装") }
            },
            dismissButton = { TextButton(onClick = { availableUpdate = null }) { Text("稍后") } },
        )
    }
}
