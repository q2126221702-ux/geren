package com.xuzheng.tiyuengine.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal object GithubDownloads {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    internal fun allowedUrl(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() &&
        (
            url.host in setOf("github.com", "api.github.com", "codeload.github.com") ||
                url.host.endsWith(".githubusercontent.com")
            )

    fun <T> read(url: String, maxBytes: Long, block: (InputStream) -> T): T {
        var current = url.toHttpUrl()
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        repeat(6) {
            require(allowedUrl(current)) { "更新来源不是受信任的 HTTPS 地址" }
            val call = client.newCall(
                Request.Builder().url(current).header("User-Agent", "TiyuEngine").build(),
            )
            call.timeout().deadlineNanoTime(deadline)
            call.execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    current = redirectUrl(response, current)
                } else {
                    return block(boundedBody(response, maxBytes))
                }
            }
        }
        throw IOException("下载跳转次数过多")
    }

    private fun redirectUrl(response: Response, current: HttpUrl): HttpUrl =
        response.header("Location")?.let(current::resolve)
            ?: throw IOException("服务器返回了无效的跳转地址")

    private fun boundedBody(response: Response, maxBytes: Long): InputStream {
        if (response.code == 404) throw IOException("未找到发布文件，请稍后重试")
        check(response.isSuccessful) { "下载失败 (${response.code})" }
        val body = response.body ?: throw IOException("服务器没有返回内容")
        require(body.contentLength() <= maxBytes) { "下载文件超过大小限制" }
        return LimitedInputStream(body.byteStream(), maxBytes, "下载文件超过大小限制")
    }
}
