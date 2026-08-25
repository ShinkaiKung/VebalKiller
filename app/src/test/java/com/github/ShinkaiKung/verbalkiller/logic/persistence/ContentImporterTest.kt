package com.github.ShinkaiKung.verbalkiller.logic.persistence

import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentImporterTest {
    @Test
    fun parseWordsCsv_removesUtf8BomFromFirstId() {
        val content = "\uFEFF1,mitigate,abate;curtail,缓和\n".toByteArray(Charsets.UTF_8)

        val parsed = parseWordsCsv(content)

        assertEquals(1, parsed.groups.single().uuid)
        assertEquals(setOf("mitigate", "abate", "curtail"), parsed.groups.single().words)
        assertEquals(64, parsed.contentHash.length)
    }

    @Test
    fun parseWordsCsv_supportsQuotedCommaAndEscapedQuote() {
        val content = "1,plain,simple,\"简单,\"\"朴素\"\"\"\n".toByteArray()

        val parsed = parseWordsCsv(content)

        assertEquals("简单,\"朴素\"", parsed.groups.single().chineseMeaning)
    }

    @Test
    fun mergeImportedGroup_updatesContentButRetainsProgress() {
        val history = mutableListOf(MemoryRecord(timestamp = 123, isCorrect = false))
        val existing = Group(
            uuid = 7,
            words = mutableSetOf("old", "stale"),
            memoryHistory = history,
            chineseMeaning = "旧",
            errorStates = mutableMapOf("old" to 3),
        )
        val imported = Group(
            uuid = 7,
            words = mutableSetOf("new", "fresh"),
            chineseMeaning = "新",
        )

        val merged = mergeImportedGroup(existing, imported)

        assertEquals(setOf("new", "fresh"), merged.words)
        assertEquals("新", merged.chineseMeaning)
        assertEquals(history, merged.memoryHistory)
        assertEquals(mapOf("old" to 3), merged.errorStates)
        assertFalse(merged.memoryHistory === existing.memoryHistory)
        assertFalse(merged.errorStates === existing.errorStates)
    }

    @Test
    fun legacyAttemptsFor_backfillsEveryJsonHistoryRecord() {
        val groups = listOf(
            Group(
                uuid = 7,
                memoryHistory = mutableListOf(
                    MemoryRecord(timestamp = 10, isCorrect = true),
                    MemoryRecord(timestamp = 20, isCorrect = false),
                ),
            ),
            Group(uuid = 8),
        )

        val attempts = legacyAttemptsFor(groups)

        assertEquals(2, attempts.size)
        assertEquals(listOf(10L, 20L), attempts.map { it.timestamp })
        assertEquals(listOf(true, false), attempts.map { it.isCorrect })
        assertTrue(attempts.all { it.groupId == 7 })
        assertTrue(attempts.all { it.source == LEGACY_HISTORY_SOURCE })
    }

    @Test
    fun legacyReviewStatesFor_derivesTailStreakLapsesAndInterval() {
        val group = Group(
            uuid = 7,
            memoryHistory = mutableListOf(
                MemoryRecord(timestamp = 100, isCorrect = false),
                MemoryRecord(timestamp = 400, isCorrect = true),
                MemoryRecord(timestamp = 200, isCorrect = true),
                MemoryRecord(timestamp = 300, isCorrect = true),
            ),
        )

        val review = legacyReviewStatesFor(listOf(group), now = 1_000).single()

        assertEquals(2, review.box)
        assertEquals(400L + 7L * DAY_MILLIS_FOR_TEST, review.dueAt)
        assertEquals(400L, review.lastReviewedAt)
        assertEquals(true, review.lastCorrect)
        assertEquals(3, review.consecutiveCorrect)
        assertEquals(1, review.lapseCount)
        assertEquals(1_000L, review.updatedAt)
    }

    @Test
    fun legacyReviewStatesFor_lastErrorResetsBoxAndUsesOneDayInterval() {
        val group = Group(
            uuid = 7,
            memoryHistory = mutableListOf(
                MemoryRecord(timestamp = 100, isCorrect = true),
                MemoryRecord(timestamp = 200, isCorrect = false),
            ),
        )

        val review = legacyReviewStatesFor(listOf(group), now = 1_000).single()

        assertEquals(0, review.box)
        assertEquals(200L + DAY_MILLIS_FOR_TEST, review.dueAt)
        assertEquals(false, review.lastCorrect)
        assertEquals(0, review.consecutiveCorrect)
        assertEquals(1, review.lapseCount)
    }

    @Test
    fun legacyReviewStatesFor_capsLongCorrectStreakAtThirtyDayBox() {
        val group = Group(
            uuid = 7,
            memoryHistory = (1L..7L).mapTo(mutableListOf()) { timestamp ->
                MemoryRecord(timestamp = timestamp, isCorrect = true)
            },
        )

        val review = legacyReviewStatesFor(listOf(group), now = 1_000).single()

        assertEquals(4, review.box)
        assertEquals(7L + 30L * DAY_MILLIS_FOR_TEST, review.dueAt)
        assertEquals(7, review.consecutiveCorrect)
    }

    @Test
    fun legacyReviewStatesFor_queuesErrorOnlyGroupAndSkipsUntouchedGroup() {
        val errorOnly = Group(uuid = 7, errorStates = mutableMapOf("wrong" to 3))
        val untouched = Group(uuid = 8)

        val reviews = legacyReviewStatesFor(listOf(errorOnly, untouched), now = 1_000)

        val review = reviews.single()
        assertEquals(7, review.groupId)
        assertEquals(0, review.box)
        assertEquals(1_000L, review.dueAt)
        assertNull(review.lastReviewedAt)
        assertNull(review.lastCorrect)
    }

    @Test
    fun calculateDashboard_handlesEmptyAndAggregatedData() {
        val empty = calculateDashboard(emptyList(), emptyList(), emptyList(), emptyList(), now = 10)
        assertNull(empty.accuracy)

        val groups = listOf(Group(1), Group(2))
        val attempts = listOf(
            PracticeAttempt(groupId = 1, timestamp = 5, isCorrect = true),
            PracticeAttempt(groupId = 1, timestamp = 8, isCorrect = false),
        )
        val reviews = listOf(
            ReviewState(groupId = 1, box = 1, dueAt = 9, updatedAt = 8),
            ReviewState(groupId = 2, box = 1, dueAt = 11, updatedAt = 8),
        )
        val confusions = listOf(
            Confusion(groupId = 1, word = "a", confusedWith = "b", count = 3, lastOccurredAt = 8)
        )

        val result = calculateDashboard(groups, reviews, attempts, confusions, now = 10)

        assertEquals(2, result.totalGroups)
        assertEquals(1, result.practicedGroups)
        assertEquals(1, result.dueReviews)
        assertEquals(2, result.totalAttempts)
        assertEquals(1, result.correctAttempts)
        assertEquals(0.5, result.accuracy!!, 0.0)
        assertEquals(3, result.confusionEvents)
        assertEquals(8L, result.lastAttemptAt)
        assertTrue(result.accuracy!! in 0.0..1.0)
    }

    private companion object {
        const val DAY_MILLIS_FOR_TEST = 86_400_000L
    }
}
