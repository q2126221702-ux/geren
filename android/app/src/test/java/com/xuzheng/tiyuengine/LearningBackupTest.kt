package com.xuzheng.tiyuengine

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.xuzheng.tiyuengine.data.AnswerBundle
import com.xuzheng.tiyuengine.data.FavoriteStore
import com.xuzheng.tiyuengine.data.LearningBackup
import com.xuzheng.tiyuengine.data.LearningStore
import com.xuzheng.tiyuengine.data.Question
import com.xuzheng.tiyuengine.data.WrongBookStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningBackupTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferenceNames = listOf("learning_history", "wrong_book", "favorites")
    private val journal get() = File(context.noBackupFilesDir, "learning_restore_pending.json")

    @Before
    fun seedOriginalData() {
        preferenceNames.forEach { prefs(it).edit().clear().commit() }
        journal.delete()
        prefs("learning_history").edit().putString("records", JSONArray().put(record()).toString()).commit()
        prefs("wrong_book").edit().putString("items_v2", JSONArray().put(wrongItem()).toString())
            .putStringSet("question_ids", setOf("legacy_q1")).commit()
        prefs("favorites").edit().putStringSet("question_ids", setOf("q1", "q2")).commit()
    }

    @Test
    fun exportAndRestoreRoundTripPreservesUsableRecords() {
        val backup = LearningBackup(context)
        val expectedRecords = LearningStore(context).load()
        val expectedWrong = WrongBookStore(context).loadItems()
        val json = backup.exportJson(now = 9_999L)
        preferenceNames.forEach { prefs(it).edit().clear().commit() }

        val restored = backup.restore(json)

        assertEquals(9_999L, restored.exportedAt)
        assertEquals(1, restored.learningRecordCount)
        assertEquals(1, restored.wrongItemCount)
        assertEquals(2, restored.favoriteCount)
        assertEquals(expectedRecords, LearningStore(context).load())
        assertEquals(expectedWrong, WrongBookStore(context).loadItems())
        assertEquals(setOf("q1", "q2"), FavoriteStore(context).loadIds())
        assertFalse(prefs("wrong_book").contains("question_ids"))
        assertFalse(journal.exists())
    }

    @Test
    fun oldBackupsWithoutFavoritesAttemptsOrAnswersRemainCompatible() {
        val old = backup()
        old.remove("favoriteIds")
        old.getJSONArray("learningRecords").getJSONObject(0).remove("attempts")
        val restored = LearningBackup(context).restore(old.toString())
        assertEquals(0, restored.favoriteCount)
        assertTrue(LearningStore(context).load().single().attempts.isEmpty())

        val noAnswer = backup()
        noAnswer.getJSONArray("learningRecords").getJSONObject(0)
            .getJSONArray("attempts").getJSONObject(0).remove("userAnswer")
        LearningBackup(context).restore(noAnswer.toString())
        assertEquals("", LearningStore(context).load().single().attempts.single().userAnswer)
    }

    @Test
    fun rejectsInvalidRecordsBeforeAnyPreferenceChanges() {
        val invalid = mutableListOf<String>()
        fun changed(change: (JSONObject) -> Unit) { invalid += backup().also(change).toString() }
        changed { it.put("learningRecords", JSONArray().put(JSONObject())) }
        changed { it.put("wrongItems", JSONArray().put(JSONObject())) }
        changed { it.put("learningRecords", JSONArray().put(JSONObject.NULL)) }
        changed { it.put("learningRecords", JSONArray().put("record")) }
        changed { it.put("schemaVersion", "1") }
        changed { it.put("exportedAt", Long.MAX_VALUE) }
        changed { it.put("wrongItems", JSONObject.NULL) }
        val invalidRecordChanges: List<(JSONObject) -> Unit> = listOf(
            { it.remove("quizId") },
            { it.put("quizId", "") },
            { it.put("quizTitle", "x".repeat(4_097)) },
            { it.put("score", "1") },
            { it.put("score", 1.5) },
            { it.put("score", -1) },
            { it.put("score", 2) },
            { it.put("questionCount", 0) },
            { it.put("durationSeconds", -1) },
            { it.put("submittedAt", Long.MAX_VALUE) },
            { it.put("attempts", "not an array") },
            { it.getJSONArray("attempts").getJSONObject(0).put("type", "INVALID") },
            { it.getJSONArray("attempts").getJSONObject(0).put("correct", "true") },
            { it.getJSONArray("attempts").getJSONObject(0).put("userAnswer", 42) },
            { it.getJSONArray("attempts").getJSONObject(0).put("userAnswer", "x".repeat(65_537)) },
            {
                it.put("questionCount", 2)
                it.getJSONArray("attempts").put(attempt())
            },
        )
        invalidRecordChanges.forEach { change ->
            changed { change(it.getJSONArray("learningRecords").getJSONObject(0)) }
        }
        changed { it.getJSONArray("wrongItems").put(wrongItem()) }
        changed { it.getJSONArray("wrongItems").getJSONObject(0).put("correctStreak", 4) }
        changed { it.getJSONArray("wrongItems").getJSONObject(0).put("wrongTimes", -1) }
        changed { it.put("favoriteIds", JSONObject()) }
        changed { it.put("favoriteIds", JSONObject.NULL) }
        changed { it.put("favoriteIds", JSONArray().put(JSONObject.NULL)) }
        changed { it.put("favoriteIds", JSONArray().put(42)) }
        changed { it.put("favoriteIds", JSONArray().put("q1").put("q1")) }
        changed { it.put("favoriteIds", JSONArray().put("q".repeat(513))) }
        changed { root -> root.put("favoriteIds", JSONArray().apply { repeat(10_001) { put("q$it") } }) }
        changed { root -> root.put("wrongItems", JSONArray().apply { repeat(10_001) { put(wrongItem("q$it")) } }) }

        val original = snapshot()
        invalid.forEachIndexed { index, json ->
            assertThrows("invalid input #$index", IllegalArgumentException::class.java) {
                LearningBackup(context).restore(json)
            }
            assertEquals("input #$index changed data", original, snapshot())
            assertFalse("input #$index created a journal", journal.exists())
        }
    }

    @Test
    fun rejectsDeepOrNonstandardJsonBeforeRecursiveParsing() {
        val nested = "{\"format\":\"tiyuengine-learning-backup\",\"x\":" + "[".repeat(100) + "0" + "]".repeat(100) + "}"
        val nonstandard = "{'format':'tiyuengine-learning-backup','x':" + "[".repeat(100) + "0" + "]".repeat(100) + "}"
        listOf(nested, nonstandard).forEach {
            assertThrows(IllegalArgumentException::class.java) { LearningBackup(context).preview(it) }
        }
    }

    @Test
    fun boundedReadStopsAtLimitWithoutConsumingRemainingInput() {
        val maximum = 5L * 1024 * 1024
        var consumed = 0L
        val stream = object : InputStream() {
            override fun read(): Int {
                consumed++
                return ' '.code
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill(' '.code.toByte(), offset, offset + length)
                consumed += length
                return length
            }
        }
        assertThrows(IOException::class.java) { LearningBackup(context).readJson(stream) }
        assertTrue(consumed <= maximum + 1)
        assertTrue(consumed > maximum)
    }

    @Test
    fun boundedReadPreservesUtf8AndRejectsOversizedStringApiInput() {
        val json = backup().put("comment", "中文与 😀").toString()
        val read = json.byteInputStream().use { LearningBackup(context).readJson(it) }
        assertEquals(json, read)
        assertEquals(1, LearningBackup(context).preview(read).learningRecordCount)
        assertThrows(IllegalArgumentException::class.java) {
            LearningBackup(context).preview(" ".repeat(5 * 1024 * 1024 + 1))
        }
    }

    @Test
    fun commitFailureRollsBackAllThreePreferencesIncludingLegacyKeys() {
        val original = snapshot()
        var failOnce = true
        val failing = interceptCommit("wrong_book") { editor ->
            val committed = editor.commit()
            if (failOnce) {
                failOnce = false
                false
            } else {
                committed
            }
        }
        assertThrows(IOException::class.java) { LearningBackup(failing).restore(emptyBackup()) }
        assertEquals(original, snapshot())
        assertFalse(journal.exists())
    }

    @Test
    fun interruptedRestoreIsRolledBackBeforeTheNextStoreRead() {
        val original = snapshot()
        val interrupted = interceptCommit("wrong_book") { editor ->
            editor.commit()
            throw SimulatedProcessDeath()
        }
        assertThrows(SimulatedProcessDeath::class.java) { LearningBackup(interrupted).restore(emptyBackup()) }
        assertTrue(journal.exists())
        assertEquals("[]", prefs("learning_history").getString("records", null))

        // A fresh store follows the same recovery path as app startup after process death.
        assertEquals("quiz", LearningStore(context).load().single().quizId)
        assertEquals(original, snapshot())
        assertFalse(journal.exists())
    }

    @Test
    fun failedRollbackKeepsJournalUntilAHealthyStoreCanRecover() {
        val original = snapshot()
        val failing = interceptCommit("wrong_book") { editor ->
            editor.commit()
            false
        }
        assertThrows(IOException::class.java) { LearningBackup(failing).restore(emptyBackup()) }
        assertTrue(journal.exists())

        assertEquals(setOf("q1", "q2"), FavoriteStore(context).loadIds())
        assertEquals(original, snapshot())
        assertFalse(journal.exists())
    }

    @Test
    fun incompleteStagingFileCannotTriggerRollback() {
        val original = snapshot()
        File(context.noBackupFilesDir, "learning_restore_pending.json.staging").writeText("{partial")
        assertEquals("quiz", LearningStore(context).load().single().quizId)
        assertEquals(original, snapshot())
    }

    @Test
    fun maximumWrongCountSurvivesRepeatedAnswersAndBackupRoundTrip() {
        val json = backup().apply {
            getJSONArray("wrongItems").getJSONObject(0).put("wrongTimes", Int.MAX_VALUE)
        }.toString()
        val backup = LearningBackup(context)
        backup.restore(json)
        val store = WrongBookStore(context)
        val question = Question("q1", "测试题目", listOf("正确", "错误"), 0, "解释")
        val answers = AnswerBundle(optionAnswers = mapOf("q1" to setOf(1)))

        repeat(2) { store.updateAfterSubmission(listOf(question), answers, isReview = true) }

        assertEquals(Int.MAX_VALUE, store.loadItems().single().wrongTimes)
        backup.restore(backup.exportJson())
        assertEquals(Int.MAX_VALUE, store.loadItems().single().wrongTimes)
    }

    @Test
    fun correctReviewStreakRemainsAtMaximumAfterFurtherAnswers() {
        val json = backup().apply {
            getJSONArray("wrongItems").getJSONObject(0).put("correctStreak", 3)
        }.toString()
        LearningBackup(context).restore(json)
        val store = WrongBookStore(context)
        val question = Question("q1", "测试题目", listOf("正确", "错误"), 0, "解释")
        val answers = AnswerBundle(optionAnswers = mapOf("q1" to setOf(0)))

        repeat(2) { store.updateAfterSubmission(listOf(question), answers, isReview = true) }

        assertEquals(3, store.loadItems().single().correctStreak)
    }

    private class SimulatedProcessDeath : Error()

    private fun interceptCommit(name: String, operation: (SharedPreferences.Editor) -> Boolean): Context =
        object : ContextWrapper(context) {
            override fun getSharedPreferences(requestedName: String, mode: Int): SharedPreferences {
                val actual = super.getSharedPreferences(requestedName, mode)
                if (requestedName != name) return actual
                return object : SharedPreferences by actual {
                    override fun edit(): SharedPreferences.Editor {
                        val actualEditor = actual.edit()
                        return object : SharedPreferences.Editor by actualEditor {
                            // Fluent editor methods must retain this interceptor.
                            override fun putString(key: String?, value: String?) = apply {
                                actualEditor.putString(key, value)
                            }
                            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                                actualEditor.putStringSet(key, values)
                            }
                            override fun remove(key: String?) = apply { actualEditor.remove(key) }
                            override fun commit(): Boolean = operation(actualEditor)
                        }
                    }
                }
            }
        }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun snapshot(): List<Map<String, Any?>> = preferenceNames.map { name ->
        prefs(name).all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
    }

    private fun emptyBackup() = backup()
        .put("learningRecords", JSONArray())
        .put("wrongItems", JSONArray())
        .put("favoriteIds", JSONArray())
        .toString()
    private fun backup() = JSONObject().apply {
        put("format", "tiyuengine-learning-backup")
        put("schemaVersion", 1)
        put("exportedAt", 100)
        put("learningRecords", JSONArray().put(record()))
        put("wrongItems", JSONArray().put(wrongItem()))
        put("favoriteIds", JSONArray().put("q1").put("q2"))
    }

    private fun record() = JSONObject().apply {
        put("quizId", "quiz")
        put("quizTitle", "测试题库")
        put("score", 1)
        put("total", 1)
        put("questionCount", 1)
        put("durationSeconds", 20)
        put("submittedAt", 100)
        put("attempts", JSONArray().put(attempt()))
    }

    private fun attempt() = JSONObject().apply {
        put("questionId", "q1")
        put("type", "SINGLE")
        put("correct", true)
        put("userAnswer", "0")
    }

    private fun wrongItem(id: String = "q1") = JSONObject().apply {
        put("questionId", id)
        put("wrongTimes", 2)
        put("correctStreak", 0)
        put("lastWrongAt", 90)
        put("lastReviewedAt", 0)
        put("nextReviewAt", 90)
    }
}
