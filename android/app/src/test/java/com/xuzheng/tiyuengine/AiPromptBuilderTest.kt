package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.AiPromptBuilder
import com.xuzheng.tiyuengine.data.AiProviderCatalog
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.QuestionType
import com.xuzheng.tiyuengine.data.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptBuilderTest {
    @Test
    fun questionPromptContainsStudentAndReferenceAnswers() {
        val question = Question("q1", "工业协议是什么？", listOf("PROFINET", "HTTP"), 0, "", QuestionType.SINGLE)
        val prompt = AiPromptBuilder.question(question, AnswerBundle(optionAnswers = mapOf("q1" to setOf(1))), full = true)

        assertTrue(prompt.contains("参考答案：PROFINET"))
        assertTrue(prompt.contains("学生作答：HTTP"))
        assertTrue(prompt.contains("错因诊断"))
    }

    @Test
    fun essayPromptRequiresScoreOnFirstLine() {
        val question = Question("q2", "简述作用", emptyList(), 0, "", QuestionType.ESSAY, referenceAnswer = "提高可靠性")
        val prompt = AiPromptBuilder.question(question, AnswerBundle(textAnswers = mapOf("q2" to "减少故障")), full = false)

        assertTrue(prompt.contains("【得分】X/10"))
        assertTrue(prompt.contains("参考答案：提高可靠性"))
    }

    @Test
    fun analysisUsesWrongQuestionNumbersAndCatalogMatchesWeb() {
        val questions = listOf(
            Question("q1", "题一", listOf("对", "错"), 0, ""),
            Question("q2", "题二", listOf("对", "错"), 0, ""),
        )
        val prompt = AiPromptBuilder.analysis(Quiz("quiz", "测试", "", questions), 1, AnswerBundle(optionAnswers = mapOf("q1" to setOf(0), "q2" to setOf(1))), full = true)

        assertTrue(prompt.contains("第2题"))
        assertEquals(9, AiProviderCatalog.providers.size)
    }
}
