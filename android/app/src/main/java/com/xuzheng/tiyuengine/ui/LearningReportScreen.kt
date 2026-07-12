package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.LearningRecord
import com.xuzheng.tiyuengine.data.LearningStats
import com.xuzheng.tiyuengine.data.Quiz
import com.xuzheng.tiyuengine.data.ReviewStatus
import com.xuzheng.tiyuengine.data.WrongItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearningReportScreen(
    records: List<LearningRecord>,
    quizzes: List<Quiz>,
    wrongItems: List<WrongItem>,
    onBack: () -> Unit,
) {
    val summary = LearningStats.summary(records)
    val days = LearningStats.lastSevenDays(records)
    val quizAccuracy = LearningStats.quizAccuracy(records)
    val typeAccuracy = LearningStats.typeAccuracy(records)
    val unmastered = wrongItems.count { it.status == ReviewStatus.UNMASTERED }
    val reviewing = wrongItems.count { it.status == ReviewStatus.REVIEWING }
    val mastered = wrongItems.count { it.status == ReviewStatus.MASTERED }

    Scaffold(
        containerColor = Color(0xFFF4F6FA),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("学习报告", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("趋势、薄弱点与最近测验", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RowMetrics(summary.questionCount, summary.quizCount, summary.todayQuestionCount, LearningStats.streakDays(records)) }
            item {
                ReportCard("近 7 天趋势") {
                    days.forEach { day ->
                        TrendRow(SimpleDateFormat("E", Locale.CHINA).format(Date(day.dayStart)), day.questionCount, day.correctCount)
                    }
                }
            }
            item {
                ReportCard("错题掌握进度") {
                    StatProgress("未掌握", unmastered, wrongItems.size, Color(0xFFE15B64))
                    StatProgress("复习中", reviewing, wrongItems.size, Color(0xFFE4A33A))
                    StatProgress("已掌握", mastered, wrongItems.size, Color(0xFF20A66A))
                }
            }
            if (quizAccuracy.isNotEmpty()) {
                item {
                    ReportCard("薄弱题库") {
                        quizAccuracy.take(5).forEach { StatProgress(it.label, it.correct, it.total, Color(0xFF0759BD)) }
                    }
                }
            }
            if (typeAccuracy.isNotEmpty()) {
                item {
                    ReportCard("题型正确率") {
                        typeAccuracy.forEach { StatProgress(it.label, it.correct, it.total, Color(0xFF6B4CC5)) }
                    }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    ReportCard("最近测验") {
                        records.takeLast(5).reversed().forEach { record ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.quizTitle, fontWeight = FontWeight.SemiBold)
                                    Text(formatDateTime(record.submittedAt), color = Color(0xFF64748B), fontSize = 12.sp)
                                }
                                Text(
                                    "${if (record.total == 0) 0 else record.score * 100 / record.total}%",
                                    color = Color(0xFF0759BD),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowMetrics(questionCount: Int, quizCount: Int, todayCount: Int, streakDays: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMetric("累计答题", "$questionCount 道", Modifier.weight(1f))
            MiniMetric("累计测验", "$quizCount 次", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniMetric("今日已完成", "$todayCount 道", Modifier.weight(1f))
            MiniMetric("连续学习", "$streakDays 天", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF123A70))
        Text(label, color = Color(0xFF697586), fontSize = 13.sp)
    }
}
