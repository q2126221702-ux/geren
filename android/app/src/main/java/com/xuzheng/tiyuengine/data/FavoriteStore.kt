package com.xuzheng.tiyuengine.data

import android.content.Context

class FavoriteStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun loadIds(): Set<String> = LearningBackupTransaction.withStableData(context) {
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()
    }

    fun toggle(questionId: String): Set<String> = LearningBackupTransaction.withStableData(context) {
        val ids = loadIds().toMutableSet().apply { if (!add(questionId)) remove(questionId) }
        preferences.edit().putStringSet(KEY_IDS, ids).apply()
        ids
    }

    private companion object { const val KEY_IDS = "question_ids" }
}
