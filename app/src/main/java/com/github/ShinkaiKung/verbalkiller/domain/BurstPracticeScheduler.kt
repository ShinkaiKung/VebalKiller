package com.github.ShinkaiKung.verbalkiller.domain

/**
 * Question-count based reinforcement for a short, intensive practice round.
 *
 * A wrong answer resets progress and returns after three intervening questions. Correct answers
 * move through six- and twelve-question gaps; three consecutive correct answers pass the group.
 */
object BurstPracticeScheduler {
    const val REQUIRED_CONSECUTIVE_CORRECT = 3

    fun review(previousProgress: Int?, wasCorrect: Boolean): BurstPracticeTransition {
        val normalizedPrevious = previousProgress
            ?.coerceIn(0, REQUIRED_CONSECUTIVE_CORRECT)
            ?: 0
        val nextProgress = if (wasCorrect) {
            (normalizedPrevious + 1).coerceAtMost(REQUIRED_CONSECUTIVE_CORRECT)
        } else {
            0
        }
        return BurstPracticeTransition(
            progress = nextProgress,
            delayInQuestions = delayForProgress(nextProgress),
        )
    }

    fun isPassed(progress: Int): Boolean = progress >= REQUIRED_CONSECUTIVE_CORRECT

    fun delayForProgress(progress: Int): Int? =
        DELAY_BY_PROGRESS[progress.coerceIn(0, REQUIRED_CONSECUTIVE_CORRECT)]

    private val DELAY_BY_PROGRESS = mapOf(
        0 to 3,
        1 to 6,
        2 to 12,
        REQUIRED_CONSECUTIVE_CORRECT to null,
    )
}

data class BurstPracticeTransition(
    val progress: Int,
    val delayInQuestions: Int?,
) {
    val isPassed: Boolean
        get() = delayInQuestions == null
}
