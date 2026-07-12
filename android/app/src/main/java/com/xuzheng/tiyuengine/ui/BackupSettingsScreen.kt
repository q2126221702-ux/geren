package com.xuzheng.tiyuengine.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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
        snackbarHostState = snackbarHostState,
    ) {
        Surface(color = Color(0xFFE8EFF9), shape = RoundedCornerShape(14.dp)) {
            Text(
                "备份包含学习记录、错题与收藏。恢复后将覆盖当前设备上的对应数据。",
                modifier = Modifier.padding(16.dp),
                color = Color(0xFF526174),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val fileName = "题域引擎-学习备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.json"
                    exportBackup.launch(fileName)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("导出学习数据") }
            OutlinedButton(
                onClick = { importBackup.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("从备份文件恢复") }
        }
    }

    if (pendingImportJson != null && pendingImportPreview != null) {
        val preview = pendingImportPreview!!
        AlertDialog(
            onDismissRequest = { pendingImportJson = null; pendingImportPreview = null },
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
                TextButton(onClick = { pendingImportJson = null; pendingImportPreview = null }) {
                    Text("取消")
                }
            },
        )
    }
}
