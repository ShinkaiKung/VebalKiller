package com.github.ShinkaiKung.verbalkiller.domain

import java.text.Normalizer
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

/** Stable identity of one answer pair. A and B are presentation-only assignments. */
@JvmInline
value class PairId(val value: String) {
    init {
        require(value.isNotBlank()) { "PairId must not be blank" }
    }
}

@JvmInline
value class ChoiceId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChoiceId must not be blank" }
    }
}

enum class Assignment {
    A,
    B,
}

/**
 * Normalization used by the generator's uniqueness and overlap invariants.
 * Display text keeps case, while comparison text is case-insensitive.
 */
object PairDrillTokenNormalizer {
    private val repeatedWhitespace = Regex("\\s+")

    fun display(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .trim()
            .replace(repeatedWhitespace, " ")

    fun normalize(raw: String): String = display(raw).lowercase(Locale.ROOT)
}

/** A displayed option. A null [pairId] means that the option is a distractor. */
data class PairDrillChoice private constructor(
    val id: ChoiceId,
    val text: String,
    val normalizedText: String,
    val sourceGroupId: Int,
    val pairId: PairId?,
    val explanation: String?,
) {
    companion object {
        fun create(
            id: ChoiceId,
            text: String,
            sourceGroupId: Int,
            pairId: PairId?,
            explanation: String? = null,
        ): PairDrillChoice {
            val displayText = PairDrillTokenNormalizer.display(text)
            val normalizedText = PairDrillTokenNormalizer.normalize(text)
            require(normalizedText.isNotEmpty()) { "Choice text must not be blank" }
            return PairDrillChoice(
                id = id,
                text = displayText,
                normalizedText = normalizedText,
                sourceGroupId = sourceGroupId,
                pairId = pairId,
                explanation = explanation?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }
}

/** Canonical answer pair and the explanation shown after submission. */
class PairDrillPair private constructor(
    val id: PairId,
    val choiceIds: Set<ChoiceId>,
    val explanation: String?,
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is PairDrillPair &&
            id == other.id && choiceIds == other.choiceIds && explanation == other.explanation

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + choiceIds.hashCode()) +
        (explanation?.hashCode() ?: 0)

    override fun toString(): String =
        "PairDrillPair(id=$id, choiceIds=$choiceIds, explanation=$explanation)"

    companion object {
        fun create(
            id: PairId,
            choiceIds: Collection<ChoiceId>,
            explanation: String? = null,
        ): PairDrillPair {
            val immutableChoiceIds = immutableSetCopy(choiceIds)
            require(immutableChoiceIds.size == 2) { "A correct pair must contain exactly two choices" }
            return PairDrillPair(
                id = id,
                choiceIds = immutableChoiceIds,
                explanation = explanation?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }
}

/**
 * Immutable six-choice Pair Drill question.
 *
 * A valid question has two disjoint target pairs and two singleton distractor groups.
 */
class PairDrillQuestion private constructor(
    val id: String,
    val choices: List<PairDrillChoice>,
    val correctPairs: List<PairDrillPair>,
) {
    val choicesById: Map<ChoiceId, PairDrillChoice> = immutableMapCopy(choices.associateBy { it.id })

    init {
        require(id.isNotBlank()) { "Question id must not be blank" }
        require(choices.size == CHOICE_COUNT) { "A Pair Drill question must have exactly six choices" }
        require(choicesById.size == choices.size) { "Choice ids must be unique" }
        require(choices.map { it.normalizedText }.toSet().size == choices.size) {
            "Choice text must be unique after normalization"
        }
        require(correctPairs.size == CORRECT_PAIR_COUNT) {
            "A Pair Drill question must have exactly two correct pairs"
        }
        require(correctPairs.map { it.id }.toSet().size == correctPairs.size) {
            "Correct pair ids must be unique"
        }

        val targetChoiceIds = correctPairs.flatMap { it.choiceIds }
        require(targetChoiceIds.toSet().size == CORRECT_CHOICE_COUNT) {
            "Correct pairs must be disjoint"
        }
        require(targetChoiceIds.all(choicesById::containsKey)) {
            "Every correct pair choice must exist in the question"
        }

        val pairIds = correctPairs.map { it.id }.toSet()
        require(choices.filter { it.pairId != null }.mapNotNull { it.pairId }.toSet() == pairIds) {
            "Choice pair ids must match the canonical correct pairs"
        }
        correctPairs.forEach { pair ->
            val choicesForPair = choices.filter { it.pairId == pair.id }
            require(choicesForPair.map { it.id }.toSet() == pair.choiceIds) {
                "A choice's pairId must agree with its canonical pair"
            }
            require(choicesForPair.map { it.sourceGroupId }.toSet().size == 1) {
                "Both choices in a target pair must come from one source group"
            }
        }

        val choicesBySourceGroup = choices.groupBy { it.sourceGroupId }
        require(choicesBySourceGroup.size == SOURCE_GROUP_COUNT) {
            "A Pair Drill question must use four distinct source groups"
        }
        require(choicesBySourceGroup.values.count { it.size == 2 } == CORRECT_PAIR_COUNT) {
            "Exactly two source groups must contribute target pairs"
        }
        require(choicesBySourceGroup.values.count { it.size == 1 } == DISTRACTOR_COUNT) {
            "Exactly two source groups must contribute singleton distractors"
        }
        choicesBySourceGroup.values.filter { it.size == 1 }.forEach { singleton ->
            require(singleton.single().pairId == null) { "A singleton source group must be a distractor" }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is PairDrillQuestion &&
            id == other.id && choices == other.choices && correctPairs == other.correctPairs

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + choices.hashCode()) +
        correctPairs.hashCode()

    override fun toString(): String =
        "PairDrillQuestion(id=$id, choices=$choices, correctPairs=$correctPairs)"

    companion object {
        const val CHOICE_COUNT = 6
        const val CORRECT_PAIR_COUNT = 2
        const val CORRECT_CHOICE_COUNT = 4
        const val SOURCE_GROUP_COUNT = 4
        const val DISTRACTOR_COUNT = 2

        fun create(
            id: String,
            choices: Collection<PairDrillChoice>,
            correctPairs: Collection<PairDrillPair>,
        ): PairDrillQuestion = PairDrillQuestion(
            id = id.trim(),
            choices = immutableListCopy(choices),
            correctPairs = immutableListCopy(correctPairs),
        )
    }
}

class PairDrillResponse private constructor(
    val assignments: Map<ChoiceId, Assignment>,
) {
    fun choiceIdsFor(assignment: Assignment): Set<ChoiceId> =
        assignments.filterValues { it == assignment }.keys.toSet()

    companion object {
        fun create(assignments: Map<ChoiceId, Assignment>): PairDrillResponse =
            PairDrillResponse(immutableMapCopy(assignments))

        fun empty(): PairDrillResponse = PairDrillResponse(emptyMap())
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is PairDrillResponse && assignments == other.assignments

    override fun hashCode(): Int = assignments.hashCode()

    override fun toString(): String = "PairDrillResponse(assignments=$assignments)"
}

private fun <T> immutableListCopy(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <T> immutableSetCopy(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))

private fun <K, V> immutableMapCopy(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))
