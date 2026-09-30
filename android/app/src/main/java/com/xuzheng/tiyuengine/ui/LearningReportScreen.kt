package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.AccuracyStat
import com.xuzheng.tiyuengine.data.DailyLearning
import com.xuzheng.tiyuengine.data.LearningRecord
import com.xuzheng.tiyuengine.data.LearningStats
import com.xuzheng.tiyuengine.data.Quiz
import com.xuzheng.tiyuengine.data.ReviewStatus
import com.xuzheng.tiyuengine.data.WrongItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LearningReportScreen(
    records: List<LearningRecord>,
    quizzes: List<Quiz>,
    wrongItems: List<WrongItem>,
    onBack: () -> Unit,
) {
    val colors = appColors()
    val summary = LearningStats.summary(records)
    val days = LearningStats.lastSevenDays(records)
    val quizAccuracy = LearningStats.quizAccuracy(records)
    val typeAccuracy = LearningStats.typeAccuracy(records)
    val unmastered = wrongItems.count { it.status == ReviewStatus.UNMASTERED }
    val reviewing = wrongItems.count { it.status == ReviewStatus.REVIEWING }
    val mastered = wrongItems.count { it.status == ReviewStatus.MASTERED }

    Scaffold(containerColor = colors.pageBackground) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ReportHeader(onBack) }
            item {
                ReportCard("学习概览", Modifier.padding(horizontal = 20.dp)) {
                    ReportOverview(
                        summary.questionCount,
                        summary.quizCount,
                        summary.todayQuestionCount,
                        LearningStats.streakDays(records),
                    )
                }
            }
            item {
                ReportCard("近 7 天趋势", Modifier.padding(horizontal = 20.dp)) {
                    SevenDayChart(days)
                }
            }
            item {
                ReportCard("错题掌握进度", Modifier.padding(horizontal = 20.dp)) {
                    MasterySummary(unmastered, reviewing, mastered)
                }
            }
            if (quizAccuracy.isNotEmpty()) {
                item {
                    ReportCard("薄弱题库", Modifier.padding(horizontal = 20.dp)) {
                        quizAccuracy.take(5).forEach {
                            StatProgress(it.label, it.correct, it.total, colors.violet)
                        }
                    }
                }
            }
            if (typeAccuracy.isNotEmpty()) {
                item {
                    ReportCard("题型正确率", Modifier.padding(horizontal = 20.dp)) {
                        typeAccuracy.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                row.forEach { AccuracyTile(it, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    ReportCard("最近测验", Modifier.padding(horizontal = 20.dp)) {
                        records.takeLast(5).reversed().forEachIndexed { index, record ->
                            if (index > 0) HorizontalDivider(color = colors.border)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        record.quizTitle,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatDateTime(record.submittedAt),
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    "${if (record.total == 0) 0 else record.score * 100 / record.total}%",
                                    modifier = Modifier.padding(start = 12.dp),
                                    color = colors.primary,
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
private fun ReportHeader(onBack: () -> Unit) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.heroSky)
            .padding(start = 8.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.textPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("学习报告", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("学习情况一目了然", color = colors.textSecondary, fontSize = 14.sp)
        }
        Row(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.68f))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(18.dp, 34.dp, 26.dp).forEachIndexed { index, height ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index == 2) colors.success else colors.primary)
                )
            }
        }
    }
}

@Composable
private fun ReportOverview(questionCount: Int, quizCount: Int, todayCount: Int, streakDays: Int) {
    val colors = appColors()
    BoxWithConstraints {
        val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    OverviewMetric("累计答题", "$questionCount 道", Modifier.weight(1f))
                    OverviewMetric("累计测验", "$quizCount 次", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    OverviewMetric("今日已完成", "$todayCount 道", Modifier.weight(1f))
                    OverviewMetric("连续学习", "$streakDays 天", Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    OverviewMetric("累计答题", "$questionCount 道", Modifier.weight(1f))
                    OverviewMetric("累计测验", "$quizCount 次", Modifier.weight(1f))
                    OverviewMetric("今日已完成", "$todayCount 道", Modifier.weight(1f))
                }
                Text("连续学习 $streakDays 天", color = colors.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = appColors()
    Column(modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 2)
        Text(label, color = colors.textSecondary, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
private fun SevenDayChart(days: List<DailyLearning>) {
    val colors = appColors()
    val maxCount = (days.maxOfOrNull { it.questionCount } ?: 0).coerceAtLeast(1)
    val dayFormat = SimpleDateFormat("MM/dd", Locale.CHINA)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { index, day ->
            val label = dayFormat.format(Date(day.dayStart))
            val barHeight = if (day.questionCount == 0) 4.dp else (96f * day.questionCount / maxCount).dp
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(146.dp)
                    .semantics { contentDescription = "$label，答题${day.questionCount}道，答对${day.correctCount}道" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${day.questionCount}", color = colors.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            when {
                                day.questionCount == 0 -> colors.progressTrack
                                index == days.lastIndex -> colors.success
                                else -> colors.primary
                            }
                        )
                )
                Spacer(Modifier.height(8.dp))
                Text(label, color = colors.textSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MasterySummary(unmastered: Int, reviewing: Int, mastered: Int) {
    val colors = appColors()
    val total = unmastered + reviewing + mastered
    Text("$mastered / $total 已掌握", color = colors.textSecondary, fontSize = 13.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.progressTrack),
    ) {
        if (unmastered > 0) Box(Modifier.weight(unmastered.toFloat()).fillMaxHeight().background(colors.actionOrange))
        if (reviewing > 0) Box(Modifier.weight(reviewing.toFloat()).fillMaxHeight().background(colors.primary))
        if (mastered > 0) Box(Modifier.weight(mastered.toFloat()).fillMaxHeight().background(colors.success))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MasteryLegend("未掌握", unmastered, colors.actionOrange, Modifier.weight(1f))
        MasteryLegend("复习中", reviewing, colors.primary, Modifier.weight(1f))
        MasteryLegend("已掌握", mastered, colors.success, Modifier.weight(1f))
    }
}

@Composable
private fun MasteryLegend(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    val colors = appColors()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(8.dp)).background(color))
        Text("$label $count", color = colors.textSecondary, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun AccuracyTile(stat: AccuracyStat, modifier: Modifier = Modifier) {
    val colors = appColors()
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stat.label, color = colors.textSecondary, fontSize = 13.sp, maxLines = 2)
        Text("${stat.percent}%", color = colors.success, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("${stat.correct} / ${stat.total}", color = colors.textSecondary, fontSize = 12.sp)
    }
}
