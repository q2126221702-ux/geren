package com.xuzheng.tiyuengine.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.xuzheng.tiyuengine.BuildConfig
import com.xuzheng.tiyuengine.data.AiMode
import com.xuzheng.tiyuengine.data.AiSettingsStore

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onAiSettings: () -> Unit,
    onUpdate: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val colors = appColors()
    val aiSettings = remember(context) { AiSettingsStore(context).load() }
    val aiSummary = when {
        aiSettings.mode == AiMode.SHARED -> "站点默认 AI · 已就绪"
        aiSettings.hasApiKey -> "${aiSettings.provider.name} · 已配置"
        else -> "需要填写 API Key"
    }

    SettingsScaffold(title = "应用设置", subtitle = "管理学习数据与应用功能", onBack = onBack, icon = Icons.Default.Settings) {
        SettingsGroup("学习数据") {
            SettingsNavRow("学习数据备份", "导出、恢复学习记录", onBackup, Icons.Default.CloudUpload, colors.success)
        }
        SettingsGroup("智能服务") {
            SettingsNavRow("AI 设置", aiSummary, onAiSettings, Icons.Default.AutoAwesome, colors.violet)
        }
        SettingsGroup("应用维护") {
            SettingsNavRow(
                "检查更新",
                "当前版本 ${BuildConfig.VERSION_NAME}",
                onUpdate,
                Icons.Default.SystemUpdate,
                colors.actionOrange
            )
        }
        SettingsGroup("关于") {
            SettingsNavRow("关于题域引擎", "应用信息与说明", onAbout, Icons.Default.Info, colors.primary)
        }
    }
}
