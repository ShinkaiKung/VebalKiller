package com.github.ShinkaiKung.verbalkiller.domain

import com.github.ShinkaiKung.verbalkiller.logic.Group
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairDrillQuestionGeneratorTest {
    @Test
    fun `blank question id returns typed failure`() {
        val result = PairDrillQuestionGenerator.generate(
            questionId = "   ",
            prioritizedCandidates = candidates(),
            random = Random(1),
        )

        assertEquals(
            PairDrillGenerationFailureReason.INVALID_QUESTION_ID,
            (result as PairDrillGenerationResult.Failure).reason,
        )
    }

    @Test
    fun `fixed seed produces the same immutable question`() {
        val candidates = candidates()

        val first = success(
            PairDrillQuestionGenerator.generate(
                questionId = "seeded",
                prioritizedCandidates = candidates,
                random = Random(42),
            ),
        )
        val second = success(
            PairDrillQuestionGenerator.generate(
                questionId = "seeded",
                prioritizedCandidates = candidates,
                random = Random(42),
            ),
        )

        assertEquals(first, second)
        assertEquals(setOf(1, 2, 3, 4), first.choices.map { it.sourceGroupId }.toSet())
    }

    @Test
    fun `filters blank tokens and normalizes six unique choices`() {
        val candidates = listOf(
            group(1, "", "   ", " Alpha ", "BETA"),
            group(2, "gamma", "delta", "  "),
            group(3, "epsilon", "zeta"),
            group(4, "eta", "theta"),
        )

        val question = success(
            PairDrillQuestionGenerator.generate(
                questionId = "normalized",
                prioritizedCandidates = candidates,
                requiredTargetGroupIds = setOf(1),
                random = Random(7),
            ),
        )

        assertEquals(6, question.choices.size)
        assertFalse(question.choices.any { it.normalizedText.isBlank() })
        assertEquals(6, question.choices.map { it.normalizedText }.toSet().size)
        assertTrue(question.choices.filter { it.sourceGroupId == 1 }.all { it.text in setOf("Alpha", "BETA") })
    }

    @Test
    fun `removes normalized duplicate groups while preserving a required duplicate id`() {
        val candidates = listOf(
            group(1, "alpha", "beta"),
            group(2, " ALPHA ", "Beta"),
            group(3, "gamma", "delta"),
            group(4, "epsilon", "zeta"),
            group(5, "eta", "theta"),
            group(6, "iota", "kappa"),
        )

        val question = success(
            PairDrillQuestionGenerator.generate(
                questionId = "dedupe",
                prioritizedCandidates = candidates,
                requiredTargetGroupIds = setOf(2),
                random = Random(9),
            ),
        )
        val selectedIds = question.choices.map { it.sourceGroupId }.toSet()
        val targetIds = question.choices.filter { it.pairId != null }.map { it.sourceGroupId }.toSet()

        assertTrue(2 in targetIds)
        assertFalse(1 in selectedIds)
    }

    @Test
    fun `selected source groups never overlap after normalization`() {
        val candidates = listOf(
            group(1, "alpha", "beta", "shared"),
            group(2, " SHARED ", "gamma"),
            group(3, "delta", "epsilon"),
            group(4, "zeta", "eta"),
            group(5, "theta", "iota"),
            group(6, "kappa", "lambda"),
        )

        val question = success(
            PairDrillQuestionGenerator.generate(
                questionId = "overlap",
                prioritizedCandidates = candidates,
                requiredTargetGroupIds = setOf(1),
                random = Random(11),
            ),
        )
        val selectedIds = question.choices.map { it.sourceGroupId }.toSet()

        assertTrue(1 in selectedIds)
        assertFalse(2 in selectedIds)
    }

    @Test
    fun `required target is strict and preferred target is selected when feasible`() {
        val question = success(
            PairDrillQuestionGenerator.generate(
                questionId = "priority",
                prioritizedCandidates = candidates(),
                requiredTargetGroupIds = setOf(6),
                preferredTargetGroupIds = setOf(5),
                random = Random(13),
            ),
        )
        val targetIds = question.choices.filter { it.pairId != null }.map { it.sourceGroupId }.toSet()

        assertEquals(setOf(5, 6), targetIds)
    }

    @Test
    fun `preferred target falls back when no valid question can contain it`() {
        val candidates = listOf(
            group(1, "alpha", "gamma", "epsilon", "eta"),
            group(2, "alpha", "beta"),
            group(3, "gamma", "delta"),
            group(4, "epsilon", "zeta"),
            group(5, "eta", "theta"),
        )

        val question = success(
            PairDrillQuestionGenerator.generate(
                questionId = "preferred-fallback",
                prioritizedCandidates = candidates,
                preferredTargetGroupIds = setOf(1),
                random = Random(17),
            ),
        )

        assertFalse(question.choices.any { it.sourceGroupId == 1 })
        assertEquals(setOf(2, 3, 4, 5), question.choices.map { it.sourceGroupId }.toSet())
    }

    @Test
    fun `missing required target returns typed failure`() {
        val result = PairDrillQuestionGenerator.generate(
            questionId = "missing",
            prioritizedCandidates = candidates(),
            requiredTargetGroupIds = setOf(999),
            random = Random(1),
        )

        assertEquals(
            PairDrillGenerationFailureReason.REQUIRED_TARGET_UNAVAILABLE,
            (result as PairDrillGenerationResult.Failure).reason,
        )
    }

    @Test
    fun `more than two required targets returns typed failure`() {
        val result = PairDrillQuestionGenerator.generate(
            questionId = "too-many",
            prioritizedCandidates = candidates(),
            requiredTargetGroupIds = setOf(1, 2, 3),
            random = Random(1),
        )

        assertEquals(
            PairDrillGenerationFailureReason.TOO_MANY_REQUIRED_TARGETS,
            (result as PairDrillGenerationResult.Failure).reason,
        )
    }

    @Test
    fun `insufficient compatible candidates returns typed failure`() {
        val result = PairDrillQuestionGenerator.generate(
            questionId = "insufficient",
            prioritizedCandidates = listOf(
                group(1, "alpha", "beta"),
                group(2, "beta", "gamma"),
                group(3, "gamma", "alpha"),
                group(4, " ", "only-one"),
            ),
            random = Random(1),
        )

        assertEquals(
            PairDrillGenerationFailureReason.INSUFFICIENT_COMPATIBLE_GROUPS,
            (result as PairDrillGenerationResult.Failure).reason,
        )
    }

    @Test
    fun `generator invariants hold over many seeds`() {
        val candidates = (1..12).map { id ->
            group(id, "word-${id}-a", "word-${id}-b", "word-${id}-c")
        }

        repeat(250) { seed ->
            val question = success(
                PairDrillQuestionGenerator.generate(
                    questionId = "property-$seed",
                    prioritizedCandidates = candidates,
                    random = Random(seed),
                ),
            )
            assertEquals(6, question.choices.map { it.normalizedText }.toSet().size)
            assertEquals(4, question.choices.map { it.sourceGroupId }.toSet().size)
            assertEquals(2, question.correctPairs.size)
            assertTrue(question.correctPairs.all { it.choiceIds.size == 2 })
            assertEquals(4, question.correctPairs.flatMap { it.choiceIds }.toSet().size)
        }
    }

    private fun candidates(): List<Group> = listOf(
        group(1, "alpha", "beta", "aleph"),
        group(2, "gamma", "delta", "gimel"),
        group(3, "epsilon", "zeta", "eta"),
        group(4, "theta", "iota", "kappa"),
        group(5, "lambda", "mu", "nu"),
        group(6, "xi", "omicron", "pi"),
    )

    private fun group(id: Int, vararg words: String): Group = Group(
        uuid = id,
        words = words.toMutableSet(),
        chineseMeaning = "meaning-$id",
    )

    private fun success(result: PairDrillGenerationResult): PairDrillQuestion =
        (result as PairDrillGenerationResult.Success).question
}
