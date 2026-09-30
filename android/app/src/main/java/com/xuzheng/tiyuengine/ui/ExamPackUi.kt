package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.Quiz
import kotlin.random.Random

data class LibraryEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val questionCount: Int,
    val quiz: Quiz?,
    val examVariants: List<Quiz>? = null,
)

fun buildLibraryEntries(quizzes: List<Quiz>): List<LibraryEntry> {
    val examVariants = quizzes.filter { it.id.startsWith("exam100_") }.sortedBy { it.id }
    val regular = quizzes.filter { !it.id.startsWith("exam100_") }.map { quiz ->
        LibraryEntry(quiz.id, quiz.title, quiz.subtitle, quiz.questions.size, quiz)
    }
    if (examVariants.isEmpty()) return regular
    val packTitle = examVariants.first().title.substringBefore("（").ifBlank { "工业网络技术期末考核" }
    return regular + LibraryEntry(
        id = "exam100_pack",
        title = packTitle,
        subtitle = "工业网络 · 期末模拟卷 · ${examVariants.size} 套可选",
        questionCount = examVariants.first().questions.size,
        quiz = null,
        examVariants = examVariants,
    )
}

fun examVariantLabel(quiz: Quiz): String {
    val suffix = quiz.id.substringAfterLast('_').uppercase()
    return "试卷 $suffix"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamPickerSheet(variants: List<Quiz>, onDismiss: () -> Unit, onSelect: (Quiz) -> Unit) {
    val colors = appColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedId by remember(variants) { mutableStateOf(variants.firstOrNull()?.id) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择试卷", fontWeight = FontWeight.Bold, fontSize = 23.sp, color = colors.textPrimary)
                TextButton(onClick = onDismiss) { Text("关闭", color = colors.textSecondary) }
            }
            Text("期末模拟卷 · ${variants.size} 套固定试卷", color = colors.textSecondary, fontSize = 14.sp)
            Surface(
                onClick = { if (variants.isNotEmpty()) onSelect(variants[Random.nextInt(variants.size)]) },
                color = colors.greenSoft,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("随机选卷", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.textPrimary)
                        Text("从 ${variants.size} 套固定试卷中随机抽取一套", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Text("→", color = colors.success, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            variants.chunked(2).forEach { rowVariants ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    rowVariants.forEach { variant ->
                        val selected = variant.id == selectedId
                        Surface(
                            onClick = { selectedId = variant.id },
                            modifier = Modifier.weight(1f).height(64.dp),
                            color = if (selected) colors.primary else colors.surface,
                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.border),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    examVariantLabel(variant),
                                    color = if (selected) colors.onPrimary else colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (selected) "◉" else "○",
                                    color = if (selected) colors.onPrimary else colors.textSecondary,
                                    fontSize = 19.sp
                                )
                            }
                        }
                    }
                    if (rowVariants.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Button(
                onClick = { variants.find { it.id == selectedId }?.let(onSelect) },
                enabled = selectedId != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colors.actionOrange,
                    contentColor = colors.onPrimary
                ),
            ) { Text("开始练习", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(4.dp))
        }
    }
}
