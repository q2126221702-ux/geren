package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
    val colors = appColors()
    val provider = AiProviderCatalog.find(providerId)
    val canClear = savedSettings.hasApiKey || savedSettings.model.isNotBlank() || savedSettings.mode != AiMode.SHARED
    val keyReady = mode == AiMode.SHARED || apiKey.isNotBlank() || savedSettings.hasApiKey

    fun draftSettings() = AiSettings(mode, providerId, model, savedSettings.hasApiKey, savedSettings.keyHint)

    SettingsScaffold(
        title = "AI 设置",
        subtitle = "本地加密存储，仅用于访问 AI 服务",
        onBack = onBack,
        icon = Icons.Default.AutoAwesome,
        iconColor = colors.violet,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(colors.surface).imePadding().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsPrimaryButton(
                    text = "保存设置",
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
                                .onFailure {
                                    status = it.message ?: "保存失败，请检查填写内容";
                                    statusSuccess = false
                                }
                            busy = false
                        }
                    },
                    enabled = !busy && keyReady,
                    modifier = Modifier.weight(1f),
                )
                SettingsSecondaryButton(
                    text = if (busy) "请稍候…" else "测试连接",
                    onClick = {
                        busy = true
                        status = "正在测试连接…"
                        statusSuccess = null
                        scope.launch {
                            runCatching { client.test(draftSettings(), apiKey) }
                                .onSuccess {
                                    status = "连接成功 · ${if (mode == AiMode.SHARED) "站点默认 AI" else provider.name}";
                                    statusSuccess = true
                                }
                                .onFailure {
                                    status = it.message ?: "连接失败，请检查网络和 API Key";
                                    statusSuccess = false
                                }
                            busy = false
                        }
                    },
                    enabled = !busy && keyReady,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        ConnectionStatus(savedSettings, status, statusSuccess)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AI 接入方式", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeOption(
                    "站点默认 AI",
                    "使用共享额度，开箱即用",
                    mode == AiMode.SHARED,
                    Modifier.fillMaxWidth()
                ) {
                    mode = AiMode.SHARED;
                    status = ""
                }
                ModeOption(
                    "自带 API Key",
                    "使用自己的服务额度",
                    mode == AiMode.OWN_KEY,
                    Modifier.fillMaxWidth()
                ) {
                    mode = AiMode.OWN_KEY;
                    status = ""
                }
            }
            Text(
                if (mode == AiMode.SHARED) "共享额度可能存在冷却和频率限制。" else "直连服务商，支持完整解析、重新生成和流式输出。",
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        if (mode == AiMode.OWN_KEY) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("配置参数", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                ProviderRow(provider) { providerDialog = true }
                Text(provider.hint, color = colors.textSecondary, fontSize = 13.sp)
                TextButton(
                    onClick = { uriHandler.openUri(provider.keyUrl) },
                    modifier = Modifier.padding(horizontal = 0.dp)
                ) { Text("前往 ${provider.name} 官网申请 API Key →") }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it;
                        status = ""
                    },
                    label = { Text("API Key") },
                    placeholder = {
                        Text(
                            if (savedSettings.hasApiKey) "已安全保存 · ••••${savedSettings.keyHint}（留空不修改）" else "粘贴你的 API Key"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { keyVisible = !keyVisible }
                        ) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (keyVisible) "隐藏 API Key" else "显示 API Key"
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary
                    ),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it;
                        status = ""
                    },
                    label = { Text("模型（可选）") },
                    placeholder = { Text("留空使用 ${provider.defaultModel}") },
                    supportingText = { Text("推荐模型：${provider.defaultModel}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary
                    ),
                )
            }
        }
        Surface(color = colors.greenSoft, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, tint = colors.success, modifier = Modifier.size(22.dp))
                Text(
                    "API Key 由 Android 系统密钥加密，不进入学习备份。使用 AI 时，题目和作答会直接发送给当前服务商处理。",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
        TextButton(onClick = { clearDialog = true }, enabled = canClear) {
            val dangerColor = if (canClear) colors.danger else colors.textSecondary
            Icon(Icons.Default.DeleteOutline, null, tint = dangerColor);
            Text(" 清除本机 AI 配置", color = dangerColor)
        }
    }

    if (providerDialog) {
        AlertDialog(
            onDismissRequest = { providerDialog = false },
            title = { Text("选择服务提供商", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState())
                ) {
                    AiProviderCatalog.providers.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                providerId = item.id;
                                model = "";
                                providerDialog = false
                            }.padding(
                                vertical = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item.id == providerId,
                                onClick = null
                            );
                            Column(
                                Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    item.name,
                                    fontWeight = FontWeight.SemiBold
                                );
                                Text(item.defaultModel, color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { providerDialog = false }) { Text("完成") } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("清除本机 AI 配置？", fontWeight = FontWeight.Bold) },
            text = { Text("将删除已保存的 API Key、服务商和模型设置。此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        store.clear();
                        savedSettings = store.load();
                        mode = savedSettings.mode;
                        providerId = savedSettings.providerId;
                        model = "";
                        apiKey = "";
                        status = "本机 AI 配置已清除";
                        statusSuccess = true;
                        clearDialog = false
                    }
                ) { Text("清除配置") }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("保留配置") } },
        )
    }
}

@Composable
private fun ConnectionStatus(settings: AiSettings, message: String, success: Boolean?) {
    val colors = appColors()
    val configured = settings.mode == AiMode.SHARED || settings.hasApiKey
    val stateSuccess = success ?: configured
    val title = when {
        message.isNotBlank() -> message
        settings.mode == AiMode.SHARED -> "站点默认 AI 已就绪"
        settings.hasApiKey -> "${settings.provider.name} · 已配置"
        else -> "尚未配置 API Key"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(
                    42.dp
                ).background(if (stateSuccess) colors.greenSoft else colors.actionOrangeSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (stateSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    null,
                    tint = if (stateSuccess) colors.success else colors.warning
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    color = if (success == false) colors.danger else colors.textPrimary
                )
                Text(
                    if (settings.mode == AiMode.SHARED) "共享模式" else settings.activeModel,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ModeOption(label: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = appColors()
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) colors.primarySoft else colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) colors.primary else colors.border
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ProviderRow(provider: AiProvider, onClick: () -> Unit) {
    val colors = appColors()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(colors.violetSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text(provider.name.take(1), color = colors.violet, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(provider.name, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                Text(provider.defaultModel, color = colors.textSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary)
        }
    }
}
