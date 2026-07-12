package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.LearningRecord
import com.xuzheng.tiyuengine.data.LearningStats
import com.xuzheng.tiyuengine.data.QuestionAttempt
import com.xuzheng.tiyuengine.data.QuestionType
import com.xuzheng.tiyuengine.data.Quiz
import com.xuzheng.tiyuengine.data.UpdateVersions
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningStatsTest {
    @Test
    fun masteryUsesWeightedSessionsWhenAttemptDetailsAreMissing() {
        val now = System.currentTimeMillis()
        val records = listOf(
            LearningRecord("quiz", "练习", 2, 5, 5, 60, now - 1_000),
            LearningRecord("quiz", "练习", 4, 5, 5, 70, now),
        )

        assertEquals(2, LearningStats.summary(records, now).quizCount)
        assertEquals(10, LearningStats.summary(records, now).questionCount)
        assertEquals(66, LearningStats.masteryPercent(records, "quiz"))
    }

    @Test
    fun masteryUsesLatestPerQuestionAcrossSessions() {
        val now = System.currentTimeMillis()
        val records = listOf(
            LearningRecord("quiz", "练习", 1, 2, 2, 60, now - 2_000, listOf(
                QuestionAttempt("a", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("b", QuestionType.SINGLE, false, "1"),
            )),
            LearningRecord("quiz", "练习", 1, 2, 2, 70, now, listOf(
                QuestionAttempt("a", QuestionType.SINGLE, false, "1"),
                QuestionAttempt("b", QuestionType.SINGLE, true, "0"),
            )),
        )

        assertEquals(50, LearningStats.masteryPercent(records, "quiz", objectiveQuestionCount = 2))
        assertEquals(50, LearningStats.masteryPercent(records, "quiz"))
    }

    @Test
    fun masteryReflectsImprovementInsteadOfLastSessionOnly() {
        val now = System.currentTimeMillis()
        val records = listOf(
            LearningRecord("quiz", "练习", 5, 5, 5, 60, now - 1_000, listOf(
                QuestionAttempt("a", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("b", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("c", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("d", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("e", QuestionType.SINGLE, true, "0"),
            )),
            LearningRecord("quiz", "练习", 1, 5, 5, 70, now, listOf(
                QuestionAttempt("a", QuestionType.SINGLE, false, "1"),
                QuestionAttempt("b", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("c", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("d", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("e", QuestionType.SINGLE, true, "0"),
            )),
        )

        assertEquals(80, LearningStats.masteryPercent(records, "quiz", objectiveQuestionCount = 5))
    }

    @Test
    fun aggregatesSevenDayTypeAndWeakestQuizStats() {
        val now = System.currentTimeMillis()
        val records = listOf(
            LearningRecord("weak", "薄弱题库", 1, 2, 2, 20, now - 1_000, listOf(
                QuestionAttempt("a", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("b", QuestionType.FILL, false, ""),
            )),
            LearningRecord("weak", "薄弱题库", 2, 2, 2, 25, now, listOf(
                QuestionAttempt("a", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("b", QuestionType.FILL, false, "x"),
            )),
            LearningRecord("strong", "优势题库", 2, 2, 2, 20, now, listOf(
                QuestionAttempt("c", QuestionType.SINGLE, true, "0"),
                QuestionAttempt("d", QuestionType.FILL, true, "ok"),
            )),
        )
        val quizzes = listOf(Quiz("strong", "优势题库", "", emptyList()), Quiz("weak", "薄弱题库", "", emptyList()))

        assertEquals(6, LearningStats.lastSevenDays(records, now).last().questionCount)
        assertEquals("weak", LearningStats.recommendedQuizId(records, quizzes))
        assertEquals("薄弱题库", LearningStats.quizAccuracy(records).first().label)
        assertEquals(50, LearningStats.quizAccuracy(records).first().percent)
        assertEquals("填空", LearningStats.typeAccuracy(records).first().label)
        assertEquals(1, LearningStats.streakDays(records, now))
    }

    @Test
    fun updateVersionComparisonHandlesPrefixesAndDifferentLengths() {
        assertEquals(true, UpdateVersions.isNewer("v1.0.2", "1.0.1"))
        assertEquals(false, UpdateVersions.isNewer("1.0.1", "1.0.1"))
        assertEquals(false, UpdateVersions.isNewer("1.0", "1.0.1"))
    }
}
