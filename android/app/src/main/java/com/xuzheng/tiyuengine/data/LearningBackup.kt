package com.xuzheng.tiyuengine.data

import android.content.Context
import com.xuzheng.tiyuengine.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

data class BackupPreview(
    val exportedAt: Long,
    val learningRecordCount: Int,
    val wrongItemCount: Int,
    val favoriteCount: Int,
)

class LearningBackup(private val context: Context) {
    fun readJson(input: InputStream): String = input.readUtf8Bounded(MAX_BACKUP_BYTES.toLong(), "备份文件过大")

    fun exportJson(now: Long = System.currentTimeMillis()): String = LearningBackupTransaction.withStableData(context) {
        val learningPreferences = context.getSharedPreferences("learning_history", Context.MODE_PRIVATE)
        val wrongPreferences = context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE)
        val favoritePreferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val favoriteIds = favoritePreferences.getStringSet("question_ids", emptySet()).orEmpty().sorted()
        JSONObject().apply {
            put("format", FORMAT)
            put("schemaVersion", SCHEMA_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportedAt", now)
            put("learningRecords", JSONArray(learningPreferences.getString("records", "[]")))
            put("wrongItems", JSONArray(wrongPreferences.getString("items_v2", "[]")))
            put("favoriteIds", JSONArray(favoriteIds))
        }.toString(2)
    }

    fun preview(json: String): BackupPreview = validated(json).preview()

    fun restore(json: String): BackupPreview {
        // Parse every field before a journal or any preference is changed.
        val backup = validated(json)
        LearningBackupTransaction.replace(
            context,
            backup.learningRecords.toString(),
            backup.wrongItems.toString(),
            backup.favoriteIds,
        )
        return backup.preview()
    }

    private data class ValidatedBackup(
        val exportedAt: Long,
        val learningRecords: JSONArray,
        val wrongItems: JSONArray,
        val favoriteIds: Set<String>,
    ) {
        fun preview() = BackupPreview(exportedAt, learningRecords.length(), wrongItems.length(), favoriteIds.size)
    }

    private fun validated(json: String): ValidatedBackup {
        require(
            json.length <= MAX_BACKUP_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES,
        ) { "备份文件过大" }
        requireShallowJson(json)
        val root = try { JSONObject(json) } catch (_: Exception) { throw IllegalArgumentException("不是有效的题域引擎备份文件") }
        require(root.opt("format") == FORMAT) { "备份文件类型不正确" }
        require(root.integer("schemaVersion", 1, 1) == SCHEMA_VERSION.toLong()) { "暂不支持此备份版本" }
        val exportedAt = root.integer("exportedAt", 1, MAX_TIMESTAMP)
        val records = root.array("learningRecords", MAX_LEARNING_RECORDS)
        val wrong = root.array("wrongItems", MAX_WRONG_ITEMS)
        var totalAttempts = 0
        var totalQuestions = 0L
        val learningRecords = JSONArray()
        for (index in 0 until records.length()) {
            val item = records.objectAt(index)
            val total = item.integer("total", 0, MAX_QUESTIONS.toLong())
            val questionCount = item.integer("questionCount", total, MAX_QUESTIONS.toLong())
            totalQuestions += questionCount
            require(totalQuestions <= Int.MAX_VALUE) { "累计题目数量异常" }
            val attempts = if (item.has("attempts")) item.array("attempts", MAX_QUESTIONS) else JSONArray()
            totalAttempts += attempts.length()
            require(totalAttempts <= MAX_ATTEMPTS) { "答题记录数量异常" }
            require(attempts.length() <= questionCount) { "答题记录数量超过题目数量" }
            val attemptIds = mutableSetOf<String>()
            val canonicalAttempts = JSONArray()
            for (attemptIndex in 0 until attempts.length()) {
                val attempt = attempts.objectAt(attemptIndex)
                val id = attempt.identifier("questionId")
                require(attemptIds.add(id)) { "同次练习包含重复题目" }
                val type = attempt.text("type", 32)
                require(QuestionType.entries.any { it.name == type }) { "答题题型无效" }
                val correct = attempt.opt("correct")
                require(correct is Boolean) { "答题结果类型无效" }
                // Older records did not always retain the user's answer.
                val userAnswer = if (attempt.has("userAnswer")) {
                    attempt.text("userAnswer", MAX_ANSWER_LENGTH, allowBlank = true)
                } else {
                    ""
                }
                canonicalAttempts.put(
                    JSONObject().apply {
                        put("questionId", id)
                        put("type", type)
                        put("correct", correct)
                        put("userAnswer", userAnswer)
                    },
                )
            }
            learningRecords.put(
                JSONObject().apply {
                    put("quizId", item.identifier("quizId"))
                    put("quizTitle", item.text("quizTitle", 4_096, allowBlank = true))
                    put("score", item.integer("score", 0, total))
                    put("total", total)
                    put("questionCount", questionCount)
                    put("durationSeconds", item.integer("durationSeconds", 0, MAX_DURATION_SECONDS))
                    put("submittedAt", item.integer("submittedAt", 0, MAX_TIMESTAMP))
                    put("attempts", canonicalAttempts)
                },
            )
        }
        val wrongIds = mutableSetOf<String>()
        val wrongItems = JSONArray()
        for (index in 0 until wrong.length()) {
            val item = wrong.objectAt(index)
            val id = item.identifier("questionId")
            require(wrongIds.add(id)) { "错题记录包含重复题目" }
            wrongItems.put(
                JSONObject().apply {
                    put("questionId", id)
                    put("wrongTimes", item.integer("wrongTimes", 0, Int.MAX_VALUE.toLong()))
                    put("correctStreak", item.integer("correctStreak", 0, 3))
                    put("lastWrongAt", item.integer("lastWrongAt", 0, MAX_TIMESTAMP))
                    put("lastReviewedAt", item.integer("lastReviewedAt", 0, MAX_TIMESTAMP))
                    put("nextReviewAt", item.integer("nextReviewAt", 0, MAX_TIMESTAMP))
                },
            )
        }
        // Missing favorites is the original v1 format; a present field must be an array.
        val favorites = if (root.has("favoriteIds")) root.array("favoriteIds", MAX_FAVORITES) else JSONArray()
        val favoriteIds = mutableSetOf<String>()
        for (index in 0 until favorites.length()) {
            val id = checkedText(favorites.opt(index), "收藏题目 ID", 512, false)
            require(id.none(Char::isISOControl) && favoriteIds.add(id)) { "收藏题目 ID 无效或重复" }
        }
        return ValidatedBackup(exportedAt, learningRecords, wrongItems, favoriteIds)
    }

