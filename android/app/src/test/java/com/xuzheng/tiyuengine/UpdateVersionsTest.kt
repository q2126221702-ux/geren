package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.UpdateVersions
import org.junit.Assert.assertFalse
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
}
