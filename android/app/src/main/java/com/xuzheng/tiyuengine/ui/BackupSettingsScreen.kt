package com.xuzheng.tiyuengine.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.BackupPreview
import com.xuzheng.tiyuengine.data.LearningBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupSettingsScreen(onBack: () -> Unit, onDataImported: () -> Unit) {
    val context = LocalContext.current
    val learningBackup = remember(context) { LearningBackup(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportPreview by remember { mutableStateOf<BackupPreview?>(null) }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                            it.write(learningBackup.exportJson())
                        } ?: error("无法写入所选文件")
                    }
                }.onSuccess { snackbarHostState.showSnackbar("学习数据已导出") }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "导出失败") }
            }
        }
    }

    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("无法读取所选文件")
                        json to learningBackup.preview(json)
                    }
                }.onSuccess { (json, preview) ->
                    pendingImportJson = json
                    pendingImportPreview = preview
                }.onFailure { snackbarHostState.showSnackbar(it.message ?: "备份文件无效") }
            }
        }
    }

    SettingsScaffold(
        title = "学习数据备份",
        subtitle = "备份不含 AI Key，仅含学习记录",
        onBack = onBack,
        icon = Icons.Default.CloudUpload,
        iconColor = appColors().success,
        snackbarHostState = snackbarHostState,
    ) {
        val colors = appColors()
        Surface(color = colors.heroSky, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("你的学习进度，由你掌控。", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("导出备份后可安全保存学习记录，换设备时也能恢复。", color = colors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        SettingsGroup("备份包含") {
            BackupDataRow(Icons.Default.Description, "学习记录", "已练习题目与复习进度", colors.success)
            BackupDataRow(Icons.Default.ErrorOutline, "错题记录", "添加的错题与相关笔记", colors.violet)
            BackupDataRow(Icons.Default.FavoriteBorder, "收藏题目", "你收藏的题目列表", colors.actionOrange)
        }
        Surface(color = colors.greenSoft, shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Shield, null, tint = colors.success, modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AI Key 不会导出", color = colors.success, fontWeight = FontWeight.Bold)
                    Text("备份文件不包含 API Key 等敏感配置。", color = colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsPrimaryButton(
                text = "导出学习数据",
                onClick = {
                    val fileName = "题域引擎-学习备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.json"
                    exportBackup.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsSecondaryButton(
                text = "从备份文件恢复",
                onClick = { importBackup.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("恢复会覆盖本机对应的学习、错题和收藏数据，请先导出当前备份。", color = colors.warning, fontSize = 13.sp, lineHeight = 20.sp)
    }

    if (pendingImportJson != null && pendingImportPreview != null) {
        val preview = pendingImportPreview!!
        AlertDialog(
            onDismissRequest = {
                pendingImportJson = null
                pendingImportPreview = null
            },
            title = { Text("确认恢复学习数据？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "备份时间：${formatDateTime(preview.exportedAt)}\n" +
                        "学习记录：${preview.learningRecordCount} 条\n" +
                        "错题记录：${preview.wrongItemCount} 条\n" +
                        "收藏题目：${preview.favoriteCount} 道\n\n" +
                        "恢复后将覆盖当前设备上的学习、错题与收藏记录。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    val json = pendingImportJson ?: return@Button
                    pendingImportJson = null
                    pendingImportPreview = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { learningBackup.restore(json) } }
                            .onSuccess {
                                onDataImported()
                                snackbarHostState.showSnackbar(
                                    "恢复完成：${it.learningRecordCount} 条学习记录、${it.wrongItemCount} 条错题",
                                )
                            }
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "恢复失败") }
                    }
                }) { Text("覆盖并恢复") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImportJson = null
                    pendingImportPreview = null
                }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun BackupDataRow(icon: ImageVector, title: String, subtitle: String, tint: Color) {
    val colors = appColors()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(tint.copy(alpha = 0.11f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Column {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
