package com.xuzheng.tiyuengine.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AiQuestionResult(val text: String, val score: Double? = null, val maxScore: Int? = null)

class AiClient(context: Context) {
    private val settingsStore = AiSettingsStore(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun test(settings: AiSettings, draftApiKey: String): String = complete(
        settings = settings,
        apiKey = draftApiKey.ifBlank { if (settings.mode == AiMode.OWN_KEY) settingsStore.apiKey() else "" },
        prompt = "请只回复：连接成功",
        maxTokens = 64,
        temperature = 0.0,
        stream = false,
    )

    suspend fun explain(question: Question, answers: AnswerBundle, onPartial: suspend (String) -> Unit): AiQuestionResult {
        val settings = settingsStore.load()
        val full = settings.mode == AiMode.OWN_KEY
        val prompt = AiPromptBuilder.question(question, answers, full)
        val raw = complete(settings, settingsStore.apiKey(), prompt, if (full) 4096 else 768, if (full) 0.6 else 0.5, full, onPartial)
        if (question.type != QuestionType.ESSAY) return AiQuestionResult(raw)
        val score = SCORE_REGEX.find(raw)
        return AiQuestionResult(
            text = raw.replaceFirst(SCORE_REGEX, "").trim(),
            score = score?.groupValues?.get(1)?.toDoubleOrNull(),
            maxScore = 10,
        )
    }

    suspend fun analyze(quiz: Quiz, score: Int, answers: AnswerBundle, onPartial: suspend (String) -> Unit): String {
        val settings = settingsStore.load()
        val full = settings.mode == AiMode.OWN_KEY
        return complete(
            settings,
            settingsStore.apiKey(),
            AiPromptBuilder.analysis(quiz, score, answers, full),
            if (full) 4096 else 512,
            if (full) 0.6 else 0.4,
            full,
            onPartial,
        )
    }

    private suspend fun complete(
        settings: AiSettings,
        apiKey: String,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        stream: Boolean,
        onPartial: suspend (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        if (settings.mode == AiMode.OWN_KEY && apiKey.isBlank()) error("请先在 AI 设置中填写 API Key")
        val baseUrl = if (settings.mode == AiMode.SHARED) SHARED_BASE_URL else settings.provider.baseUrl
        val model = if (settings.mode == AiMode.SHARED) SHARED_MODEL else settings.activeModel
        val payload = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("stream", stream)
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", if (stream) "text/event-stream" else "application/json")
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw aiError(response.code, response.body?.string().orEmpty())
            val body = response.body ?: error("AI 服务未返回内容")
            if (!stream) {
                val text = extractMessage(JSONObject(body.string()))
                if (text.isBlank()) error("AI 服务返回了空内容，请重试")
                return@withContext text
            }
            val output = StringBuilder()
            while (true) {
                val line = body.source().readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val piece = runCatching {
                    JSONObject(data).getJSONArray("choices").getJSONObject(0)
                        .optJSONObject("delta")?.optString("content").orEmpty()
                }.getOrDefault("")
                if (piece.isNotEmpty()) {
                    output.append(piece)
                    withContext(Dispatchers.Main.immediate) { onPartial(output.toString()) }
                }
            }
            output.toString().trim().ifBlank { error("AI 流式输出中断，请重试") }
        }
    }

    private fun extractMessage(json: JSONObject): String = json.getJSONArray("choices").getJSONObject(0)
        .getJSONObject("message").optString("content").trim()

    private fun aiError(code: Int, body: String): IOException {
        val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        val message = when (code) {
            401, 403 -> "API Key 无效或已过期，请检查 AI 设置"
            404 -> "模型不存在，请检查模型名称"
            429 -> "AI 服务请求过于频繁或额度不足，请稍后重试"
            in 500..599 -> "AI 服务暂时不可用，请稍后重试"
            else -> "AI 请求失败（$code）"
        }
        return IOException(if (detail.isBlank()) message else "$message：${detail.take(120)}")
    }

    private companion object {
        const val SHARED_BASE_URL = "https://ai.488227.xyz/v1"
        const val SHARED_MODEL = "glm-4-flash"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val SCORE_REGEX = Regex("【得分】\\s*([0-9]+(?:\\.[0-9]+)?)/10")
    }
}

object AiPromptBuilder {
    fun question(question: Question, answers: AnswerBundle, full: Boolean): String {
        val userAnswer = userAnswer(question, answers)
        val reference = referenceAnswer(question)
        val intro = if (full) {
            "你是测验深度复盘导师。学生已交卷，请用中文做深度讲解（350–500字）。先指出掌握之处，再诊断具体错因；一次聚焦一个核心知识点；不要复述整道题。"
        } else {
            "你是测验复盘助手。学生已交卷，请用中文一段讲完（120–180字，无标题、无列表）。按错因诊断→正确要点→记忆句的顺序回答。"
        }
        return buildString {
            appendLine(intro)
            appendLine("题型：${question.type.labelForAi()}")
            appendLine("题目：${question.prompt}")
            if (question.options.isNotEmpty()) {
                appendLine("选项：")
                question.options.forEachIndexed { index, option -> appendLine("${('A'.code + index).toChar()}. $option") }
            }
            appendLine("参考答案：$reference")
            appendLine("学生作答：${userAnswer.ifBlank { "（未作答）" }}")
            if (question.type == QuestionType.ESSAY) {
                appendLine("本题由你评分，满分10分。第一行且仅第一行输出：【得分】X/10，第二行开始点评关键信息、准确性和表达。")
            } else {
                appendLine("判题结果：${if (QuizEngine.isCorrect(question, answers)) "正确" else "错误"}")
            }
            if (full) append("请分为：审题路径、正解剖析、错因诊断、巩固拓展四段；使用简短加粗小标题，不要数字编号，不要给复习日程。")
        }
    }

