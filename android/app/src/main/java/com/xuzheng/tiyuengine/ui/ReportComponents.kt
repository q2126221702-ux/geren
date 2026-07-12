package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = appColors()
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            content()
        }
    }
}

@Composable
internal fun TrendRow(label: String, questions: Int, correct: Int) {
    val colors = appColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.size(width = 32.dp, height = 22.dp), color = colors.textSecondary, fontSize = 12.sp)
        LinearProgressIndicator(
            progress = { (questions.coerceAtMost(20) / 20f) },
            modifier = Modifier.weight(1f).height(8.dp),
            color = Color(0xFF00A7D6),
            trackColor = colors.progressTrack,
        )
        Text("$questions 题 · $correct 对", modifier = Modifier.padding(start = 10.dp), fontSize = 12.sp, color = colors.textSecondary)
    }
}

@Composable
internal fun StatProgress(label: String, value: Int, total: Int, color: Color) {
    val colors = appColors()
    val percent = if (total == 0) 0 else value * 100 / total
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = colors.textPrimary)
            Text("$value / $total · $percent%", fontSize = 12.sp, color = colors.textSecondary)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else value.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = color,
            trackColor = colors.progressTrack,
        )
    }
}
