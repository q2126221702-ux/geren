package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("期末模拟卷", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.textPrimary)
            Text("选择固定试卷，或随机抽取一套", color = colors.textSecondary, fontSize = 13.sp)
            Button(
                onClick = { onSelect(variants[Random.nextInt(variants.size)]) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("随机抽卷") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(variants) { variant ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(variant) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(examVariantLabel(variant), fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text("${variant.questions.size} 题", color = colors.textSecondary, fontSize = 12.sp)
                        }
                        Text("开始", color = colors.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
