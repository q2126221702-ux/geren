package com.xuzheng.tiyuengine.data

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Counts actual bytes, including one probe byte, rather than trusting Content-Length. */
internal class LimitedInputStream(
    input: InputStream,
    private val maxBytes: Long,
    private val errorMessage: String,
) : FilterInputStream(input) {
    var bytesRead: Long = 0
        private set

    init {
        require(maxBytes >= 0)
    }

    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) record(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val allowed = minOf(length.toLong(), maxBytes - bytesRead + 1).toInt()
        val count = `in`.read(buffer, offset, allowed)
        if (count > 0) record(count)
        return count
    }

    override fun skip(count: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = count.coerceAtLeast(0)
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            remaining -= read
        }
        return count.coerceAtLeast(0) - remaining
    }

    override fun markSupported(): Boolean = false

    override fun reset(): Unit = throw IOException("不支持重置受限输入流")

    private fun record(count: Int) {
        bytesRead += count
        if (bytesRead > maxBytes) throw IOException(errorMessage)
    }
}

internal fun InputStream.copyBoundedTo(output: OutputStream, maxBytes: Long, errorMessage: String): Long =
    LimitedInputStream(this, maxBytes, errorMessage).copyTo(output)

internal fun InputStream.readUtf8Bounded(maxBytes: Long, errorMessage: String): String {
    val output = ByteArrayOutputStream()
    copyBoundedTo(output, maxBytes, errorMessage)
    return output.toString(Charsets.UTF_8.name())
}
