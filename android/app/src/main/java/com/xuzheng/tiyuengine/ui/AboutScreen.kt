package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.BuildConfig

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    SettingsScaffold(title = "关于题域引擎", onBack = onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(64.dp).background(Color(0xFFE3E9F3), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("题", color = Color(0xFF123A70), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
                Text("题域引擎", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("版本 ${BuildConfig.VERSION_NAME}", color = Color(0xFF64748B), fontSize = 14.sp)
                Text(
                    "工业网络 · 英语备考\n本地题库练习与学习统计",
                    color = Color(0xFF697586),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
        }
        SettingsGroup("法律信息") {
            SettingsNavRow("隐私政策", "查看公开网页版隐私说明", onClick = {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            })
        }
        Text(
            "© 2026 Xu Zheng · 保留所有权利",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
