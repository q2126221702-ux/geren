package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.QuestionType
import com.xuzheng.tiyuengine.ui.shouldAutoAdvance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAdvanceTest {
    @Test
    fun singleAndJudgmentAdvanceWhenAnswerChanges() {
        assertTrue(shouldAutoAdvance(QuestionType.SINGLE, emptySet(), 1, 0, 2))
        assertTrue(shouldAutoAdvance(QuestionType.TRUE_FALSE, setOf(0), 1, 0, 2))
    }

    @Test
    fun sameAnswerMultipleChoiceAndLastQuestionStayPut() {
        assertFalse(shouldAutoAdvance(QuestionType.SINGLE, setOf(1), 1, 0, 2))
        assertFalse(shouldAutoAdvance(QuestionType.MULTIPLE, emptySet(), 1, 0, 2))
        assertFalse(shouldAutoAdvance(QuestionType.SINGLE, emptySet(), 1, 2, 2))
    }
}
