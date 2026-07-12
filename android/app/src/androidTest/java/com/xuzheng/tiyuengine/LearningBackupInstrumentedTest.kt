package com.xuzheng.tiyuengine

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuzheng.tiyuengine.data.LearningBackup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningBackupInstrumentedTest {
    @Test
    fun exportAndRestorePreservesLearningAndWrongRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val learning = context.getSharedPreferences("learning_history", Context.MODE_PRIVATE)
        val wrong = context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE)
        val favorites = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        learning.edit().putString("records", "[{\"quizId\":\"quiz\"}]").commit()
        wrong.edit().putString("items_v2", "[{\"questionId\":\"q1\"}]").commit()
        favorites.edit().putStringSet("question_ids", setOf("q1", "q2")).commit()

        val backup = LearningBackup(context)
        val json = backup.exportJson(now = 1_234L)
        learning.edit().clear().commit()
        wrong.edit().clear().commit()
        favorites.edit().clear().commit()
        val restored = backup.restore(json)

        assertEquals(1_234L, restored.exportedAt)
        assertEquals(1, restored.learningRecordCount)
        assertEquals(1, restored.wrongItemCount)
        assertEquals(2, restored.favoriteCount)
        assertEquals("[{\"quizId\":\"quiz\"}]", learning.getString("records", null))
        assertEquals("[{\"questionId\":\"q1\"}]", wrong.getString("items_v2", null))
        assertEquals(setOf("q1", "q2"), favorites.getStringSet("question_ids", emptySet()))
    }
}
