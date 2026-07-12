package com.xuzheng.tiyuengine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.ReviewStatus
import com.xuzheng.tiyuengine.data.WrongBookStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WrongBookStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val question = Question("review_test", "测试题", listOf("正确", "错误"), 0, "")
    private val correctAnswer = AnswerBundle(optionAnswers = mapOf(question.id to setOf(0)))

    @Before
    fun clearStore() {
        context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun reviewProgressesToMasteredAfterThreeCorrectAnswers() {
        val store = WrongBookStore(context)
        store.updateAfterSubmission(listOf(question), AnswerBundle(), isReview = false)
        assertEquals(ReviewStatus.UNMASTERED, store.loadItems().single().status)

        store.updateAfterSubmission(listOf(question), correctAnswer, isReview = true)
        assertEquals(ReviewStatus.REVIEWING, store.loadItems().single().status)
        assertTrue(store.loadItems().single().nextReviewAt > System.currentTimeMillis())

        store.updateAfterSubmission(listOf(question), correctAnswer, isReview = true)
        store.updateAfterSubmission(listOf(question), correctAnswer, isReview = true)
        assertEquals(ReviewStatus.MASTERED, store.loadItems().single().status)
        assertTrue(question.id !in store.loadIds())
    }
}
