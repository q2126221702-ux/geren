package com.xuzheng.tiyuengine.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SyncResult(val quizCount: Int, val questionCount: Int, val syncedAt: Long)

class QuizRepository(private val context: Context) {
    fun loadQuizzes(): List<Quiz> = synchronized(syncLock) {
        recoverPendingSwap()
        val syncedDirectory = File(context.filesDir, SYNC_DIRECTORY).takeIf { File(it, "manifest.json").isFile }
        loadQuizzes(syncedDirectory)
    }

    fun lastSyncedAt(): Long = context.getSharedPreferences(
        "quiz_sync",
        Context.MODE_PRIVATE
    ).getLong("last_synced_at", 0L)

    fun syncFromGithub(): SyncResult = synchronized(downloadLock) {
        synchronized(syncLock) { recoverPendingSwap() }
        val temporaryDirectory = File(context.filesDir, "quiz_data_download").apply {
            check(!exists() || deleteRecursively()) { "无法清理题库临时目录" }
            check(mkdirs()) { "无法创建题库临时目录" }
        }
        try {
            GithubDownloads.read(ARCHIVE_URL, QuizArchive.MAX_DOWNLOAD_BYTES) {
                QuizArchive.extract(it, temporaryDirectory)
            }
            val manifest = readJson("manifest.json", temporaryDirectory)
            requiredFiles(manifest).forEach { fileName ->
                require(File(temporaryDirectory, fileName).isFile) { "仓库缺少题库：$fileName" }
            }
            val parsed = loadQuizzes(temporaryDirectory)
            require(parsed.isNotEmpty() && parsed.all { it.questions.isNotEmpty() }) { "远程题库为空" }

            synchronized(syncLock) {
                val activeDirectory = File(context.filesDir, SYNC_DIRECTORY)
                val backupDirectory = File(context.filesDir, "${SYNC_DIRECTORY}_backup")
                if (activeDirectory.exists()) check(activeDirectory.renameTo(backupDirectory)) { "无法备份旧题库" }
                if (!temporaryDirectory.renameTo(activeDirectory)) {
                    recoverPendingSwap()
                    error("无法保存新题库")
                }
                backupDirectory.deleteRecursively()
            }
            val syncedAt = System.currentTimeMillis()
            context.getSharedPreferences(
                "quiz_sync",
                Context.MODE_PRIVATE
            ).edit().putLong("last_synced_at", syncedAt).apply()
            SyncResult(parsed.size, parsed.sumOf { it.questions.size }, syncedAt)
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }

    private fun recoverPendingSwap() {
        val active = File(context.filesDir, SYNC_DIRECTORY)
        val backup = File(context.filesDir, "${SYNC_DIRECTORY}_backup")
        if (!backup.exists()) return
        if (!active.exists()) {
            check(backup.renameTo(active)) { "无法恢复上一次题库同步" }
        } else {
            check(backup.deleteRecursively()) { "无法清理题库备份" }
        }
    }

    private fun loadQuizzes(directory: File?): List<Quiz> {
        val manifest = readJson("manifest.json", directory)
        val result = mutableListOf<Quiz>()
        val loadedFiles = mutableSetOf<String>()
        val entries = manifest.getJSONArray("quizzes")
        require(entries.length() <= MAX_QUIZZES) { "题库数量过多" }
        var totalQuestions = 0
        fun addQuiz(id: String, fileName: String) {
            require(loadedFiles.add(fileName)) { "清单重复引用题库文件" }
            require(result.size < MAX_QUIZZES) { "试卷数量过多" }
            val quiz = parseQuiz(id, fileName, directory)
            totalQuestions += quiz.questions.size
            require(totalQuestions <= MAX_TOTAL_QUESTIONS) { "题目总数超过限制" }
            result += quiz
        }
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            if (entry.optString("kind") == "flashcard") continue
            val variants = entry.optJSONArray("variants")
            if (variants != null) {
                require(variants.length() <= MAX_QUIZZES) { "试卷数量过多" }
                for (variantIndex in 0 until variants.length()) {
                    val file = variants.getString(variantIndex)
                    val suffix = file.substringAfterLast('_').substringBefore(".json")
                    addQuiz("${entry.getString("id")}_$suffix", file)
                }
            } else {
                addQuiz(entry.getString("id"), entry.getString("file"))
            }
        }
        require(result.map { it.id }.distinct().size == result.size) { "题库标识重复" }
        return result
    }