    fun analysis(quiz: Quiz, score: Int, answers: AnswerBundle, full: Boolean): String {
        val objective = quiz.questions.filter { it.type != QuestionType.ESSAY }
        val wrong = objective.mapIndexedNotNull { index, question ->
            if (QuizEngine.isCorrect(question, answers)) null else "第${quiz.questions.indexOf(question) + 1}题 [${question.type.labelForAi()}] ${question.prompt.take(if (full) 120 else 40)}"
        }
        val rate = if (objective.isEmpty()) 0 else score * 100 / objective.size
        return if (full) buildString {
            appendLine("你是专业学情分析师。根据测验数据写深度复盘报告（400–550字，必须完整收束）。")
            appendLine("用加粗小标题分为：整体表现、知识盲区、典型错因、记忆锦囊。按知识簇归纳，不要逐题流水账，不要提供复习日程。")
            appendLine("测验：${quiz.title}")
            appendLine("客观题得分：$score/${objective.size}（得分率 $rate%）")
            appendLine(if (wrong.isEmpty()) "客观题全部答对。" else "错题明细：\n${wrong.joinToString("\n")}")
        } else {
            "你是测验复盘顾问。根据数据写150–200字精炼复盘，包含得分、两个错因模式和1–2条记忆口诀，不要小标题和复习计划。\n测验：${quiz.title}\n客观题得分：$score/${objective.size}（$rate%）\n${if (wrong.isEmpty()) "客观题全对。" else "错题${wrong.size}题，题号：${wrong.take(6).joinToString("、") { it.substringBefore(' ') }}"}"
        }
    }

    private fun userAnswer(question: Question, answers: AnswerBundle): String = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> answers.optionAnswers[question.id].orEmpty().sorted().joinToString("、") { question.options.getOrElse(it) { "" } }
        QuestionType.FILL, QuestionType.ESSAY -> answers.textAnswers[question.id].orEmpty()
    }

    private fun referenceAnswer(question: Question): String = when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTIPLE, QuestionType.TRUE_FALSE -> question.answerIndices.sorted().joinToString("、") { question.options[it] }
        QuestionType.FILL -> question.acceptedAnswers.joinToString(" / ")
        QuestionType.ESSAY -> question.referenceAnswer
    }

    private fun QuestionType.labelForAi(): String = when (this) {
        QuestionType.SINGLE -> "单选题"
        QuestionType.MULTIPLE -> "多选题"
        QuestionType.TRUE_FALSE -> "判断题"
        QuestionType.FILL -> "填空题"
        QuestionType.ESSAY -> "问答题"
    }
}
