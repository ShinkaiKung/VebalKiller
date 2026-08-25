package com.github.ShinkaiKung.verbalkiller.domain

import com.github.ShinkaiKung.verbalkiller.logic.Group
import kotlin.random.Random

enum class PairDrillGenerationFailureReason {
    INVALID_QUESTION_ID,
    TOO_MANY_REQUIRED_TARGETS,
    REQUIRED_TARGET_UNAVAILABLE,
    INSUFFICIENT_COMPATIBLE_GROUPS,
}

sealed interface PairDrillGenerationResult {
    data class Success(val question: PairDrillQuestion) : PairDrillGenerationResult

    data class Failure(
        val reason: PairDrillGenerationFailureReason,
        val details: String,
    ) : PairDrillGenerationResult
}

/**
 * Safe generator for the two-pair/six-choice drill.
 *
 * [prioritizedCandidates] is consumed in priority order. Required ids are a strict target-pair
 * constraint. Preferred ids are included as target pairs whenever any valid four-group question
 * containing one can be built. Pass a seeded [random] for reproducible questions and tests.
 */
object PairDrillQuestionGenerator {
    fun generate(
        questionId: String,
        prioritizedCandidates: List<Group>,
        requiredTargetGroupIds: Set<Int> = emptySet(),
        preferredTargetGroupIds: Set<Int> = emptySet(),
        random: Random = Random.Default,
    ): PairDrillGenerationResult {
        if (questionId.isBlank()) {
            return PairDrillGenerationResult.Failure(
                PairDrillGenerationFailureReason.INVALID_QUESTION_ID,
                "Question id must not be blank",
            )
        }
        if (requiredTargetGroupIds.size > TARGET_GROUP_COUNT) {
            return PairDrillGenerationResult.Failure(
                PairDrillGenerationFailureReason.TOO_MANY_REQUIRED_TARGETS,
                "At most two target groups can be required",
            )
        }

        val candidates = sanitizeAndDeduplicate(
            groups = prioritizedCandidates,
            requiredTargetGroupIds = requiredTargetGroupIds,
            preferredTargetGroupIds = preferredTargetGroupIds,
        )
        val availableIds = candidates.map { it.id }.toSet()
        val unavailableRequiredIds = requiredTargetGroupIds - availableIds
        if (unavailableRequiredIds.isNotEmpty()) {
            return PairDrillGenerationResult.Failure(
                PairDrillGenerationFailureReason.REQUIRED_TARGET_UNAVAILABLE,
                "Required target groups are invalid, duplicated, or unavailable: " +
                    unavailableRequiredIds.sorted().joinToString(),
            )
        }

        val availablePreferredIds = preferredTargetGroupIds intersect availableIds
        val selection = if (availablePreferredIds.isNotEmpty()) {
            findSelection(
                candidates = candidates,
                requiredTargetGroupIds = requiredTargetGroupIds,
                preferredTargetGroupIds = availablePreferredIds,
                mustContainPreferred = true,
            ) ?: findSelection(
                candidates = candidates,
                requiredTargetGroupIds = requiredTargetGroupIds,
                preferredTargetGroupIds = availablePreferredIds,
                mustContainPreferred = false,
            )
        } else {
            findSelection(
                candidates = candidates,
                requiredTargetGroupIds = requiredTargetGroupIds,
                preferredTargetGroupIds = emptySet(),
                mustContainPreferred = false,
            )
        }

        if (selection == null) {
            return PairDrillGenerationResult.Failure(
                PairDrillGenerationFailureReason.INSUFFICIENT_COMPATIBLE_GROUPS,
                "Need two target groups and two distractor groups with mutually disjoint normalized words",
            )
        }

        return PairDrillGenerationResult.Success(
            buildQuestion(
                questionId = questionId.trim(),
                selection = selection,
                random = random,
            ),
        )
    }

    private fun sanitizeAndDeduplicate(
        groups: List<Group>,
        requiredTargetGroupIds: Set<Int>,
        preferredTargetGroupIds: Set<Int>,
    ): List<CandidateGroup> {
        val seenIds = mutableSetOf<Int>()
        val sanitized = groups.mapIndexedNotNull { priorityIndex, group ->
            if (!seenIds.add(group.uuid)) return@mapIndexedNotNull null

            val wordsByNormalizedText = linkedMapOf<String, CandidateWord>()
            group.words.toList().forEach { rawWord ->
                val display = PairDrillTokenNormalizer.display(rawWord)
                val normalized = PairDrillTokenNormalizer.normalize(rawWord)
                if (normalized.isNotEmpty()) {
                    wordsByNormalizedText.putIfAbsent(
                        normalized,
                        CandidateWord(display = display, normalized = normalized),
                    )
                }
            }
            if (wordsByNormalizedText.size < WORDS_PER_TARGET) return@mapIndexedNotNull null

            CandidateGroup(
                id = group.uuid,
                words = wordsByNormalizedText.values.sortedBy { it.normalized },
                normalizedWords = wordsByNormalizedText.keys.toSet(),
                explanation = group.chineseMeaning.trim().takeIf(String::isNotEmpty),
                priorityIndex = priorityIndex,
            )
        }

        // Equivalent normalized groups represent the same candidate. Prefer a required/preferred id
        // over the earlier duplicate so caller constraints remain satisfiable whenever possible.
        return sanitized
            .groupBy { it.normalizedWords }
            .values
            .map { duplicates ->
                duplicates.minWith(
                    compareBy<CandidateGroup> {
                        when (it.id) {
                            in requiredTargetGroupIds -> 0
                            in preferredTargetGroupIds -> 1
                            else -> 2
                        }
                    }.thenBy { it.priorityIndex },
                )
            }
            .sortedBy { it.priorityIndex }
    }

