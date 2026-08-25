package com.github.ShinkaiKung.verbalkiller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstPracticeSchedulerTest {
    @Test
    fun `wrong answer resets progress and returns after three questions`() {
        val transition = BurstPracticeScheduler.review(previousProgress = 2, wasCorrect = false)

        assertEquals(0, transition.progress)
        assertEquals(3, transition.delayInQuestions)
        assertFalse(transition.isPassed)
    }

    @Test
    fun `three correct answers use six and twelve question gaps before passing`() {
        val first = BurstPracticeScheduler.review(previousProgress = null, wasCorrect = true)
        val second = BurstPracticeScheduler.review(first.progress, wasCorrect = true)
        val third = BurstPracticeScheduler.review(second.progress, wasCorrect = true)

        assertEquals(1, first.progress)
        assertEquals(6, first.delayInQuestions)
        assertEquals(2, second.progress)
        assertEquals(12, second.delayInQuestions)
        assertEquals(3, third.progress)
        assertNull(third.delayInQuestions)
        assertTrue(third.isPassed)
    }

    @Test
    fun `wrong answer between correct answers restarts the round`() {
        val first = BurstPracticeScheduler.review(null, wasCorrect = true)
        val reset = BurstPracticeScheduler.review(first.progress, wasCorrect = false)
        val restarted = BurstPracticeScheduler.review(reset.progress, wasCorrect = true)

        assertEquals(0, reset.progress)
        assertEquals(1, restarted.progress)
        assertEquals(6, restarted.delayInQuestions)
    }

    @Test
    fun `passed progress stays capped`() {
        val transition = BurstPracticeScheduler.review(3, wasCorrect = true)

        assertEquals(3, transition.progress)
        assertTrue(transition.isPassed)
    }
}
