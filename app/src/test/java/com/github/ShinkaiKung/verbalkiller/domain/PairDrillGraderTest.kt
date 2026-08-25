package com.github.ShinkaiKung.verbalkiller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairDrillGraderTest {
    @Test
    fun `accepts exact pairs when A and B are swapped`() {
        val question = question()
        val grade = PairDrillGrader.grade(
            question,
            PairDrillResponse.create(
                mapOf(
                    choiceId(3) to Assignment.A,
                    choiceId(4) to Assignment.A,
                    choiceId(1) to Assignment.B,
                    choiceId(2) to Assignment.B,
                ),
            ),
        )

        assertTrue(grade.isCorrect)
        assertTrue(grade.issues.isEmpty())
        assertEquals(
            setOf(AssignmentFeedbackStatus.CORRECT_PAIR),
            grade.assignmentFeedback.map { it.status }.toSet(),
        )
        assertEquals(
            4,
            grade.choiceFeedback.count { it.status == ChoiceFeedbackStatus.CORRECTLY_GROUPED },
        )
        assertEquals(
            2,
            grade.choiceFeedback.count { it.status == ChoiceFeedbackStatus.CORRECTLY_REJECTED_DISTRACTOR },
        )
    }

    @Test
    fun `rejects singleton assignments instead of recording them as correct`() {
        val grade = PairDrillGrader.grade(
            question(),
            PairDrillResponse.create(
                mapOf(
                    choiceId(1) to Assignment.A,
                    choiceId(3) to Assignment.B,
                ),
            ),
        )

        assertFalse(grade.isCorrect)
        assertTrue(grade.issues.contains(PairDrillGradeIssue.ASSIGNMENT_A_MUST_HAVE_TWO_CHOICES))
        assertTrue(grade.issues.contains(PairDrillGradeIssue.ASSIGNMENT_B_MUST_HAVE_TWO_CHOICES))
        assertTrue(grade.issues.contains(PairDrillGradeIssue.PAIRS_DO_NOT_EXACTLY_MATCH))
        assertEquals(
            setOf(AssignmentFeedbackStatus.INCOMPLETE),
            grade.assignmentFeedback.map { it.status }.toSet(),
        )
        assertEquals(
            4,
            grade.choiceFeedback.count {
                it.status == ChoiceFeedbackStatus.MISSED_TARGET ||
                    it.status == ChoiceFeedbackStatus.INCORRECTLY_GROUPED_TARGET
            },
        )
    }

    @Test
    fun `rejects two cross-paired groups even with valid cardinality`() {
        val grade = PairDrillGrader.grade(
            question(),
            PairDrillResponse.create(
                mapOf(
                    choiceId(1) to Assignment.A,
                    choiceId(3) to Assignment.A,
                    choiceId(2) to Assignment.B,
                    choiceId(4) to Assignment.B,
                ),
            ),
        )

        assertFalse(grade.isCorrect)
        assertEquals(setOf(PairDrillGradeIssue.PAIRS_DO_NOT_EXACTLY_MATCH), grade.issues)
        assertEquals(
            setOf(AssignmentFeedbackStatus.WRONG_PAIR),
            grade.assignmentFeedback.map { it.status }.toSet(),
        )
    }

    @Test
    fun `reports an assignment containing more than two choices`() {
        val grade = PairDrillGrader.grade(
            question(),
            PairDrillResponse.create(
                mapOf(
                    choiceId(1) to Assignment.A,
                    choiceId(2) to Assignment.A,
                    choiceId(5) to Assignment.A,
                    choiceId(3) to Assignment.B,
                    choiceId(4) to Assignment.B,
                ),
            ),
        )

        assertFalse(grade.isCorrect)
        assertEquals(
            AssignmentFeedbackStatus.TOO_MANY_CHOICES,
            grade.assignmentFeedback.single { it.assignment == Assignment.A }.status,
        )
        assertTrue(grade.issues.contains(PairDrillGradeIssue.ASSIGNMENT_A_MUST_HAVE_TWO_CHOICES))
    }

    @Test
    fun `reports selected distractor and missed target separately`() {
        val grade = PairDrillGrader.grade(
            question(),
            PairDrillResponse.create(
                mapOf(
                    choiceId(1) to Assignment.A,
                    choiceId(5) to Assignment.A,
                    choiceId(3) to Assignment.B,
                    choiceId(4) to Assignment.B,
                ),
            ),
        )

        assertFalse(grade.isCorrect)
        assertEquals(
            ChoiceFeedbackStatus.INCORRECTLY_SELECTED_DISTRACTOR,
            grade.choiceFeedback.single { it.choice.id == choiceId(5) }.status,
        )
        assertEquals(
            ChoiceFeedbackStatus.MISSED_TARGET,
            grade.choiceFeedback.single { it.choice.id == choiceId(2) }.status,
        )
        assertEquals(
            "第二组解释",
            grade.assignmentFeedback.single { it.assignment == Assignment.B }.explanation,
        )
    }

    @Test
    fun `unknown choice makes an otherwise plausible response invalid`() {
        val unknown = ChoiceId("unknown")
        val grade = PairDrillGrader.grade(
            question(),
            PairDrillResponse.create(
                mapOf(
                    choiceId(1) to Assignment.A,
                    unknown to Assignment.A,
                    choiceId(3) to Assignment.B,
                    choiceId(4) to Assignment.B,
                ),
            ),
        )

        assertFalse(grade.isCorrect)
        assertEquals(setOf(unknown), grade.unknownChoiceIds)
        assertTrue(grade.issues.contains(PairDrillGradeIssue.UNKNOWN_CHOICE))
    }

    @Test
    fun `question rejects normalized duplicate choice text`() {
        val choices = validChoices().toMutableList()
        choices[5] = PairDrillChoice.create(
            id = choiceId(6),
            text = "  ALPHA  ",
            sourceGroupId = 4,
            pairId = null,
        )

        assertThrows(IllegalArgumentException::class.java) {
            PairDrillQuestion.create("q", choices, validPairs())
        }
    }

    @Test
    fun `factories snapshot caller collections`() {
        val assignments = mutableMapOf(choiceId(1) to Assignment.A)
        val response = PairDrillResponse.create(assignments)
        assignments.clear()

        assertEquals(mapOf(choiceId(1) to Assignment.A), response.assignments)
    }

    private fun question(): PairDrillQuestion =
        PairDrillQuestion.create("q", validChoices(), validPairs())

    private fun validChoices(): List<PairDrillChoice> = listOf(
        choice(1, "alpha", 1, FIRST_PAIR, "alpha explanation"),
        choice(2, "beta", 1, FIRST_PAIR, "beta explanation"),
        choice(3, "gamma", 2, SECOND_PAIR, "gamma explanation"),
        choice(4, "delta", 2, SECOND_PAIR, "delta explanation"),
        choice(5, "epsilon", 3, null, "epsilon explanation"),
        choice(6, "zeta", 4, null, "zeta explanation"),
    )

    private fun validPairs(): List<PairDrillPair> = listOf(
        PairDrillPair.create(FIRST_PAIR, listOf(choiceId(1), choiceId(2)), "第一组解释"),
        PairDrillPair.create(SECOND_PAIR, listOf(choiceId(3), choiceId(4)), "第二组解释"),
    )

    private fun choice(
        number: Int,
        text: String,
        sourceGroupId: Int,
        pairId: PairId?,
        explanation: String,
    ): PairDrillChoice = PairDrillChoice.create(
        id = choiceId(number),
        text = text,
        sourceGroupId = sourceGroupId,
        pairId = pairId,
        explanation = explanation,
    )

    private fun choiceId(number: Int) = ChoiceId("c$number")

    private companion object {
        val FIRST_PAIR = PairId("p1")
        val SECOND_PAIR = PairId("p2")
    }
}
