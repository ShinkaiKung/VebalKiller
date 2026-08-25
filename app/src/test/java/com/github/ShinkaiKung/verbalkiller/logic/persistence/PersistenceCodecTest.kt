package com.github.ShinkaiKung.verbalkiller.logic.persistence

import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.GroupEntity
import com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceCodecTest {
    @Test
    fun groupRoundTrip_preservesLegacyJsonFields() {
        val group = Group(
            uuid = 42,
            words = linkedSetOf("abate", "mitigate"),
            memoryHistory = mutableListOf(MemoryRecord(timestamp = 99, isCorrect = true)),
            chineseMeaning = "缓和",
            errorStates = mutableMapOf("abate" to 2),
        )

        val result = PersistenceCodec.entityToGroup(PersistenceCodec.groupToEntity(group))

        assertEquals(group, result)
        assertTrue(result.words is LinkedHashSet<*>)
    }

    @Test
    fun malformedLegacyJson_degradesToEmptyCollections() {
        val entity = GroupEntity(
            uuid = 42,
            wordsJson = "not-json",
            memoryHistoryJson = "null",
            chineseMeaning = "meaning",
            errorStatesJson = "{broken",
        )

        val result = PersistenceCodec.entityToGroup(entity)

        assertTrue(result.words.isEmpty())
        assertTrue(result.memoryHistory.isEmpty())
        assertTrue(result.errorStates.isEmpty())
        assertEquals("meaning", result.chineseMeaning)
    }

    @Test
    fun attemptRoundTrip_preservesOptionalAndWordFields() {
        val attempt = PracticeAttempt(
            id = 8,
            questionId = "question-1",
            groupId = 4,
            timestamp = 123,
            isCorrect = false,
            selectedWords = listOf("a", "b"),
            answerWords = listOf("c", "d"),
            durationMillis = 456,
            source = "daily-review",
        )

        val result = PersistenceCodec.entityToAttempt(PersistenceCodec.attemptToEntity(attempt))

        assertEquals(attempt, result)
    }
}
