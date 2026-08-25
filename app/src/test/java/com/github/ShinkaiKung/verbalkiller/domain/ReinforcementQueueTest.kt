package com.github.ShinkaiKung.verbalkiller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReinforcementQueueTest {
    @Test
    fun `item becomes due after exact completed question gap`() {
        val queue = ReinforcementQueue.empty<String>()
            .schedule("group", completedQuestionCount = 10, delayInQuestions = 12)

        assertTrue(queue.dueItems(21).isEmpty())
        assertEquals(listOf("group"), queue.dueItems(22))
    }

    @Test
    fun `latest answer replaces an older schedule for the same group`() {
        val queue = ReinforcementQueue.empty<String>()
            .schedule("group", completedQuestionCount = 10, delayInQuestions = 3)
            .schedule("group", completedQuestionCount = 11, delayInQuestions = 12)

        assertEquals(1, queue.size)
        assertEquals(23, queue.scheduled.single().dueAtCompletedQuestionCount)
    }

    @Test
    fun `poll returns earliest due item without mutating original queue`() {
        val queue = ReinforcementQueue.empty<String>()
            .schedule("later", completedQuestionCount = 10, delayInQuestions = 12)
            .schedule("first", completedQuestionCount = 10, delayInQuestions = 3)

        val poll = queue.pollDue(22)

        assertEquals("first", poll.item)
        assertEquals(2, queue.size)
        assertEquals(setOf("later"), poll.queue.items)
    }

    @Test
    fun `poll can restrict due items to the selected mode`() {
        val queue = ReinforcementQueue.empty<String>()
            .schedule("smart", 0, 3)
            .schedule("error", 0, 3)

        val poll = queue.pollDue(3) { it == "error" }

        assertEquals("error", poll.item)
        assertEquals(setOf("smart"), poll.queue.items)
    }

    @Test
    fun `remove all clears target groups only`() {
        val queue = ReinforcementQueue.empty<String>()
            .schedule("a", 0, 3)
            .schedule("b", 0, 6)
            .schedule("c", 0, 12)
            .removeAll(setOf("a", "c"))

        assertEquals(setOf("b"), queue.items)
    }

    @Test
    fun `nonpositive delay is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReinforcementQueue.empty<String>().schedule("group", 0, 0)
        }
        assertNull(ReinforcementQueue.empty<String>().pollDue(0).item)
    }
}
