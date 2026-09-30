package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.UpdateVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionsTest {
    @Test
    fun detectsNewerPatchAndMinorVersions() {
        assertTrue(UpdateVersions.isNewer("1.0.6", "1.0.5"))
        assertTrue(UpdateVersions.isNewer("1.1.0", "1.0.9"))
        assertTrue(UpdateVersions.isNewer("v1.0.6", "1.0.5"))
    }

    @Test
    fun rejectsSameOrOlderVersions() {
        assertFalse(UpdateVersions.isNewer("1.0.5", "1.0.5"))
        assertFalse(UpdateVersions.isNewer("1.0.4", "1.0.5"))
        assertFalse(UpdateVersions.isNewer("1.0", "1.0.1"))
    }

    @Test
    fun comparesDifferentLengthVersionNumbers() {
        assertTrue(UpdateVersions.isNewer("1.0.0.1", "1.0.0"))
        assertFalse(UpdateVersions.isNewer("1.0", "1.0.1"))
    }

    @Test
    fun detectsSameVersionWithNewerBuildOnly() {
        assertTrue(UpdateVersions.isNewer("1.0.7", "1.0.7", 9, 8))
        assertFalse(UpdateVersions.isNewer("1.0.7", "1.0.7", 9, 9))
        assertFalse(UpdateVersions.isNewer("1.0.7", "1.0.7", 8, 9))
        assertFalse(UpdateVersions.isNewer("1.0.7", "1.0.7", null, 8))
        assertFalse(UpdateVersions.isNewer("1.0.6", "1.0.7", 10, 8))
    }

    @Test
    fun extractsCanonicalVersionAndHidesReleaseMarkers() {
        val metadata = UpdateVersions.parseReleaseMetadata(
            "v1.0.7.1",
            """
                <!-- android-version-name: 1.0.7 -->
                <!-- android-version-code: 9 -->
                修复 AI 分析和顶部白条。
            """.trimIndent(),
        )

        assertEquals("1.0.7", metadata.versionName)
        assertEquals(9, metadata.versionCode)
        assertEquals("修复 AI 分析和顶部白条。", metadata.notes)
        assertTrue(UpdateVersions.isNewer(metadata.versionName, "1.0.7", metadata.versionCode, 8))
        assertFalse(UpdateVersions.isNewer(metadata.versionName, "1.0.7", metadata.versionCode, 9))
    }

    @Test
    fun keepsLegacyReleaseComparisonAndNotes() {
        val metadata = UpdateVersions.parseReleaseMetadata("v1.0.8", "修复问题。")

        assertEquals("1.0.8", metadata.versionName)
        assertNull(metadata.versionCode)
        assertEquals("修复问题。", metadata.notes)
        assertTrue(UpdateVersions.isNewer(metadata.versionName, "1.0.7", metadata.versionCode, 9))
        assertTrue(UpdateVersions.isNewer("1.0.7.1", "1.0.7"))
    }

    @Test
    fun ignoresInvalidBuildMetadataAndUsesDefaultNotes() {
        val metadata = UpdateVersions.parseReleaseMetadata(
            "v1.0.7.1",
            "<!-- android-version-name: v1.0.7 -->\n<!-- android-version-code: invalid -->",
        )

        assertEquals("1.0.7", metadata.versionName)
        assertNull(metadata.versionCode)
        assertEquals("修复问题并优化使用体验。", metadata.notes)
        assertFalse(UpdateVersions.isNewer(metadata.versionName, "1.0.7", metadata.versionCode, 8))
    }
}
