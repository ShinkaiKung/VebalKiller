package com.github.ShinkaiKung.verbalkiller.domain

import java.util.Collections

data class ScheduledReinforcement<T>(
    val item: T,
    val scheduledAtCompletedQuestionCount: Int,
    val dueAtCompletedQuestionCount: Int,
) {
    init {
        require(scheduledAtCompletedQuestionCount >= 0) {
            "Scheduled question count must not be negative"
        }
        require(dueAtCompletedQuestionCount >= scheduledAtCompletedQuestionCount) {
            "Due question count must follow scheduled question count"
        }
    }

    val delayInQuestions: Int
        get() = dueAtCompletedQuestionCount - scheduledAtCompletedQuestionCount
}

data class ReinforcementPoll<T>(
    val item: T?,
    val queue: ReinforcementQueue<T>,
)

/** Immutable queue keyed by completed question count, not wall-clock time. */
class ReinforcementQueue<T> private constructor(
    scheduled: Collection<ScheduledReinforcement<T>>,
) {
    val scheduled: List<ScheduledReinforcement<T>> =
        Collections.unmodifiableList(scheduled.toList())

    val size: Int
        get() = scheduled.size

    val items: Set<T>
        get() = scheduled.mapTo(linkedSetOf()) { it.item }

    fun schedule(
        item: T,
        completedQuestionCount: Int,
        delayInQuestions: Int,
    ): ReinforcementQueue<T> {
        require(completedQuestionCount >= 0) { "Completed question count must not be negative" }
        require(delayInQuestions > 0) { "Reinforcement delay must be positive" }
        val replacement = ScheduledReinforcement(
            item = item,
            scheduledAtCompletedQuestionCount = completedQuestionCount,
            dueAtCompletedQuestionCount = Math.addExact(completedQuestionCount, delayInQuestions),
        )
        return ReinforcementQueue(scheduled.filterNot { it.item == item } + replacement)
    }

    fun remove(item: T): ReinforcementQueue<T> =
        ReinforcementQueue(scheduled.filterNot { it.item == item })

    fun removeAll(items: Set<T>): ReinforcementQueue<T> =
        if (items.isEmpty()) this else ReinforcementQueue(scheduled.filterNot { it.item in items })

    fun dueItems(
        completedQuestionCount: Int,
        accepts: (T) -> Boolean = { true },
    ): List<T> {
        require(completedQuestionCount >= 0) { "Completed question count must not be negative" }
        return scheduled
            .filter { it.dueAtCompletedQuestionCount <= completedQuestionCount && accepts(it.item) }
            .sortedBy { it.dueAtCompletedQuestionCount }
            .map { it.item }
    }

    fun pollDue(
        completedQuestionCount: Int,
        accepts: (T) -> Boolean = { true },
    ): ReinforcementPoll<T> {
        val dueItem = dueItems(completedQuestionCount, accepts).firstOrNull()
            ?: return ReinforcementPoll(item = null, queue = this)
        return ReinforcementPoll(item = dueItem, queue = remove(dueItem))
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ReinforcementQueue<*> && scheduled == other.scheduled

    override fun hashCode(): Int = scheduled.hashCode()

    override fun toString(): String = "ReinforcementQueue(scheduled=$scheduled)"

    companion object {
        fun <T> empty(): ReinforcementQueue<T> = ReinforcementQueue(emptyList())
    }
}
