package com.xuzheng.tiyuengine.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class LearningRecord(
    val quizId: String,
    val quizTitle: String,
    val score: Int,
    val total: Int,
    val questionCount: Int,
    val durationSeconds: Long,
    val submittedAt: Long,
    val attempts: List<QuestionAttempt> = emptyList(),
)

data class QuestionAttempt(
    val questionId: String,
    val type: QuestionType,
    val correct: Boolean,
    val userAnswer: String,
)

data class DailyLearning(val dayStart: Long, val questionCount: Int, val correctCount: Int)
data class AccuracyStat(val label: String, val correct: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

data class LearningSummary(
    val quizCount: Int,
    val questionCount: Int,
    val todayQuestionCount: Int,
)

object LearningStats {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun summary(records: List<LearningRecord>, now: Long = System.currentTimeMillis()): LearningSummary {
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return LearningSummary(
            quizCount = records.size,
            questionCount = records.sumOf { it.questionCount },
            todayQuestionCount = records.filter { it.submittedAt >= startOfToday }.sumOf { it.questionCount },
        )
    }

    fun masteryPercent(
        records: List<LearningRecord>,
        quizId: String,
        objectiveQuestionCount: Int? = null,
    ): Int? {
        val quizRecords = records.filter { it.quizId == quizId }
        if (quizRecords.isEmpty()) return null

        val latestAttempts = latestAttemptsForQuiz(records, quizId)
        if (latestAttempts.isNotEmpty()) {
            val correct = latestAttempts.values.count { it }
            val denominator = objectiveQuestionCount?.takeIf { it > 0 } ?: latestAttempts.size
            return (correct * 100 / denominator).coerceIn(0, 100)
        }

        return weightedSessionPercent(quizRecords)
    }

    fun quizAccuracy(records: List<LearningRecord>): List<AccuracyStat> = records
        .groupBy { it.quizId }
        .mapNotNull { (quizId, items) ->
            val latestAttempts = latestAttemptsForQuiz(records, quizId)
            if (latestAttempts.isNotEmpty()) {
                AccuracyStat(items.last().quizTitle, latestAttempts.values.count { it }, latestAttempts.size)
            } else {
                val percent = weightedSessionPercent(items) ?: return@mapNotNull null
                AccuracyStat(items.last().quizTitle, percent, 100)
            }
        }
        .sortedBy { it.percent }

    fun typeAccuracy(records: List<LearningRecord>): List<AccuracyStat> = latestAttemptsAcrossQuizzes(records)
        .groupBy { it.type }
        .map { (type, attempts) -> AccuracyStat(type.label(), attempts.count { it.correct }, attempts.size) }
        .sortedBy { it.percent }

    fun lastSevenDays(records: List<LearningRecord>, now: Long = System.currentTimeMillis()): List<DailyLearning> {
        val today = startOfDay(now)
        return (6 downTo 0).map { offset ->
            val start = today - offset * DAY_MILLIS
            val dayRecords = records.filter { it.submittedAt in start until start + DAY_MILLIS }
            DailyLearning(start, dayRecords.sumOf { it.questionCount }, dayRecords.flatMap { it.attempts }.count { it.correct })
        }
    }

    fun streakDays(records: List<LearningRecord>, now: Long = System.currentTimeMillis()): Int {
        val activeDays = records.map { startOfDay(it.submittedAt) }.toSet()
        var day = startOfDay(now)
        if (day !in activeDays) day -= DAY_MILLIS
        var streak = 0
        while (day in activeDays) { streak++; day -= DAY_MILLIS }
        return streak
    }

    fun recommendedQuizId(records: List<LearningRecord>, quizzes: List<Quiz>): String? {
        if (quizzes.isEmpty()) return null
        val weakestTitle = quizAccuracy(records).firstOrNull()?.label
        return quizzes.firstOrNull { it.title == weakestTitle }?.id ?: quizzes.first().id
    }

    private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun latestAttemptsForQuiz(records: List<LearningRecord>, quizId: String): Map<String, Boolean> {
        val latest = linkedMapOf<String, Boolean>()
        records.filter { it.quizId == quizId }
            .sortedBy { it.submittedAt }
            .forEach { record ->
                record.attempts.forEach { attempt ->
                    latest[attempt.questionId] = attempt.correct
                }
            }
        return latest
    }

    private fun latestAttemptsAcrossQuizzes(records: List<LearningRecord>): List<QuestionAttempt> {
        val latest = linkedMapOf<String, QuestionAttempt>()
        records.sortedBy { it.submittedAt }
            .forEach { record ->
                record.attempts.forEach { attempt ->
                    latest[attempt.questionId] = attempt
                }
            }
        return latest.values.toList()
    }

    private fun weightedSessionPercent(quizRecords: List<LearningRecord>): Int? {
        val recent = quizRecords.takeLast(5)
        var weightedSum = 0
        var weightTotal = 0
        recent.forEachIndexed { index, record ->
            if (record.total == 0) return@forEachIndexed
            val weight = index + 1
            weightedSum += record.score * 100 / record.total * weight
            weightTotal += weight
        }
        return if (weightTotal == 0) null else weightedSum / weightTotal
    }

    private fun QuestionType.label() = when (this) {
        QuestionType.SINGLE -> "单选"; QuestionType.MULTIPLE -> "多选"; QuestionType.TRUE_FALSE -> "判断"
        QuestionType.FILL -> "填空"; QuestionType.ESSAY -> "问答"
    }
}

class LearningStore(context: Context) {
    private val preferences = context.getSharedPreferences("learning_history", Context.MODE_PRIVATE)

    fun load(): List<LearningRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_RECORDS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    LearningRecord(
                        quizId = item.getString("quizId"),
                        quizTitle = item.getString("quizTitle"),
                        score = item.getInt("score"),
                        total = item.getInt("total"),
                        questionCount = item.getInt("questionCount"),
                        durationSeconds = item.getLong("durationSeconds"),
                        submittedAt = item.getLong("submittedAt"),
                        attempts = item.optJSONArray("attempts")?.let { attempts ->
                            buildList {
                                for (attemptIndex in 0 until attempts.length()) {
                                    val attempt = attempts.getJSONObject(attemptIndex)
                                    add(QuestionAttempt(attempt.getString("questionId"), QuestionType.valueOf(attempt.getString("type")), attempt.getBoolean("correct"), attempt.optString("userAnswer")))
                                }
                            }
                        }.orEmpty(),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun add(record: LearningRecord) {
        val records = (load() + record).takeLast(200)
        val array = JSONArray()
        records.forEach { entry ->
            array.put(JSONObject().apply {
                put("quizId", entry.quizId)
                put("quizTitle", entry.quizTitle)
                put("score", entry.score)
                put("total", entry.total)
                put("questionCount", entry.questionCount)
                put("durationSeconds", entry.durationSeconds)
                put("submittedAt", entry.submittedAt)
                put("attempts", JSONArray().apply {
                    entry.attempts.forEach { attempt -> put(JSONObject().apply {
                        put("questionId", attempt.questionId); put("type", attempt.type.name)
                        put("correct", attempt.correct); put("userAnswer", attempt.userAnswer)
                    }) }
                })
            })
        }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private companion object { const val KEY_RECORDS = "records" }
}
