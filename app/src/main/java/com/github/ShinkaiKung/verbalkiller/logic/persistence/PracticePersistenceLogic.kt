package com.github.ShinkaiKung.verbalkiller.logic.persistence

internal fun calculateReviewUpdate(
    groupId: Int,
    previous: ReviewState?,
    correct: Boolean,
    timestamp: Long,
    scheduleReview: (ReviewState?, Boolean, Long) -> ReviewSchedule,
): ReviewState {
    val schedule = scheduleReview(previous, correct, timestamp)
    require(schedule.dueAt >= timestamp) { "scheduled review cannot be before the attempt" }
    return ReviewState(
        groupId = groupId,
        box = schedule.box,
        dueAt = schedule.dueAt,
        lastReviewedAt = timestamp,
        lastCorrect = correct,
        consecutiveCorrect = if (correct) (previous?.consecutiveCorrect ?: 0) + 1 else 0,
        lapseCount = (previous?.lapseCount ?: 0) + if (correct) 0 else 1,
        updatedAt = timestamp,
    )
}

internal fun updatedErrorStates(
    current: Map<String, Int>,
    words: Iterable<String>,
    correct: Boolean,
): MutableMap<String, Int> = current.toMutableMap().apply {
    words.toSet().forEach { word ->
        this[word] = if (correct) ((this[word] ?: 0) - 1).coerceAtLeast(0) else 3
    }
}
