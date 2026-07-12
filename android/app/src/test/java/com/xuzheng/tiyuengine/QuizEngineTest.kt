package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.QuizEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizEngineTest {
    private val questions = listOf(
        Question("1", "Q1", listOf("A", "B"), 0, ""),
        Question("2", "Q2", listOf("A", "B"), 1, ""),
    )

    @Test
    fun scoreCountsCorrectAnswersAndTreatsMissingAsWrong() {
        assertEquals(1, QuizEngine.score(questions, AnswerBundle(optionAnswers = mapOf("1" to setOf(0)))))
    }

    @Test
    fun scoreReturnsFullMarksForAllCorrectAnswers() {
        assertEquals(2, QuizEngine.score(questions, AnswerBundle(optionAnswers = mapOf("1" to setOf(0), "2" to setOf(1)))))
    }
}
