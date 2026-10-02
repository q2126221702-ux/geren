package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.AiCompletionRequest
import com.xuzheng.tiyuengine.data.AiHttpTransport
import com.xuzheng.tiyuengine.data.AiResponseLimits
import com.xuzheng.tiyuengine.data.aiFailureMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiHttpTransportTest {
    @Test
    fun acceptsNormalJsonAndStreamingContentWithUsageAndDoneEvents() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"正常结果"}}]}"""))
            assertEquals("正常结果", request(server))
            val events = listOf(
                ": heartbeat",
                "data: ",
                """data: {"choices":[{"delta":{"content":"你好"}}]}""",
                """data: {"choices":[{"delta":{"content":"，世界"}}]}""",
                """data: {"choices":[],"usage":{"total_tokens":12}}""",
                "data: [DONE]",
            ).joinToString("\n\n", postfix = "\n")
            server.enqueue(MockResponse().setBody(events))
            val partials = mutableListOf<String>()
            val transport = AiHttpTransport(partialDispatcher = Dispatchers.Unconfined)
            val result = transport.complete(completionRequest(server, true)) { partials.add(it) }
            assertEquals("你好，世界", result)
            assertEquals(listOf("你好", "你好，世界"), partials)
        }
    }

    @Test
    fun rejectsRedirectWithoutSendingPromptOrCredentialToAnotherServer() = runBlocking {
        MockWebServer().use { source ->
            MockWebServer().use { target ->
                source.start()
                target.start()
                source.enqueue(MockResponse().setResponseCode(307).setHeader("Location", target.url("/stolen")))
                val failure = failure { request(source) }
                assertTrue(failure.message.orEmpty().contains("跳转"))
                assertEquals("Bearer dummy-api-key", source.takeRequest().getHeader("Authorization"))
                assertNull(target.takeRequest(100, TimeUnit.MILLISECONDS))
            }
        }
    }

    @Test
    fun remoteErrorAndMalformedJsonNeverAppearInDisplayedErrors() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val remoteError = """{"error":{"message":"dummy-secret-token and private-answer"}}"""
            server.enqueue(MockResponse().setResponseCode(400).setBody(remoteError))
            val error = failure { request(server) }
            assertFalse(aiFailureMessage(error, "fallback").contains("dummy-secret"))
            assertFalse(aiFailureMessage(error, "fallback").contains("private-answer"))
            server.enqueue(MockResponse().setBody("invalid JSON containing dummy-secret-token"))
            val malformed = failure { request(server) }
            assertFalse(aiFailureMessage(malformed, "fallback").contains("dummy-secret"))
            assertTrue(malformed.message.orEmpty().contains("格式异常"))
        }
    }

    @Test
    fun boundsChunkedJsonAndErrorBodiesWithoutTrustingContentLength() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setChunkedBody("x".repeat(65), 7))
            assertOversized(failure { request(server, limits = AiResponseLimits(jsonBytes = 64)) })
            server.enqueue(MockResponse().setResponseCode(500).setChunkedBody("x".repeat(33), 7))
            assertOversized(failure { request(server, limits = AiResponseLimits(errorBytes = 32)) })
        }
    }

    @Test
    fun rejectsDeepJsonBeforeParsingBothNormalAndStreamingResponses() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val deepJson = "{\"x\":".repeat(40) + "null" + "}".repeat(40)
            server.enqueue(MockResponse().setBody(deepJson))
            assertTrue(failure { request(server) }.message.orEmpty().contains("格式异常"))
            server.enqueue(MockResponse().setBody("data: $deepJson\n"))
            assertTrue(failure { request(server, stream = true) }.message.orEmpty().contains("格式异常"))
        }
    }

    @Test
    fun boundsEachSseLineIncludingNonDataLines() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(":" + "x".repeat(33) + "\n"))
            assertOversized(failure { request(server, stream = true, limits = AiResponseLimits(lineBytes = 32)) })
        }
    }

    @Test
    fun boundsTotalSseTrafficEvenWhenItContainsOnlyHeartbeats() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setChunkedBody(": ping\n".repeat(20), 7))
            assertOversized(failure { request(server, stream = true, limits = AiResponseLimits(streamBytes = 64)) })
        }
    }

    @Test
    fun boundsAccumulatedOutputInUtf8Bytes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val events = """data: {"choices":[{"delta":{"content":"你好"}}]}""" + "\n\ndata: [DONE]\n"
            server.enqueue(MockResponse().setBody(events))
            assertOversized(failure { request(server, stream = true, limits = AiResponseLimits(outputBytes = 5)) })
        }
    }

    @Test
    fun cancellationClosesAnHttpCallWaitingForHeaders() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
            val transport = AiHttpTransport(http, Dispatchers.Unconfined)
            val pending = async(Dispatchers.Default) { request(server, transport = transport) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(2_000) { pending.cancelAndJoin() }
            assertTrue(pending.isCancelled)
        }
    }

    @Test
    fun totalCallTimeoutStopsContinuouslyDrippingResponseBytes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody(": ping\n".repeat(200)).throttleBody(7, 50, TimeUnit.MILLISECONDS))
            val http = OkHttpClient.Builder()
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(200, TimeUnit.MILLISECONDS)
                .build()
            val transport = AiHttpTransport(http, Dispatchers.Unconfined)
            val error = failure { request(server, stream = true, transport = transport) }
            assertTrue(error.message.orEmpty().contains("超时"))
        }
    }

    private suspend fun request(
        server: MockWebServer,
        stream: Boolean = false,
        limits: AiResponseLimits = AiResponseLimits(),
        transport: AiHttpTransport = AiHttpTransport(partialDispatcher = Dispatchers.Unconfined, limits = limits),
    ): String = transport.complete(completionRequest(server, stream))

    private fun completionRequest(server: MockWebServer, stream: Boolean): AiCompletionRequest = AiCompletionRequest(
        server.url("/v1").toString(),
        "dummy-model",
        "dummy-api-key",
        "private dummy prompt",
        64,
        0.0,
        stream,
    )

    private suspend fun failure(block: suspend () -> Unit): IOException {
        try {
            block()
        } catch (error: IOException) {
            return error
        }
        throw AssertionError("Expected a bounded/local AI request failure")
    }

    private fun assertOversized(error: IOException) {
        assertTrue(error.message.orEmpty().contains("安全限制"))
    }
}
