package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.Quiz

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnswerSheetBottomSheet(
    quiz: Quiz,
    currentIndex: Int,
    answers: AnswerBundle,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = appColors()
    val answeredCount = quiz.questions.count { answers.isAnswered(it) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("答题卡", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.textPrimary)
            Text(
                "已答 $answeredCount 题，未答 ${quiz.questions.size - answeredCount} 题",
                color = colors.textSecondary,
                fontSize = 13.sp,
            )
            quiz.questions.chunked(5).forEach { rowQuestions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowQuestions.forEach { item ->
                        val itemIndex = quiz.questions.indexOf(item)
                        AnswerSheetCell(
                            index = itemIndex,
                            currentIndex = currentIndex,
                            answered = answers.isAnswered(item),
                            onClick = { onJump(itemIndex); onDismiss() },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("继续答题") }
                Button(onClick = { onDismiss(); onSubmit() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("交卷")
                }
            }
        }
    }
}

@Composable
private fun AnswerSheetCell(index: Int, currentIndex: Int, answered: Boolean, onClick: () -> Unit) {
    val colors = appColors()
    val background = when {
        index == currentIndex -> colors.primary
        answered -> colors.textPrimary
        else -> colors.progressTrack
    }
    val textColor = if (index == currentIndex || answered) Color.White else colors.textSecondary
    Box(
        Modifier
            .size(52.dp)
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("${index + 1}", color = textColor, fontWeight = FontWeight.Bold)
    }
}
