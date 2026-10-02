package com.xuzheng.tiyuengine

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuzheng.tiyuengine.data.LearningBackup
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LearningBackupInstrumentedTest {
    @Test
    fun exportAndRestorePreservesLearningAndWrongRecords() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testPrefix = "backup_test_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(testPrefix + name, mode)
            override fun getNoBackupFilesDir(): File = File(target.cacheDir, testPrefix).apply { mkdirs() }
        }
        val learning = context.getSharedPreferences("learning_history", Context.MODE_PRIVATE)
        val wrong = context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE)
        val favorites = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val records = """[{"quizId":"quiz","quizTitle":"测试题库","score":1,"total":1,"questionCount":1,""" +
            """"durationSeconds":20,"submittedAt":100,"attempts":[]}]"""
        val wrongItems = """[{"questionId":"q1","wrongTimes":1,"correctStreak":0,""" +
            """"lastWrongAt":100,"lastReviewedAt":0,"nextReviewAt":100}]"""
        learning.edit().putString("records", records).commit()
        wrong.edit().putString("items_v2", wrongItems).commit()
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
        assertEquals(records, learning.getString("records", null))
        assertEquals(wrongItems, wrong.getString("items_v2", null))
        assertEquals(setOf("q1", "q2"), favorites.getStringSet("question_ids", emptySet()))
        listOf("learning_history", "wrong_book", "favorites").forEach {
            target.deleteSharedPreferences(testPrefix + it)
        }
        context.noBackupFilesDir.delete()
    }
}
