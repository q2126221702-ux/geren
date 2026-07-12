package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.AiClient
import com.xuzheng.tiyuengine.data.AiMode
import com.xuzheng.tiyuengine.data.AiProvider
import com.xuzheng.tiyuengine.data.AiProviderCatalog
import com.xuzheng.tiyuengine.data.AiSettings
import com.xuzheng.tiyuengine.data.AiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { AiSettingsStore(context) }
    val client = remember(context) { AiClient(context) }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var savedSettings by remember { mutableStateOf(store.load()) }
    var mode by remember { mutableStateOf(savedSettings.mode) }
    var providerId by remember { mutableStateOf(savedSettings.providerId) }
    var model by remember { mutableStateOf(savedSettings.model) }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var providerDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusSuccess by remember { mutableStateOf<Boolean?>(null) }
    val provider = AiProviderCatalog.find(providerId)
    val canClear = savedSettings.hasApiKey || savedSettings.model.isNotBlank() || savedSettings.mode != AiMode.SHARED

    fun draftSettings() = AiSettings(mode, providerId, model, savedSettings.hasApiKey, savedSettings.keyHint)

    Scaffold(
        containerColor = Color(0xFFF6F8FB),
        topBar = {
            TopAppBar(
                title = { Column { Text("AI 设置", fontWeight = FontWeight.Bold, fontSize = 24.sp); Text("本地加密存储，仅用于访问 AI 服务", color = Color(0xFF64748B), fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        busy = true
                        status = "正在保存设置…"
                        statusSuccess = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { store.save(mode, providerId, model, apiKey) } }
                                .onSuccess {
                                    savedSettings = store.load()
                                    apiKey = ""
                                    status = "设置已安全保存在本机"
                                    statusSuccess = true
                                }
                                .onFailure { status = it.message ?: "保存失败，请检查填写内容"; statusSuccess = false }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("保存设置") }
                OutlinedButton(
                    onClick = {
                        busy = true
                        status = "正在测试连接…"
                        statusSuccess = null
                        scope.launch {
                            runCatching { client.test(draftSettings(), apiKey) }
                                .onSuccess { status = "连接成功 · ${if (mode == AiMode.SHARED) "站点默认 AI" else provider.name}"; statusSuccess = true }
                                .onFailure { status = it.message ?: "连接失败，请检查网络和 API Key"; statusSuccess = false }
                            busy = false
                        }
                    },
                    enabled = !busy && (mode == AiMode.SHARED || apiKey.isNotBlank() || savedSettings.hasApiKey),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (busy) "请稍候…" else "测试连接") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ConnectionStatus(savedSettings, status, statusSuccess)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI 接入方式", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeOption("站点默认 AI", mode == AiMode.SHARED, Modifier.weight(1f)) { mode = AiMode.SHARED; status = "" }
                    ModeOption("自带 API Key", mode == AiMode.OWN_KEY, Modifier.weight(1f)) { mode = AiMode.OWN_KEY; status = "" }
                }
                Text(if (mode == AiMode.SHARED) "使用共享额度，可能存在冷却和频率限制。" else "直连服务商，支持完整解析、重新生成和流式输出。", color = Color(0xFF64748B), fontSize = 13.sp)
            }
            if (mode == AiMode.OWN_KEY) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("服务提供商", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    ProviderRow(provider) { providerDialog = true }
                    Text(provider.hint, color = Color(0xFF64748B), fontSize = 13.sp)
                    TextButton(onClick = { uriHandler.openUri(provider.keyUrl) }, modifier = Modifier.padding(horizontal = 0.dp)) { Text("前往 ${provider.name} 官网申请 API Key →") }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; status = "" },
                        label = { Text("API Key") },
                        placeholder = { Text(if (savedSettings.hasApiKey) "已安全保存 · ••••${savedSettings.keyHint}（留空不修改）" else "粘贴你的 API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { keyVisible = !keyVisible }) { Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (keyVisible) "隐藏 API Key" else "显示 API Key") } },
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; status = "" },
                        label = { Text("模型（可选）") },
                        placeholder = { Text("留空使用 ${provider.defaultModel}") },
                        supportingText = { Text("推荐模型：${provider.defaultModel}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            Surface(color = Color(0xFFE8EFF9), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF0759BD), modifier = Modifier.size(22.dp))
                    Text("API Key 由 Android 系统密钥加密，不进入学习备份。使用 AI 时，题目和作答会直接发送给当前服务商处理。", color = Color(0xFF526174), fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
            TextButton(onClick = { clearDialog = true }, enabled = canClear) {
                val dangerColor = if (canClear) Color(0xFFC2412D) else Color(0xFF9CA3AF)
                Icon(Icons.Default.DeleteOutline, null, tint = dangerColor); Text(" 清除本机 AI 配置", color = dangerColor)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (providerDialog) {
        AlertDialog(
            onDismissRequest = { providerDialog = false },
            title = { Text("选择服务提供商", fontWeight = FontWeight.Bold) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { AiProviderCatalog.providers.forEach { item -> Row(Modifier.fillMaxWidth().clickable { providerId = item.id; model = ""; providerDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = item.id == providerId, onClick = null); Column(Modifier.padding(start = 8.dp)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.defaultModel, color = Color(0xFF64748B), fontSize = 12.sp) } } } } },
            confirmButton = { TextButton(onClick = { providerDialog = false }) { Text("完成") } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("清除本机 AI 配置？", fontWeight = FontWeight.Bold) },
            text = { Text("将删除已保存的 API Key、服务商和模型设置。此操作无法撤销。") },
            confirmButton = { Button(onClick = { store.clear(); savedSettings = store.load(); mode = savedSettings.mode; providerId = savedSettings.providerId; model = ""; apiKey = ""; status = "本机 AI 配置已清除"; statusSuccess = true; clearDialog = false }) { Text("清除配置") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("保留配置") } },
        )
    }
}

@Composable
private fun ConnectionStatus(settings: AiSettings, message: String, success: Boolean?) {
    val configured = settings.mode == AiMode.SHARED || settings.hasApiKey
    val stateSuccess = success ?: configured
    val title = when {
        message.isNotBlank() -> message
        settings.mode == AiMode.SHARED -> "站点默认 AI 已就绪"
        settings.hasApiKey -> "${settings.provider.name} · 已配置"
        else -> "尚未配置 API Key"
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(if (stateSuccess) Color(0xFFE1F5EC) else Color(0xFFFFE9E4), CircleShape), contentAlignment = Alignment.Center) { Icon(if (stateSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (stateSuccess) Color(0xFF138A5B) else Color(0xFFC2412D)) }
            Column(Modifier.padding(start = 12.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = if (success == false) Color(0xFFC2412D) else Color(0xFF111827)); Text(if (settings.mode == AiMode.SHARED) "GLM-4-Flash · 共享模式" else settings.activeModel, color = Color(0xFF64748B), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(52.dp), color = if (selected) Color(0xFFE8EFF9) else Color.White, shape = RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Color(0xFF0759BD) else Color(0xFFD7DEE8))) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected, onClick = null); Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp) }
    }
}

@Composable
private fun ProviderRow(provider: AiProvider, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(Color(0xFFE8EFF9), CircleShape), contentAlignment = Alignment.Center) { Text(provider.name.take(1), color = Color(0xFF0759BD), fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(provider.name, fontWeight = FontWeight.SemiBold); Text(provider.defaultModel, color = Color(0xFF64748B), fontSize = 12.sp) }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B))
    }
}
