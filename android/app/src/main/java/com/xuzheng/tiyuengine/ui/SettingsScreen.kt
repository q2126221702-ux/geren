package com.xuzheng.tiyuengine.ui

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
    val aiSettings = remember(context) { AiSettingsStore(context).load() }
    val aiSummary = when {
        aiSettings.mode == AiMode.SHARED -> "站点默认 AI · 已就绪"
        aiSettings.hasApiKey -> "${aiSettings.provider.name} · 已配置"
        else -> "需要填写 API Key"
    }

    SettingsScaffold(title = "应用设置", subtitle = "数据、AI 与版本管理", onBack = onBack) {
        SettingsGroup("数据") {
            SettingsNavRow("学习数据备份", "导出或恢复本机记录", onBackup)
        }
        SettingsGroup("AI") {
            SettingsNavRow("AI 设置", aiSummary, onAiSettings)
        }
        SettingsGroup("应用") {
            SettingsNavRow("检查更新", "当前版本 ${BuildConfig.VERSION_NAME}", onUpdate)
        }
        SettingsGroup("关于") {
            SettingsNavRow("关于题域引擎", "版本、隐私与版权", onAbout)
        }
    }
}
