package com.github.ShinkaiKung.verbalkiller.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeModePolicyTest {
    @Test
    fun `smart practice focuses active pool before unseen groups`() {
        val policy = PracticeMode.SMART.candidatePolicy(
            active = listOf(1),
            unseen = listOf(2),
            errors = listOf(3),
            all = listOf(4, 3, 2, 1),
        )

        assertEquals(listOf(1), policy.focus)
        assertEquals(listOf(1, 2, 4, 3, 2, 1), policy.prioritized)
        assertFalse(policy.requiresFocus)
    }

    @Test
    fun `smart practice focuses unseen groups when active pool is empty`() {
        val policy = PracticeMode.SMART.candidatePolicy(
            active = emptyList(),
            unseen = listOf(2),
            errors = listOf(3),
            all = listOf(4, 3, 2, 1),
        )

        assertEquals(listOf(2), policy.focus)
        assertEquals(listOf(2, 4, 3, 2, 1), policy.prioritized)
    }

    @Test
    fun `smart practice becomes random reinforcement after active and unseen are empty`() {
        val policy = PracticeMode.SMART.candidatePolicy(
            active = emptyList<Int>(),
            unseen = emptyList(),
            errors = emptyList(),
            all = listOf(4, 3, 2, 1),
        )

        assertTrue(policy.focus.isEmpty())
        assertEquals(listOf(4, 3, 2, 1), policy.prioritized)
        assertFalse(policy.requiresFocus)
    }

    @Test
    fun `error practice requires an active error group`() {
        val policy = PracticeMode.ERRORS.candidatePolicy(
            active = listOf(1),
            unseen = listOf(2),
            errors = listOf(3),
            all = listOf(4, 3, 2, 1),
        )

        assertEquals(listOf(3), policy.focus)
        assertEquals(listOf(3, 4, 3, 2, 1), policy.prioritized)
        assertTrue(policy.requiresFocus)
    }

    @Test
    fun `random practice has no required focus`() {
        val policy = PracticeMode.RANDOM.candidatePolicy(
            active = listOf(1),
            unseen = listOf(2),
            errors = listOf(3),
            all = listOf(4, 3, 2, 1),
        )

        assertTrue(policy.focus.isEmpty())
        assertEquals(listOf(4, 3, 2, 1), policy.prioritized)
        assertFalse(policy.requiresFocus)
    }

    @Test
    fun `mode source codes stay explicit and unique`() {
        assertEquals(listOf("smart", "errors", "random"), PracticeMode.entries.map { it.sourceCode })
        assertEquals(PracticeMode.entries.size, PracticeMode.entries.map { it.sourceCode }.toSet().size)
    }
}
