package com.github.ShinkaiKung.verbalkiller.info

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressFilterTest {
    @Test
    fun `filter labels use the same learning-state terms as practice`() {
        assertEquals(
            listOf("全部", "未练", "强化中", "本轮通过", "高频错词"),
            ProgressFilter.entries.map { it.label },
        )
    }

    @Test
    fun `in progress filter explains the three correct threshold`() {
        assertEquals("连续答对不足 3 次", ProgressFilter.IN_PROGRESS.description)
    }
}
