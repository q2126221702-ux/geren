package com.xuzheng.tiyuengine.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WrongItem(
    val questionId: String,
    val wrongTimes: Int,
    val correctStreak: Int,
    val lastWrongAt: Long,
    val lastReviewedAt: Long,
    val nextReviewAt: Long,
) {
    val status: ReviewStatus get() = when {
        correctStreak == 0 -> ReviewStatus.UNMASTERED
        correctStreak < 3 -> ReviewStatus.REVIEWING
        else -> ReviewStatus.MASTERED
    }
}

enum class ReviewStatus { UNMASTERED, REVIEWING, MASTERED }

class WrongBookStore(context: Context) {
    private val preferences = context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE)

    fun loadItems(): List<WrongItem> {
        val stored = runCatching {
            val array = JSONArray(preferences.getString(KEY_ITEMS, "[]"))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(WrongItem(item.getString("questionId"), item.getInt("wrongTimes"), item.getInt("correctStreak"), item.getLong("lastWrongAt"), item.getLong("lastReviewedAt"), item.getLong("nextReviewAt")))
                }
            }
        }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) return stored

        val legacyIds = preferences.getStringSet(KEY_LEGACY_IDS, emptySet()).orEmpty()
        if (legacyIds.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return legacyIds.map { WrongItem(it, 1, 0, now, 0, now) }.also(::save)
    }

    fun loadIds(): Set<String> = loadItems().filter { it.status != ReviewStatus.MASTERED }.mapTo(mutableSetOf()) { it.questionId }

    fun updateAfterSubmission(questions: List<Question>, answers: AnswerBundle, isReview: Boolean) {
        val now = System.currentTimeMillis()
        val items = loadItems().associateBy { it.questionId }.toMutableMap()
        questions.filter { it.type != QuestionType.ESSAY }.forEach { question ->
            val old = items[question.id]
            val correct = QuizEngine.isCorrect(question, answers)
            if (!correct) {
                items[question.id] = WrongItem(question.id, (old?.wrongTimes ?: 0) + 1, 0, now, if (isReview) now else old?.lastReviewedAt ?: 0, now)
            } else if (isReview && old != null) {
                val streak = (old.correctStreak + 1).coerceAtMost(3)
                val delayDays = when (streak) { 1 -> 1; 2 -> 3; else -> 7 }
                items[question.id] = old.copy(correctStreak = streak, lastReviewedAt = now, nextReviewAt = now + delayDays * DAY_MILLIS)
            }
        }
        save(items.values.sortedByDescending { it.lastWrongAt })
    }

    private fun save(items: Collection<WrongItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("questionId", item.questionId)
            put("wrongTimes", item.wrongTimes)
            put("correctStreak", item.correctStreak)
            put("lastWrongAt", item.lastWrongAt)
            put("lastReviewedAt", item.lastReviewedAt)
            put("nextReviewAt", item.nextReviewAt)
        }) }
        preferences.edit().putString(KEY_ITEMS, array.toString()).remove(KEY_LEGACY_IDS).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items_v2"
        const val KEY_LEGACY_IDS = "question_ids"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
