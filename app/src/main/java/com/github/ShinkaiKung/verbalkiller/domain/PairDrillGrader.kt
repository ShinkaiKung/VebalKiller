package com.github.ShinkaiKung.verbalkiller.domain

enum class AssignmentFeedbackStatus {
    CORRECT_PAIR,
    INCOMPLETE,
    TOO_MANY_CHOICES,
    WRONG_PAIR,
}

data class PairDrillAssignmentFeedback(
    val assignment: Assignment,
    val selectedChoiceIds: Set<ChoiceId>,
    val selectedChoices: List<PairDrillChoice>,
    val status: AssignmentFeedbackStatus,
    val matchedPairId: PairId?,
    val explanation: String?,
)

enum class ChoiceFeedbackStatus {
    CORRECTLY_GROUPED,
    MISSED_TARGET,
    INCORRECTLY_GROUPED_TARGET,
    INCORRECTLY_SELECTED_DISTRACTOR,
    CORRECTLY_REJECTED_DISTRACTOR,
}

data class PairDrillChoiceFeedback(
    val choice: PairDrillChoice,
    val assignment: Assignment?,
    val status: ChoiceFeedbackStatus,
    val expectedPairId: PairId?,
    val explanation: String?,
)

enum class PairDrillGradeIssue {
    UNKNOWN_CHOICE,
    ASSIGNMENT_A_MUST_HAVE_TWO_CHOICES,
    ASSIGNMENT_B_MUST_HAVE_TWO_CHOICES,
    PAIRS_DO_NOT_EXACTLY_MATCH,
}

data class PairDrillGrade(
    val isCorrect: Boolean,
    val issues: Set<PairDrillGradeIssue>,
    val unknownChoiceIds: Set<ChoiceId>,
    val assignmentFeedback: List<PairDrillAssignmentFeedback>,
    val choiceFeedback: List<PairDrillChoiceFeedback>,
    val correctPairs: List<PairDrillPair>,
)

/** Strict, order-independent grading. Assignment A and B may be swapped. */
object PairDrillGrader {
    fun grade(
        question: PairDrillQuestion,
        response: PairDrillResponse,
    ): PairDrillGrade {
        val knownChoiceIds = question.choicesById.keys
        val unknownChoiceIds = response.assignments.keys - knownChoiceIds
        val selectedByAssignment = Assignment.entries.associateWith(response::choiceIdsFor)
        val canonicalPairSets = question.correctPairs.map { it.choiceIds }.toSet()
        val submittedPairSets = selectedByAssignment.values.toSet()

        val issues = buildSet {
            if (unknownChoiceIds.isNotEmpty()) add(PairDrillGradeIssue.UNKNOWN_CHOICE)
            if (selectedByAssignment.getValue(Assignment.A).size != PAIR_SIZE) {
                add(PairDrillGradeIssue.ASSIGNMENT_A_MUST_HAVE_TWO_CHOICES)
            }
            if (selectedByAssignment.getValue(Assignment.B).size != PAIR_SIZE) {
                add(PairDrillGradeIssue.ASSIGNMENT_B_MUST_HAVE_TWO_CHOICES)
            }
            if (submittedPairSets != canonicalPairSets) {
                add(PairDrillGradeIssue.PAIRS_DO_NOT_EXACTLY_MATCH)
            }
        }

        val assignmentFeedback = Assignment.entries.map { assignment ->
            val selectedIds = selectedByAssignment.getValue(assignment)
            val matchedPair = question.correctPairs.singleOrNull { it.choiceIds == selectedIds }
            val status = when {
                selectedIds.size < PAIR_SIZE -> AssignmentFeedbackStatus.INCOMPLETE
                selectedIds.size > PAIR_SIZE -> AssignmentFeedbackStatus.TOO_MANY_CHOICES
                matchedPair != null -> AssignmentFeedbackStatus.CORRECT_PAIR
                else -> AssignmentFeedbackStatus.WRONG_PAIR
            }
            PairDrillAssignmentFeedback(
                assignment = assignment,
                selectedChoiceIds = selectedIds,
                selectedChoices = selectedIds.mapNotNull(question.choicesById::get),
                status = status,
                matchedPairId = matchedPair?.id,
                explanation = matchedPair?.explanation,
            )
        }

        val feedbackByAssignment = assignmentFeedback.associateBy { it.assignment }
        val choiceFeedback = question.choices.map { choice ->
            val assignment = response.assignments[choice.id]
            val assignedAsCorrectPair = assignment != null &&
                feedbackByAssignment.getValue(assignment).matchedPairId == choice.pairId
            val status = when {
                choice.pairId != null && assignedAsCorrectPair -> ChoiceFeedbackStatus.CORRECTLY_GROUPED
                choice.pairId != null && assignment == null -> ChoiceFeedbackStatus.MISSED_TARGET
                choice.pairId != null -> ChoiceFeedbackStatus.INCORRECTLY_GROUPED_TARGET
                assignment != null -> ChoiceFeedbackStatus.INCORRECTLY_SELECTED_DISTRACTOR
                else -> ChoiceFeedbackStatus.CORRECTLY_REJECTED_DISTRACTOR
            }
            PairDrillChoiceFeedback(
                choice = choice,
                assignment = assignment,
                status = status,
                expectedPairId = choice.pairId,
                explanation = choice.explanation,
            )
        }

        return PairDrillGrade(
            isCorrect = issues.isEmpty(),
            issues = issues.toSet(),
            unknownChoiceIds = unknownChoiceIds.toSet(),
            assignmentFeedback = assignmentFeedback,
            choiceFeedback = choiceFeedback,
            correctPairs = question.correctPairs,
        )
    }

    private const val PAIR_SIZE = 2
}