    private fun JSONObject.integer(key: String, minimum: Long, maximum: Long): Long {
        val value = opt(key)
        require(value is Int || value is Long) { "$key 必须是整数" }
        return (value as Number).toLong().also { require(it in minimum..maximum) { "$key 超出有效范围" } }
    }

    private fun JSONObject.array(key: String, maximum: Int): JSONArray {
        val value = opt(key)
        require(value is JSONArray && value.length() <= maximum) { "$key 格式或数量无效" }
        return value
    }

    private fun JSONArray.objectAt(index: Int): JSONObject = (opt(index) as? JSONObject)
        ?: throw IllegalArgumentException("备份记录必须是对象")

    private fun JSONObject.text(key: String, maximum: Int, allowBlank: Boolean = false): String =
        checkedText(opt(key), key, maximum, allowBlank)

    private fun JSONObject.identifier(key: String): String = text(key, 512).also {
        require(it.none(Char::isISOControl)) { "$key 包含无效字符" }
    }

    private fun checkedText(value: Any?, key: String, maximum: Int, allowBlank: Boolean): String {
        require(value is String && value.length <= maximum && (allowBlank || value.isNotBlank())) { "$key 文本无效或过长" }
        return value
    }

    private fun requireShallowJson(json: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        var previous: Char? = null
        var closedString = false
        json.forEach { character ->
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> {
                        quoted = false
                        closedString = true
                        previous = character
                    }
                }
            } else if (!character.isWhitespace()) {
                if (closedString) {
                    require(character in ":,]}") { "备份 JSON 文本格式无效" }
                    closedString = false
                }
                when (character) {
                    '"' -> {
                        require(previous == null || previous in "{[:,") { "备份 JSON 文本格式无效" }
                        quoted = true
                    }
                    '{', '[' -> {
                        depth++
                        require(depth <= 16) { "备份结构嵌套过深" }
                    }
                    '}', ']' -> {
                        depth--
                        require(depth >= 0) { "备份 JSON 结构无效" }
                    }
                    else -> require(character in ":,0123456789.-+eEtruefalsn") { "备份 JSON 文本格式无效" }
                }
                previous = character
            }
        }
        require(!quoted && depth == 0) { "备份 JSON 结构不完整" }
    }

    private companion object {
        const val FORMAT = "tiyuengine-learning-backup"
        const val SCHEMA_VERSION = 1
        const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
        const val MAX_LEARNING_RECORDS = 10_000
        const val MAX_WRONG_ITEMS = 10_000
        const val MAX_FAVORITES = 10_000
        const val MAX_QUESTIONS = 20_000
        const val MAX_ATTEMPTS = 100_000
        const val MAX_ANSWER_LENGTH = 65_536
        const val MAX_TIMESTAMP = 253_402_300_799_999L
        const val MAX_DURATION_SECONDS = 10L * 365 * 24 * 60 * 60
    }
}
