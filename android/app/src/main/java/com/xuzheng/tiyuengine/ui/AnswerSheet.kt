package com.xuzheng.tiyuengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.AnswerBundle
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
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(
                .86f
            ).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("答题卡", fontWeight = FontWeight.Bold, fontSize = 23.sp, color = colors.textPrimary)
                TextButton(onClick = onDismiss) { Text("关闭", color = colors.textSecondary) }
            }
            Text("已完成 $answeredCount / ${quiz.questions.size}", color = colors.textSecondary, fontSize = 14.sp)
            LinearProgressIndicator(
                progress = { answeredCount.toFloat() / quiz.questions.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = colors.primary,
                trackColor = colors.progressTrack,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● 当前", color = colors.primary, fontSize = 12.sp)
                Text("● 已答", color = colors.textPrimary, fontSize = 12.sp)
                Text("○ 未答", color = colors.textSecondary, fontSize = 12.sp)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items((quiz.questions.size + 4) / 5) { rowIndex ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(5) { columnIndex ->
                            val itemIndex = rowIndex * 5 + columnIndex
                            if (itemIndex < quiz.questions.size) {
                                AnswerSheetCell(
                                    index = itemIndex,
                                    currentIndex = currentIndex,
                                    answered = answers.isAnswered(quiz.questions[itemIndex]),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onJump(itemIndex)
                                        onDismiss()
                                    },
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.primary),
                ) { Text("继续答题", color = colors.primary) }
                Button(
                    onClick = {
                        onDismiss()
                        onSubmit()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colors.actionOrange,
                        contentColor = colors.onPrimary
                    ),
                ) {
                    Text("交卷")
                }
            }
        }
    }
}

@Composable
private fun AnswerSheetCell(
    index: Int,
    currentIndex: Int,
    answered: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = appColors()
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        color = when { index == currentIndex -> colors.primary
            answered -> colors.primarySoft
            else -> colors.surface },
        border = BorderStroke(1.dp, if (index == currentIndex) colors.primary else colors.border),
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "${index + 1}",
                color = if (index == currentIndex) colors.onPrimary else if (answered) colors.primary else colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
