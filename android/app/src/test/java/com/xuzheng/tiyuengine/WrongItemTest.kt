package com.xuzheng.tiyuengine

import com.xuzheng.tiyuengine.data.ReviewStatus
import com.xuzheng.tiyuengine.data.WrongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WrongItemTest {
    @Test
    fun statusFollowsCorrectStreak() {
        fun item(streak: Int) = WrongItem("q", 1, streak, 0, 0, 0)
        assertEquals(ReviewStatus.UNMASTERED, item(0).status)
        assertEquals(ReviewStatus.REVIEWING, item(1).status)
        assertEquals(ReviewStatus.REVIEWING, item(2).status)
        assertEquals(ReviewStatus.MASTERED, item(3).status)
    }
}
