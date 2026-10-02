package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.GithubDownloads
import com.xuzheng.tiyuengine.data.LimitedInputStream
import com.xuzheng.tiyuengine.data.QuizArchive
import com.xuzheng.tiyuengine.data.UpdateInfo
import com.xuzheng.tiyuengine.data.UpdatePackagePolicy
import com.xuzheng.tiyuengine.data.readUtf8Bounded
import com.xuzheng.tiyuengine.data.requireBoundedJson
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadSecurityTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun unknownLengthStreamStopsAfterLimitPlusOneByte() {
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int {
                consumed++
                return 'x'.code
            }
        }
        assertThrows(IOException::class.java) { endless.readUtf8Bounded(32, "too large") }
        assertEquals(33, consumed)
    }

    @Test
    fun exactByteLimitAcceptsUtf8AndSkipCannotBypassQuota() {
        val text = "安全"
        assertEquals(text, ByteArrayInputStream(text.toByteArray()).readUtf8Bounded(6, "too large"))
        val input = LimitedInputStream(ByteArrayInputStream(ByteArray(20)), 10, "too large")
        assertThrows(IOException::class.java) { input.skip(20) }
        assertEquals(11L, input.bytesRead)
    }

    @Test
    fun extractsOnlyTopLevelDataAndNeverTraversesPaths() {
        val target = temporary.newFolder()
        val archive = zipOf(
            "repo/data/manifest.json" to "{}".toByteArray(),
            "repo/android/app/src/main/assets/data/manifest.json" to "old".toByteArray(),
            "repo/data/../../escaped.json" to "bad".toByteArray(),
        )
        QuizArchive.extract(ByteArrayInputStream(archive), target)
        assertEquals(listOf("manifest.json"), target.list()!!.toList())
        assertEquals("{}", target.resolve("manifest.json").readText())
    }

    @Test
    fun rejectsOversizedJsonWhileDecompressing() {
        val archive = zipOf("repo/data/manifest.json" to ByteArray((QuizArchive.MAX_JSON_BYTES + 1).toInt()))
        assertThrows(IOException::class.java) {
            QuizArchive.extract(ByteArrayInputStream(archive), temporary.newFolder())
        }
    }

    @Test
    fun skippedEntriesStillConsumeExpansionBudget() {
        val archive = zipOf("repo/irrelevant.bin" to ByteArray(100), "repo/data/manifest.json" to "{}".toByteArray())
        assertThrows(IOException::class.java) {
            QuizArchive.extract(ByteArrayInputStream(archive), temporary.newFolder(), maxExpandedBytes = 32)
        }
    }

    @Test
    fun rejectsUntrustedRedirectTargets() {
        assertTrue(GithubDownloads.allowedUrl("https://release-assets.githubusercontent.com/a".toHttpUrl()))
        listOf(
            "http://github.com/a",
            "https://github.com.evil.example/a",
            "https://evilgithubusercontent.com/a",
            "https://github.com:444/a",
            "https://user:secret@github.com/a",
        ).forEach { assertFalse(it, GithubDownloads.allowedUrl(it.toHttpUrl())) }
    }

    @Test
    fun apkMustMatchPackageAndMetadataAndAdvanceBuild() {
        val update = UpdateInfo("1.0.7", "", "", 10)
        assertTrue(UpdatePackagePolicy.accepts("app", "1.0.7", 10, "app", 9, update))
        assertFalse(UpdatePackagePolicy.accepts("other", "1.0.7", 10, "app", 9, update))
        assertFalse(UpdatePackagePolicy.accepts("app", "1.0.8", 10, "app", 9, update))
        assertFalse(UpdatePackagePolicy.accepts("app", "1.0.7", 11, "app", 9, update))
        assertFalse(UpdatePackagePolicy.accepts("app", "1.0.7", 9, "app", 9, null))
        assertFalse(UpdatePackagePolicy.accepts("app", "1.0.7", 8, "app", 9, null))
    }

    @Test
    fun rejectsDeepJsonBeforePlatformParsingButAcceptsBracketsInStrings() {
        requireBoundedJson("""{"text":"[[[{}]]]", "items":[{"value":true}]}""")
        assertThrows(IllegalArgumentException::class.java) {
            requireBoundedJson("[".repeat(100) + "0" + "]".repeat(100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireBoundedJson("""{'unquoted': []}""")
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
