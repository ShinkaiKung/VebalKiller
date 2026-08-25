package com.github.ShinkaiKung.verbalkiller.logic.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePersistenceLogicTest {
    @Test
    fun calculateReviewUpdate_createsFirstCorrectReview() {
        var callbackPrevious: ReviewState? = null
        val result = calculateReviewUpdate(
            groupId = 7,
            previous = null,
            correct = true,
            timestamp = 100,
        ) { previous, correct, timestamp ->
            callbackPrevious = previous
            assertTrue(correct)
            ReviewSchedule(box = 0, dueAt = timestamp + DAY_MILLIS)
        }

        assertEquals(null, callbackPrevious)
        assertEquals(7, result.groupId)
        assertEquals(0, result.box)
        assertEquals(100L + DAY_MILLIS, result.dueAt)
        assertEquals(true, result.lastCorrect)
        assertEquals(1, result.consecutiveCorrect)
        assertEquals(0, result.lapseCount)
    }

    @Test
    fun calculateReviewUpdate_readsPreviousAndResetsOnError() {
        val previous = ReviewState(
            groupId = 7,
            box = 2,
            dueAt = 90,
            lastReviewedAt = 80,
            lastCorrect = true,
            consecutiveCorrect = 3,
            lapseCount = 2,
            updatedAt = 80,
        )
        var callbackSawPrevious = false

        val result = calculateReviewUpdate(
            groupId = 7,
            previous = previous,
            correct = false,
            timestamp = 100,
        ) { observed, correct, timestamp ->
            callbackSawPrevious = observed == previous
            assertFalse(correct)
            ReviewSchedule(box = 0, dueAt = timestamp + DAY_MILLIS)
        }

        assertTrue(callbackSawPrevious)
        assertEquals(false, result.lastCorrect)
        assertEquals(0, result.consecutiveCorrect)
        assertEquals(3, result.lapseCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateReviewUpdate_rejectsPastSchedule() {
        calculateReviewUpdate(
            groupId = 7,
            previous = null,
            correct = true,
            timestamp = 100,
        ) { _, _, _ -> ReviewSchedule(box = 0, dueAt = 99) }
    }

    @Test
    fun updatedErrorStates_decrementsCorrectAndMarksIncorrect() {
        val current = mapOf("a" to 2, "b" to 0)

        val correct = updatedErrorStates(current, listOf("a", "a", "b", "new"), correct = true)
        val incorrect = updatedErrorStates(current, listOf("a", "new"), correct = false)

        assertEquals(mapOf("a" to 1, "b" to 0, "new" to 0), correct)
        assertEquals(mapOf("a" to 3, "b" to 0, "new" to 3), incorrect)
        assertEquals(mapOf("a" to 2, "b" to 0), current)
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