    private fun findSelection(
        candidates: List<CandidateGroup>,
        requiredTargetGroupIds: Set<Int>,
        preferredTargetGroupIds: Set<Int>,
        mustContainPreferred: Boolean,
    ): GroupSelection? {
        for ((firstTarget, secondTarget) in targetPairs(candidates, requiredTargetGroupIds)) {
            if (!firstTarget.isDisjointFrom(secondTarget)) continue
            if (mustContainPreferred &&
                firstTarget.id !in preferredTargetGroupIds &&
                secondTarget.id !in preferredTargetGroupIds
            ) {
                continue
            }
            val distractors = findDistractors(candidates, firstTarget, secondTarget) ?: continue
            return GroupSelection(
                targetGroups = listOf(firstTarget, secondTarget),
                distractorGroups = distractors,
            )
        }
        return null
    }

    private fun targetPairs(
        candidates: List<CandidateGroup>,
        requiredTargetGroupIds: Set<Int>,
    ): Sequence<Pair<CandidateGroup, CandidateGroup>> = sequence {
        val required = candidates.filter { it.id in requiredTargetGroupIds }
        when (required.size) {
            2 -> yield(required[0] to required[1])
            1 -> candidates.forEach { candidate ->
                if (candidate.id != required.single().id) yield(required.single() to candidate)
            }
            else -> candidates.indices.forEach { firstIndex ->
                for (secondIndex in firstIndex + 1 until candidates.size) {
                    yield(candidates[firstIndex] to candidates[secondIndex])
                }
            }
        }
    }

    private fun findDistractors(
        candidates: List<CandidateGroup>,
        firstTarget: CandidateGroup,
        secondTarget: CandidateGroup,
    ): List<CandidateGroup>? {
        val targetIds = setOf(firstTarget.id, secondTarget.id)
        val targetWords = firstTarget.normalizedWords + secondTarget.normalizedWords
        val eligible = candidates.filter { candidate ->
            candidate.id !in targetIds && candidate.normalizedWords.intersect(targetWords).isEmpty()
        }
        eligible.indices.forEach { firstIndex ->
            for (secondIndex in firstIndex + 1 until eligible.size) {
                val first = eligible[firstIndex]
                val second = eligible[secondIndex]
                if (first.isDisjointFrom(second)) return listOf(first, second)
            }
        }
        return null
    }

    private fun buildQuestion(
        questionId: String,
        selection: GroupSelection,
        random: Random,
    ): PairDrillQuestion {
        var nextChoiceNumber = 1
        val choices = mutableListOf<PairDrillChoice>()
        val pairs = mutableListOf<PairDrillPair>()

        selection.targetGroups.forEach { group ->
            val pairId = PairId("group:${group.id}")
            val pairChoiceIds = group.words.shuffled(random).take(WORDS_PER_TARGET).map { word ->
                val choiceId = ChoiceId("$questionId:choice:${nextChoiceNumber++}")
                choices += PairDrillChoice.create(
                    id = choiceId,
                    text = word.display,
                    sourceGroupId = group.id,
                    pairId = pairId,
                    explanation = group.explanation,
                )
                choiceId
            }
            pairs += PairDrillPair.create(
                id = pairId,
                choiceIds = pairChoiceIds,
                explanation = group.explanation,
            )
        }

        selection.distractorGroups.forEach { group ->
            val word = group.words.random(random)
            choices += PairDrillChoice.create(
                id = ChoiceId("$questionId:choice:${nextChoiceNumber++}"),
                text = word.display,
                sourceGroupId = group.id,
                pairId = null,
                explanation = group.explanation,
            )
        }

        return PairDrillQuestion.create(
            id = questionId,
            choices = choices.shuffled(random),
            correctPairs = pairs,
        )
    }

    private data class CandidateWord(
        val display: String,
        val normalized: String,
    )

    private data class CandidateGroup(
        val id: Int,
        val words: List<CandidateWord>,
        val normalizedWords: Set<String>,
        val explanation: String?,
        val priorityIndex: Int,
    ) {
        fun isDisjointFrom(other: CandidateGroup): Boolean =
            normalizedWords.intersect(other.normalizedWords).isEmpty()
    }

    private data class GroupSelection(
        val targetGroups: List<CandidateGroup>,
        val distractorGroups: List<CandidateGroup>,
    )

    private const val TARGET_GROUP_COUNT = 2
    private const val WORDS_PER_TARGET = 2
}
