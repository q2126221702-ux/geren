package com.xuzheng.tiyuengine.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal class AiCompletionRequest(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val prompt: String,
    val maxTokens: Int,
    val temperature: Double,
    val stream: Boolean,
)

internal data class AiResponseLimits(
    val jsonBytes: Long = 256 * 1024,
    val errorBytes: Long = 8 * 1024,
    val streamBytes: Long = 2 * 1024 * 1024,
    val lineBytes: Int = 32 * 1024,
    val outputBytes: Long = 128 * 1024,
)

internal class AiHttpTransport(
    httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build(),
    private val partialDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val limits: AiResponseLimits = AiResponseLimits(),
) {
    private val client = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .apply { if (httpClient.callTimeoutMillis == 0) callTimeout(120, TimeUnit.SECONDS) }
        .build()

    suspend fun complete(
        request: AiCompletionRequest,
        onPartial: suspend (String) -> Unit = {},
    ): String = try {
        withContext(Dispatchers.IO) {
            val call = client.newCall(buildRequest(request))
            // Keep cancellation attached until response consumption finishes, not just until headers arrive.
            val cancellationWatcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                currentCoroutineContext().ensureActive()
                call.execute().use { response ->
                    readResponse(response, request.stream, onPartial)
                }
            } finally {
                cancellationWatcher.cancel()
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: AiResponseException) {
        throw error
    } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        if (error.message == TOO_LARGE) throw AiResponseException(TOO_LARGE)
        throw AiResponseException("AI 网络请求失败或超时，请稍后重试")
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        throw AiResponseException("AI 服务返回格式异常，请稍后重试")
    }

    private fun buildRequest(request: AiCompletionRequest): Request {
        val message = JSONObject().put("role", "user").put("content", request.prompt)
        val payload = JSONObject()
            .put("model", request.model)
            .put("messages", JSONArray().put(message))
            .put("max_tokens", request.maxTokens)
            .put("temperature", request.temperature)
            .put("stream", request.stream)
        return Request.Builder()
            .url("${request.baseUrl.trimEnd('/')}/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", if (request.stream) "text/event-stream" else "application/json")
            .apply { if (request.apiKey.isNotBlank()) header("Authorization", "Bearer ${request.apiKey}") }
            .build()
    }

    private suspend fun readResponse(
        response: Response,
        stream: Boolean,
        onPartial: suspend (String) -> Unit,
    ): String {
        if (!response.isSuccessful) {
            // Never surface a remote error body: it can echo credentials or prompt data.
            response.body?.byteStream()?.readUtf8Bounded(limits.errorBytes, TOO_LARGE)
            throw AiResponseException(httpError(response.code))
        }
        val body = response.body ?: throw AiResponseException("AI 服务未返回内容")
        return if (stream) readStream(body, onPartial) else readJson(body)
    }

    private fun readJson(body: ResponseBody): String {
        val jsonText = body.byteStream().readUtf8Bounded(limits.jsonBytes, TOO_LARGE)
        requireBoundedJson(jsonText)
        val json = JSONObject(jsonText)
        val text = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
        if (text.isBlank()) throw AiResponseException("AI 服务返回了空内容，请重试")
        if (text.toByteArray(Charsets.UTF_8).size > limits.outputBytes) throw AiResponseException(TOO_LARGE)
        return text
    }

    private suspend fun readStream(body: ResponseBody, onPartial: suspend (String) -> Unit): String {
        val input = LimitedInputStream(body.byteStream(), limits.streamBytes, TOO_LARGE).buffered()
        val output = StringBuilder()
        var outputBytes = 0L
        var line = input.readBoundedLine(limits.lineBytes)
        while (line != null) {
            currentCoroutineContext().ensureActive()
            val piece = readStreamPiece(line)
            if (piece.done) break
            outputBytes += piece.text.toByteArray(Charsets.UTF_8).size
            if (outputBytes > limits.outputBytes) throw AiResponseException(TOO_LARGE)
            if (piece.text.isNotEmpty()) {
                output.append(piece.text)
                withContext(partialDispatcher) { onPartial(output.toString()) }
            }
            line = input.readBoundedLine(limits.lineBytes)
        }
        return output.toString().trim().ifBlank { throw AiResponseException("AI 流式输出中断，请重试") }
    }

    private fun readStreamPiece(line: String): StreamPiece {
        val data = line.removePrefix("data:").trim()
        return when {
            !line.startsWith("data:") || data.isBlank() -> StreamPiece()
            data == "[DONE]" -> StreamPiece(done = true)
            else -> {
                requireBoundedJson(data)
                val choices = JSONObject(data).getJSONArray("choices")
                if (choices.length() == 0) {
                    StreamPiece()
                } else {
                    val piece = choices.getJSONObject(0)
                        .optJSONObject("delta")?.optString("content").orEmpty()
                    StreamPiece(piece)
                }
            }
        }
    }

    private data class StreamPiece(val text: String = "", val done: Boolean = false)

    private fun InputStream.readBoundedLine(maxBytes: Int): String? {
        val output = ByteArrayOutputStream()
        var next = read()
        while (next >= 0 && next != '\n'.code) {
            if (output.size() >= maxBytes) throw AiResponseException(TOO_LARGE)
            output.write(next)
            next = read()
        }
        return if (next == -1 && output.size() == 0) null else output.toString(Charsets.UTF_8.name()).trimEnd('\r')
    }

    private fun httpError(code: Int): String = when (code) {
        401, 403 -> "API Key 无效或已过期，请检查 AI 设置"
        404 -> "模型不存在，请检查模型名称"
        429 -> "AI 服务请求过于频繁或额度不足，请稍后重试"
        in 300..399 -> "AI 服务返回了跳转地址，为保护数据已停止请求"
        in 500..599 -> "AI 服务暂时不可用，请稍后重试"
        else -> "AI 请求失败，请稍后重试"
    }

    private companion object {
        const val TOO_LARGE = "AI 返回内容超过安全限制，请稍后重试"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private class AiResponseException(message: String) : IOException(message)

internal fun aiFailureMessage(error: Throwable, fallback: String): String =
    if (error is AiResponseException) error.message ?: fallback else fallback
