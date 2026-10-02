package com.xuzheng.tiyuengine.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Serializes store access and rolls back an interrupted multi-preference restore. */
internal object LearningBackupTransaction {
    private val lock = Any()
    private const val JOURNAL_NAME = "learning_restore_pending.json"
    private const val MAX_JOURNAL_BYTES = 32L * 1024 * 1024

    fun <T> withStableData(context: Context, block: () -> T): T = synchronized(lock) {
        recoverPending(context)
        block()
    }

    // Roll back I/O failures and runtime failures from preference implementations alike.
    @Suppress("TooGenericExceptionCaught")
    fun replace(
        context: Context,
        records: String,
        wrongItems: String,
        favorites: Set<String>,
    ) = withStableData(context) {
        val previous = Snapshot.capture(context)
        val journal = journalFile(context)
        writeJournal(journal, previous)
        try {
            commit(context.preferences("learning_history").edit().putString("records", records))
            commit(context.preferences("wrong_book").edit().putString("items_v2", wrongItems).remove("question_ids"))
            commit(context.preferences("favorites").edit().putStringSet("question_ids", favorites))
            deleteJournal(journal)
        } catch (failure: Exception) {
            try {
                previous.restore(context)
                deleteJournal(journal)
            } catch (rollbackFailure: Exception) {
                // The durable journal remains for the next store access / next app launch.
                failure.addSuppressed(rollbackFailure)
            }
            throw IOException("恢复未完成，原有数据已保留或将在下次启动时恢复", failure)
        }
    }

    private fun recoverPending(context: Context) {
        val journal = journalFile(context)
        if (!journal.exists()) return
        val snapshot = journal.inputStream().use { input ->
            Snapshot.decode(JSONObject(input.readUtf8Bounded(MAX_JOURNAL_BYTES, "恢复日志过大")))
        }
        snapshot.restore(context)
        deleteJournal(journal)
    }

    private fun journalFile(context: Context) = File(context.noBackupFilesDir, JOURNAL_NAME)

    // Always close and abandon an incomplete staged journal, including runtime failures.
    @Suppress("TooGenericExceptionCaught")
    private fun writeJournal(journal: File, snapshot: Snapshot) {
        val encoded = snapshot.encode().toString().toByteArray(Charsets.UTF_8)
        if (encoded.size > MAX_JOURNAL_BYTES) throw IOException("当前学习数据过大，无法安全恢复；原有数据未更改")
        // A partially written staging file is never considered a pending transaction.
        // Preferences are only touched after the fsynced file is renamed into place.
        val staged = File(journal.parentFile, "$JOURNAL_NAME.staging")
        val atomic = AtomicFile(staged)
        val output = atomic.startWrite()
        try {
            output.write(encoded)
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
        check(!journal.exists() && staged.renameTo(journal)) { "无法保存恢复日志" }
    }

    private fun deleteJournal(journal: File) {
        check(!journal.exists() || journal.delete()) { "无法完成数据恢复事务" }
    }

    private fun commit(editor: SharedPreferences.Editor) {
        if (!editor.commit()) throw IOException("无法写入学习数据")
    }

    private fun Context.preferences(name: String) = getSharedPreferences(name, Context.MODE_PRIVATE)

    private data class Snapshot(
        val records: String?,
        val wrongItems: String?,
        val legacyWrongIds: Set<String>?,
        val favorites: Set<String>?,
    ) {
        fun encode() = JSONObject().apply {
            put("version", 1)
            put("records", records ?: JSONObject.NULL)
            put("wrongItems", wrongItems ?: JSONObject.NULL)
            put("legacyWrongIds", legacyWrongIds?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
            put("favorites", favorites?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
        }

        // Every store must be attempted even when another preference implementation throws.
        @Suppress("TooGenericExceptionCaught")
        fun restore(context: Context) {
            // Try every store even if one is currently unwritable. Retain the journal on any failure.
            var failure: Exception? = null
            val editors = listOf(
                context.preferences("learning_history").edit().putString("records", records),
                context.preferences("wrong_book").edit()
                    .putString("items_v2", wrongItems)
                    .putStringSet("question_ids", legacyWrongIds),
                context.preferences("favorites").edit().putStringSet("question_ids", favorites),
            )
            editors.forEach { editor ->
                try { commit(editor) } catch (error: Exception) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }

        companion object {
            fun capture(context: Context) = Snapshot(
                context.preferences("learning_history").getString("records", null),
                context.preferences("wrong_book").getString("items_v2", null),
                context.preferences("wrong_book").getStringSet("question_ids", null)?.toSet(),
                context.preferences("favorites").getStringSet("question_ids", null)?.toSet(),
            )

            fun decode(json: JSONObject): Snapshot {
                require(json.getInt("version") == 1) { "恢复日志版本无效" }
                fun nullableText(key: String): String? {
                    val value = json.get(key)
                    require(value == JSONObject.NULL || value is String) { "恢复日志文本无效" }
                    return value as? String
                }
                fun nullableIds(key: String): Set<String>? {
                    val value = json.get(key)
                    if (value == JSONObject.NULL) return null
                    require(value is JSONArray) { "恢复日志 ID 无效" }
                    return (0 until value.length()).mapTo(mutableSetOf()) {
                        val id = value.get(it)
                        require(id is String) { "恢复日志 ID 类型无效" }
                        id
                    }
                }
                return Snapshot(
                    nullableText("records"),
                    nullableText("wrongItems"),
                    nullableIds("legacyWrongIds"),
                    nullableIds("favorites"),
                )
            }
        }
    }
}
