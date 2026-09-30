package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val colors = appColors()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("尚未检查更新") }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateCacheBytes by remember { mutableStateOf(appUpdater.cachedUpdateBytes()) }

    SettingsScaffold(
        title = "检查更新",
        subtitle = "从 GitHub Releases 获取最新版本",
        onBack = onBack,
        icon = Icons.Default.SystemUpdate,
        snackbarHostState = snackbarHostState,
    ) {
        SettingsGroup("当前版本") {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(60.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.SystemUpdate, null, tint = colors.primary, modifier = Modifier.size(36.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("题域引擎", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 17.sp)
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary
                    )
                    Text("这是当前安装的应用版本", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        }
        SettingsGroup("更新状态") {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = colors.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(updateStatus, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        "从 GitHub Releases 获取官方发布的最新版本",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
        SettingsPrimaryButton(
            text = if (checkingUpdate) "正在检查…" else "检查更新",
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
                                updateTitle(update)
                            }
                        }
                        .onFailure {
                            updateStatus = "检查失败"
                            snackbarHostState.showSnackbar(it.message ?: "检查更新失败，请稍后重试")
                        }
                    checkingUpdate = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsGroup("存储管理") {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DeleteOutline, null, tint = colors.actionOrange, modifier = Modifier.size(28.dp))
                Column {
                    Text("安装包缓存", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = 16.sp)
                    Text(
                        if (updateCacheBytes > 0L) formatStorageSize(updateCacheBytes) else "暂无缓存",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
        if (updateCacheBytes > 0L) {
            SettingsSecondaryButton(
                text = "清理安装包缓存",
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(color = colors.infoBanner, shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                Text(
                    "发现新版本后会先下载安装包，再由系统确认安装。仅从官方发布页获取更新。",
                    color = colors.infoBannerText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text(updateTitle(update), fontWeight = FontWeight.Bold) },
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

private fun updateTitle(update: UpdateInfo): String =
    if (update.versionName == BuildConfig.VERSION_NAME) {
        "发现补丁更新 ${update.versionName}"
    } else {
        "发现新版本 ${update.versionName}"
    }
