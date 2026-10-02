package com.xuzheng.tiyuengine.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

internal object QuizArchive {
    const val MAX_DOWNLOAD_BYTES = 128L * 1024 * 1024
    const val MAX_JSON_BYTES = 2L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 256L * 1024 * 1024
    private const val MAX_DATA_BYTES = 16L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private const val MAX_DATA_FILES = 512
    private val discard = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }

    fun safeFileName(name: String): Boolean = name.length in 1..180 && name.endsWith(".json") &&
        name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 }

    fun extract(input: InputStream, target: File, maxExpandedBytes: Long = MAX_EXPANDED_BYTES) {
        var expandedBytes = 0L
        var dataBytes = 0L
        var entries = 0
        val names = mutableSetOf<String>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(++entries <= MAX_ENTRIES) { "题库压缩包文件数量过多" }
                // Only accept the repository's top-level data/, not embedded Android assets.
                val segments = entry.name.split('/')
                val fileName = segments.getOrNull(2)
                val selected = !entry.isDirectory && segments.size == 3 && segments[1] == "data" &&
                    fileName != null && safeFileName(fileName)
                val remaining = maxExpandedBytes - expandedBytes
                if (selected) {
                    require(names.add(fileName!!)) { "题库压缩包包含重复文件" }
                    require(names.size <= MAX_DATA_FILES) { "题库文件数量过多" }
                    val budget = minOf(MAX_JSON_BYTES, MAX_DATA_BYTES - dataBytes, remaining)
                    val count = extractFile(zip, File(target, fileName), budget)
                    dataBytes += count
                    expandedBytes += count
                } else {
                    // Drain through the quota too; closeEntry alone can expand a skipped ZIP bomb.
                    expandedBytes += zip.copyBoundedTo(discard, remaining, "题库压缩包解压后过大")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require("manifest.json" in names) { "仓库中未找到 data/manifest.json" }
    }

    private fun extractFile(zip: ZipInputStream, target: File, budget: Long): Long =
        target.outputStream().use { zip.copyBoundedTo(it, budget, "题库文件超过大小限制") }
}
