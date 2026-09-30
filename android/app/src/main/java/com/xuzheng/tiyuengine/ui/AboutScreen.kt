package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.BuildConfig
import com.xuzheng.tiyuengine.R

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = appColors()

    SettingsScaffold(title = "关于题域引擎", subtitle = "应用信息与说明", onBack = onBack, icon = Icons.Default.Info) {
        Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(22.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_art),
                    contentDescription = "题域引擎应用图标",
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)),
                )
                Text("题域引擎", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("工业网络 · 英语备考", color = colors.textSecondary, fontSize = 15.sp)
                Text("版本 ${BuildConfig.VERSION_NAME}", color = colors.textSecondary, fontSize = 13.sp)
                Text(
                    "本地题库练习与学习统计",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
        }
        SettingsGroup("法律信息") {
            SettingsNavRow("隐私政策", "查看公开网页版隐私说明", icon = Icons.Default.PrivacyTip, onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(PRIVACY_POLICY_URL)
                    )
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            })
        }
        SettingsGroup("应用说明") {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("支持本地题库的练习与复习。", color = colors.textPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                Text(
                    "学习记录默认保存在本机。使用 AI 功能时，题目与作答会发送给所选服务商处理。",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
        Text(
            "© 2026 Xu Zheng · 保留所有权利",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = colors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
