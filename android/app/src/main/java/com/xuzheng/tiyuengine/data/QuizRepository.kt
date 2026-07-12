package com.xuzheng.tiyuengine.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class SyncResult(val quizCount: Int, val questionCount: Int, val syncedAt: Long)

class QuizRepository(private val context: Context) {
    fun loadQuizzes(): List<Quiz> {
        val syncedDirectory = File(context.filesDir, SYNC_DIRECTORY).takeIf { File(it, "manifest.json").isFile }
        return loadQuizzes(syncedDirectory)
    }

    fun lastSyncedAt(): Long = context.getSharedPreferences("quiz_sync", Context.MODE_PRIVATE).getLong("last_synced_at", 0L)

    fun syncFromGithub(): SyncResult {
        val temporaryDirectory = File(context.cacheDir, "quiz_data_download").apply { deleteRecursively(); mkdirs() }
        downloadArchive(temporaryDirectory)
        val manifest = JSONObject(File(temporaryDirectory, "manifest.json").readText())
        requiredFiles(manifest).forEach { fileName -> require(File(temporaryDirectory, fileName).isFile) { "仓库缺少题库：$fileName" } }
        val parsed = loadQuizzes(temporaryDirectory)
        require(parsed.isNotEmpty() && parsed.all { it.questions.isNotEmpty() }) { "远程题库为空" }

        val activeDirectory = File(context.filesDir, SYNC_DIRECTORY)
        val backupDirectory = File(context.filesDir, "${SYNC_DIRECTORY}_backup")
        backupDirectory.deleteRecursively()
        if (activeDirectory.exists()) check(activeDirectory.renameTo(backupDirectory)) { "无法备份旧题库" }
        if (!temporaryDirectory.renameTo(activeDirectory)) {
            backupDirectory.renameTo(activeDirectory)
            error("无法保存新题库")
        }
        backupDirectory.deleteRecursively()
        val syncedAt = System.currentTimeMillis()
        context.getSharedPreferences("quiz_sync", Context.MODE_PRIVATE).edit().putLong("last_synced_at", syncedAt).apply()
        return SyncResult(parsed.size, parsed.sumOf { it.questions.size }, syncedAt)
    }

    private fun loadQuizzes(directory: File?): List<Quiz> {
        val manifest = readJson("manifest.json", directory)
        val result = mutableListOf<Quiz>()
        val entries = manifest.getJSONArray("quizzes")
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            if (entry.optString("kind") == "flashcard") continue
            val variants = entry.optJSONArray("variants")
            if (variants != null) {
                for (variantIndex in 0 until variants.length()) {
                    val file = variants.getString(variantIndex)
                    val suffix = file.substringAfterLast('_').substringBefore(".json")
                    result += parseQuiz("${entry.getString("id")}_$suffix", file, directory)
                }
            } else {
                result += parseQuiz(entry.getString("id"), entry.getString("file"), directory)
            }
        }
        return result
    }

    private fun parseQuiz(id: String, fileName: String, directory: File?): Quiz {
        val json = readJson(fileName, directory)
        val questionsJson = json.getJSONArray("questions")
        val questions = buildList {
            for (index in 0 until questionsJson.length()) {
                add(parseQuestion(id, index, questionsJson.getJSONObject(index)))
            }
        }
        val title = json.optString("title", fileName.substringBeforeLast('.'))
        val subtitle = when {
            id.startsWith("welearn") -> "英语 · 离线题库"
            id.startsWith("exam100") -> "工业网络 · 期末模拟卷"
            else -> "工业网络 · 专项练习"
        }
        return Quiz(id, title, subtitle, questions)
    }

    private fun parseQuestion(quizId: String, index: Int, json: JSONObject): Question {
        val typeName = json.optString("type")
        val type = when {
            typeName.startsWith("多选") -> QuestionType.MULTIPLE
            typeName.startsWith("判断") -> QuestionType.TRUE_FALSE
            typeName.startsWith("填空") -> QuestionType.FILL
            typeName.startsWith("问答") -> QuestionType.ESSAY
            else -> QuestionType.SINGLE
        }
        val options = json.optJSONArray("options").toStringList().filter { it.isNotBlank() }
        val rawAnswer = json.optString("correct_answer").trim()
        val answerIndices = when (type) {
            QuestionType.TRUE_FALSE -> setOf(rawAnswer.toIntOrNull()?.coerceIn(0, options.lastIndex) ?: 0)
            QuestionType.SINGLE, QuestionType.MULTIPLE -> answerLetters(rawAnswer, options)
            else -> emptySet()
        }
        val explanation = json.optString("explanation")
            .ifBlank { json.optString("analysis") }
            .ifBlank { "正确答案：$rawAnswer" }
        return Question(
            id = "${quizId}_${json.optInt("sort", index + 1)}",
            prompt = json.optString("title"),
            options = options,
            answerIndex = answerIndices.firstOrNull() ?: 0,
            explanation = explanation,
            type = type,
            answerIndices = answerIndices,
            acceptedAnswers = if (type == QuestionType.FILL) listOf(rawAnswer) else emptyList(),
            referenceAnswer = if (type == QuestionType.ESSAY) rawAnswer else "",
        )
    }

    private fun answerLetters(answer: String, options: List<String>): Set<Int> {
        val numericIndices = answer.split(',', '，', '、').mapNotNull { it.trim().toIntOrNull() }.toSet()
        if (numericIndices.isNotEmpty()) return numericIndices.filter { it in options.indices }.toSet()
        val letters = Regex("(?:^|[\\s,，、;；])([A-Z])(?:[.．、:]|$)")
            .findAll(answer.uppercase()).map { it.groupValues[1][0] - 'A' }.toSet()
        if (letters.isNotEmpty()) return letters.filter { it in options.indices }.toSet()
        val matchingIndex = options.indexOfFirst { answer == it || answer.substringAfter(". ", answer) == it }
        return setOf(if (matchingIndex >= 0) matchingIndex else 0)
    }

    private fun readJson(fileName: String, directory: File?): JSONObject {
        val text = if (directory == null) context.assets.open("data/$fileName").bufferedReader().use { it.readText() }
        else File(directory, fileName).readText()
        return JSONObject(text)
    }

    private fun requiredFiles(manifest: JSONObject): Set<String> = buildSet {
        val entries = manifest.getJSONArray("quizzes")
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            entry.optJSONArray("variants")?.let { variants ->
                for (variantIndex in 0 until variants.length()) add(variants.getString(variantIndex))
            } ?: add(entry.getString("file"))
        }
    }

    private fun downloadArchive(targetDirectory: File) {
        val connection = URL(ARCHIVE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        try {
            check(connection.responseCode in 200..299) { "下载失败 (${connection.responseCode})" }
            ZipInputStream(connection.inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val marker = "/data/"
                    val markerIndex = entry.name.indexOf(marker)
                    if (!entry.isDirectory && markerIndex >= 0 && entry.name.endsWith(".json")) {
                        val fileName = entry.name.substring(markerIndex + marker.length)
                        if (!fileName.contains('/')) File(targetDirectory, fileName).outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(File(targetDirectory, "manifest.json").isFile) { "仓库中未找到 data/manifest.json" }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) add(optString(index))
    }

    private companion object {
        const val SYNC_DIRECTORY = "quiz_data"
        const val ARCHIVE_URL = "https://codeload.github.com/q2126221702-ux/geren/zip/refs/heads/main"
    }
}