    private fun parseQuiz(id: String, fileName: String, directory: File?): Quiz {
        require(id.length in 1..128) { "题库标识无效" }
        val json = readJson(fileName, directory)
        val questionsJson = json.getJSONArray("questions")
        require(questionsJson.length() <= MAX_QUESTIONS_PER_QUIZ) { "单个题库题目过多" }
        val questions = buildList {
            for (index in 0 until questionsJson.length()) {
                add(parseQuestion(id, index, questionsJson.getJSONObject(index)))
            }
        }
        require(questions.map { it.id }.distinct().size == questions.size) { "题目标识重复" }
        val title = json.optString("title", fileName.substringBeforeLast('.'))
        require(title.length <= 500) { "题库标题过长" }
        val subtitle = when {
            id.startsWith("welearn") -> "英语 · 离线题库"
            id.startsWith("exam100") -> "工业网络 · 期末模拟卷"
            else -> "工业网络 · 专项练习"
        }
        return Quiz(id, title, subtitle, questions)
    }

    private fun parseQuestion(quizId: String, index: Int, json: JSONObject): Question {
        require(json.optString("title").length <= 50_000) { "题干过长" }
        require(json.optString("correct_answer").length <= 50_000) { "答案过长" }
        require(
            json.optString("explanation").length <= 50_000 && json.optString("analysis").length <= 50_000
        ) { "解析过长" }
        val rawOptions = json.optJSONArray("options")
        require((rawOptions?.length() ?: 0) <= 50) { "选项数量过多" }
        val typeName = json.optString("type")
        val type = when {
            typeName.startsWith("多选") -> QuestionType.MULTIPLE
            typeName.startsWith("判断") -> QuestionType.TRUE_FALSE
            typeName.startsWith("填空") -> QuestionType.FILL
            typeName.startsWith("问答") -> QuestionType.ESSAY
            else -> QuestionType.SINGLE
        }
        val options = json.optJSONArray("options").toStringList().filter { it.isNotBlank() }
        require(options.all { it.length <= 20_000 }) { "选项内容过长" }
        if (type == QuestionType.SINGLE || type == QuestionType.MULTIPLE || type == QuestionType.TRUE_FALSE) {
            require(options.isNotEmpty()) { "选择题缺少选项" }
        }
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
        require(QuizArchive.safeFileName(fileName)) { "题库文件名无效" }
        val input = if (directory == null) {
            context.assets.open("data/$fileName")
        } else {
            File(directory, fileName).inputStream()
        }
        val text = input.use { it.readUtf8Bounded(QuizArchive.MAX_JSON_BYTES, "题库文件过大") }
        requireBoundedJson(text)
        return JSONObject(text)
    }

    private fun requiredFiles(manifest: JSONObject): Set<String> = buildSet {
        val entries = manifest.getJSONArray("quizzes")
        require(entries.length() <= MAX_QUIZZES) { "题库数量过多" }
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            entry.optJSONArray("variants")?.let { variants ->
                require(variants.length() <= MAX_QUIZZES) { "试卷数量过多" }
                for (variantIndex in 0 until variants.length()) add(variants.getString(variantIndex))
            } ?: add(entry.getString("file"))
            require(size <= MAX_QUIZZES) { "题库文件数量过多" }
        }
        require(all(QuizArchive::safeFileName)) { "题库文件名无效" }
    }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) {
        emptyList()
    } else {
        buildList {
            for (index in 0 until length()) add(optString(index))
        }
    }

    private companion object {
        val syncLock = Any()
        val downloadLock = Any()
        const val MAX_QUIZZES = 512
        const val MAX_QUESTIONS_PER_QUIZ = 5_000
        const val MAX_TOTAL_QUESTIONS = 50_000
        const val SYNC_DIRECTORY = "quiz_data"
        const val ARCHIVE_URL = "https://codeload.github.com/q2126221702-ux/geren/zip/refs/heads/main"
    }
}
