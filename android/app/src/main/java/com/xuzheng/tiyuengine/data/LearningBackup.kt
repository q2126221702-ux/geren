package com.xuzheng.tiyuengine.data

import android.content.Context
import com.xuzheng.tiyuengine.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

data class BackupPreview(
    val exportedAt: Long,
    val learningRecordCount: Int,
    val wrongItemCount: Int,
    val favoriteCount: Int,
)

class LearningBackup(private val context: Context) {
    fun exportJson(now: Long = System.currentTimeMillis()): String {
        val learningRecords = JSONArray(learningPreferences().getString(KEY_LEARNING_RECORDS, "[]"))
        val wrongItems = JSONArray(wrongPreferences().getString(KEY_WRONG_ITEMS, "[]"))
        val favoriteIds = JSONArray(favoritePreferences().getStringSet(KEY_FAVORITE_IDS, emptySet()).orEmpty().sorted())
        return JSONObject().apply {
            put("format", FORMAT)
            put("schemaVersion", SCHEMA_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportedAt", now)
            put("learningRecords", learningRecords)
            put("wrongItems", wrongItems)
            put("favoriteIds", favoriteIds)
        }.toString(2)
    }

    fun preview(json: String): BackupPreview {
        val root = validated(json)
        return BackupPreview(
            exportedAt = root.getLong("exportedAt"),
            learningRecordCount = root.getJSONArray("learningRecords").length(),
            wrongItemCount = root.getJSONArray("wrongItems").length(),
            favoriteCount = root.optJSONArray("favoriteIds")?.length() ?: 0,
        )
    }

    fun restore(json: String): BackupPreview {
        val root = validated(json)
        val learningRecords = root.getJSONArray("learningRecords")
        val wrongItems = root.getJSONArray("wrongItems")
        val favoriteIds = root.optJSONArray("favoriteIds") ?: JSONArray()
        learningPreferences().edit().putString(KEY_LEARNING_RECORDS, learningRecords.toString()).commit()
        wrongPreferences().edit().putString(KEY_WRONG_ITEMS, wrongItems.toString()).remove(KEY_LEGACY_WRONG_IDS).commit()
        favoritePreferences().edit().putStringSet(KEY_FAVORITE_IDS, (0 until favoriteIds.length()).mapTo(mutableSetOf()) { favoriteIds.getString(it) }).commit()
        return preview(json)
    }

    private fun validated(json: String): JSONObject {
        require(json.toByteArray().size <= MAX_BACKUP_BYTES) { "备份文件过大" }
        val root = runCatching { JSONObject(json) }.getOrElse { error("不是有效的题域引擎备份文件") }
        require(root.optString("format") == FORMAT) { "备份文件类型不正确" }
        require(root.optInt("schemaVersion") == SCHEMA_VERSION) { "暂不支持此备份版本" }
        require(root.optLong("exportedAt") > 0) { "备份时间无效" }
        val learningRecords = root.optJSONArray("learningRecords") ?: error("备份缺少学习记录")
        val wrongItems = root.optJSONArray("wrongItems") ?: error("备份缺少错题记录")
        require(learningRecords.length() <= MAX_LEARNING_RECORDS) { "学习记录数量异常" }
        require(wrongItems.length() <= MAX_WRONG_ITEMS) { "错题记录数量异常" }
        return root
    }

    private fun learningPreferences() = context.getSharedPreferences("learning_history", Context.MODE_PRIVATE)
    private fun wrongPreferences() = context.getSharedPreferences("wrong_book", Context.MODE_PRIVATE)
    private fun favoritePreferences() = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private companion object {
        const val FORMAT = "tiyuengine-learning-backup"
        const val SCHEMA_VERSION = 1
        const val KEY_LEARNING_RECORDS = "records"
        const val KEY_WRONG_ITEMS = "items_v2"
        const val KEY_LEGACY_WRONG_IDS = "question_ids"
        const val KEY_FAVORITE_IDS = "question_ids"
        const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
        const val MAX_LEARNING_RECORDS = 10_000
        const val MAX_WRONG_ITEMS = 100_000
    }
}
