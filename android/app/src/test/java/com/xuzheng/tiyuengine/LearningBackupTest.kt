package com.xuzheng.tiyuengine

import android.content.Context
import com.xuzheng.tiyuengine.data.LearningBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningBackupTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun exportAndRestoreRoundTripPreservesRecords() {
        val learning = context.getSharedPreferences("learning_history", Context.MODE_PRIVATE)
        val wrong = context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE)
        val favorites = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        learning.edit().putString("records", "[{\"quizId\":\"quiz\"}]").commit()
        wrong.edit().putString("items_v2", "[{\"questionId\":\"q1\"}]").commit()
        favorites.edit().putStringSet("question_ids", setOf("q1", "q2")).commit()

        val backup = LearningBackup(context)
        val json = backup.exportJson(now = 9_999L)
        learning.edit().clear().commit()
        wrong.edit().clear().commit()
        favorites.edit().clear().commit()

        val restored = backup.restore(json)
        assertEquals(9_999L, restored.exportedAt)
        assertEquals(1, restored.learningRecordCount)
        assertEquals(1, restored.wrongItemCount)
        assertEquals(2, restored.favoriteCount)
        assertEquals("[{\"quizId\":\"quiz\"}]", learning.getString("records", null))
        assertEquals("[{\"questionId\":\"q1\"}]", wrong.getString("items_v2", null))
        assertEquals(setOf("q1", "q2"), favorites.getStringSet("question_ids", emptySet()))
    }

    @Test
    fun previewAcceptsBackupWithoutFavoriteIds() {
        val json = """
            {
              "format": "tiyuengine-learning-backup",
              "schemaVersion": 1,
              "exportedAt": 100,
              "learningRecords": [],
              "wrongItems": []
            }
        """.trimIndent()

        val preview = LearningBackup(context).preview(json)
        assertEquals(100L, preview.exportedAt)
        assertEquals(0, preview.favoriteCount)
    }

    @Test
    fun rejectsInvalidBackupFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            LearningBackup(context).preview("""{"format":"other"}""")
        }
    }

    @Test
    fun rejectsOversizedBackupPayload() {
        val huge = " ".repeat(5 * 1024 * 1024 + 1)
        assertThrows(IllegalArgumentException::class.java) {
            LearningBackup(context).preview(huge)
        }
    }
}
