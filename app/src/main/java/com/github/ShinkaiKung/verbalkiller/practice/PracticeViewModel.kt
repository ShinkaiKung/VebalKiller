package com.github.ShinkaiKung.verbalkiller.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.ShinkaiKung.verbalkiller.domain.Assignment
import com.github.ShinkaiKung.verbalkiller.domain.AssignmentFeedbackStatus
import com.github.ShinkaiKung.verbalkiller.domain.BurstPracticeScheduler
import com.github.ShinkaiKung.verbalkiller.domain.ChoiceId
import com.github.ShinkaiKung.verbalkiller.domain.PairId
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillGenerationResult
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillGrade
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillGrader
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillQuestion
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillQuestionGenerator
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillResponse
import com.github.ShinkaiKung.verbalkiller.domain.ReinforcementQueue
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ConfusionObservation
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ContentInitializationState
import com.github.ShinkaiKung.verbalkiller.logic.persistence.GroupPracticeOutcome
import com.github.ShinkaiKung.verbalkiller.logic.persistence.GroupRepository
import com.github.ShinkaiKung.verbalkiller.logic.persistence.QuestionPracticeOutcome
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ReviewSchedule
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ReviewState
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class PracticeMode(
    val label: String,
    val description: String,
    val sourceCode: String,
) {
    SMART("智能练习", "轮内循环：答错隔 3 题，答对后隔 6/12 题重现", "smart"),
    ERRORS("错词强化", "集中重复强化中与高频错词", "errors"),
    RANDOM("随机练习", "全库随机组合，同时更新强化进度", "random"),
}

internal data class PracticeCandidatePolicy<T>(
    val focus: List<T>,
    val prioritized: List<T>,
    val requiresFocus: Boolean,
)

internal fun <T> PracticeMode.candidatePolicy(
    active: List<T>,
    unseen: List<T>,
    errors: List<T>,
    all: List<T>,
): PracticeCandidatePolicy<T> = when (this) {
    PracticeMode.SMART -> PracticeCandidatePolicy(
        focus = active.ifEmpty { unseen },
        prioritized = active + unseen + all,
        requiresFocus = false,
    )
    PracticeMode.ERRORS -> PracticeCandidatePolicy(
        focus = errors,
        prioritized = errors + all,
        requiresFocus = true,
    )
    PracticeMode.RANDOM -> PracticeCandidatePolicy(
        focus = emptyList(),
        prioritized = all,
        requiresFocus = false,
    )
}

data class PracticeUiState(
    val isLoading: Boolean = true,
    val mode: PracticeMode = PracticeMode.SMART,
    val question: PairDrillQuestion? = null,
    val assignments: Map<ChoiceId, Assignment> = emptyMap(),
    val activeAssignment: Assignment = Assignment.A,
    val grade: PairDrillGrade? = null,
    val isSaving: Boolean = false,
    val isReinforcementQuestion: Boolean = false,
    val inProgressCount: Int = 0,
    val newCount: Int = 0,
    val errorCount: Int = 0,
    val passedCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val pendingReinforcementCount: Int = 0,
    val emptyMessage: String? = null,
    val errorMessage: String? = null,
    val saveErrorMessage: String? = null,
) {
    val selectedInA: Int
        get() = assignments.count { it.value == Assignment.A }

    val selectedInB: Int
        get() = assignments.count { it.value == Assignment.B }

    val canSubmit: Boolean
        get() = question != null && grade == null && !isSaving && selectedInA == 2 && selectedInB == 2
}

class PracticeViewModel(
    private val repository: GroupRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var latestGroups: List<Group> = emptyList()
    private var latestReviews: Map<Int, ReviewState> = emptyMap()
    private var reinforcementQueue = ReinforcementQueue.empty<Int>()
    private val activePoolIds = linkedSetOf<Int>()
    private var questionSequence = 0L
    private var questionStartedAt = clock()

    init {
        viewModelScope.launch {
            combine(
                repository.initializationState,
                repository.groups,
                repository.reviewStates,
            ) { initialization, groups, reviews -> Triple(initialization, groups, reviews) }
                .collect { (initialization, groups, reviews) ->
                    latestGroups = groups
                    latestReviews = reviews.associateBy { it.groupId }
                    replenishActivePool()
                    val newCount = groups.count { it.uuid !in latestReviews }
                    val errorCount = groups.count { group ->
                        group.errorStates.values.any { it > 0 } ||
                            latestReviews[group.uuid]?.lastCorrect == false
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = initialization is ContentInitializationState.NotStarted ||
                            initialization is ContentInitializationState.Loading,
                        inProgressCount = reviews.count { !BurstPracticeScheduler.isPassed(it.box) },
                        newCount = newCount,
                        errorCount = errorCount,
                        passedCount = reviews.count { BurstPracticeScheduler.isPassed(it.box) },
                        errorMessage = (initialization as? ContentInitializationState.Failed)
                            ?.error?.toDisplayMessage("词库初始化失败"),
                    )
                    if (initialization is ContentInitializationState.Ready &&
                        groups.isNotEmpty() && _uiState.value.question == null
                    ) {
                        loadNextQuestion()
                    }
                }
        }
    }

    fun selectMode(mode: PracticeMode) {
        val state = _uiState.value
        if (state.mode == mode || state.isSaving) return
        if (state.isReinforcementQuestion && state.grade == null) {
            _uiState.value = state.copy(
                mode = mode,
                emptyMessage = null,
                saveErrorMessage = null,
            )
            return
        }
        _uiState.value = state.copy(
            mode = mode,
            question = null,
            assignments = emptyMap(),
            grade = null,
            isSaving = false,
            isReinforcementQuestion = false,
            pendingReinforcementCount = reinforcementQueue.size,
            emptyMessage = null,
            saveErrorMessage = null,
        )
        loadNextQuestion()
    }

    fun selectAssignment(assignment: Assignment) {
        if (_uiState.value.grade == null && !_uiState.value.isSaving) {
            _uiState.value = _uiState.value.copy(activeAssignment = assignment)
        }
    }

    fun toggleChoice(choiceId: ChoiceId) {
        val state = _uiState.value
        val question = state.question ?: return
        if (state.grade != null || choiceId !in question.choicesById) return

        val assignments = state.assignments.toMutableMap()
        val active = state.activeAssignment
        val current = assignments[choiceId]
        when {
            current == active -> assignments.remove(choiceId)
            assignments.count { it.value == active } < 2 -> assignments[choiceId] = active
            else -> return
        }

        val nextActive = if (assignments.count { it.value == active } == 2) {
            val other = if (active == Assignment.A) Assignment.B else Assignment.A
            if (assignments.count { it.value == other } < 2) other else active
        } else {
            active
        }
        _uiState.value = state.copy(assignments = assignments, activeAssignment = nextActive)
    }

    fun submit() {
        val state = _uiState.value
        val question = state.question ?: return
        if (!state.canSubmit || state.isSaving) return

        val response = PairDrillResponse.create(state.assignments)
        val grade = PairDrillGrader.grade(question, response)
        val completedCount = state.completedCount + 1
        val submittedAt = clock()
        val duration = (submittedAt - questionStartedAt).coerceAtLeast(0L)
        val submittedMode = state.mode
        val wasReinforcement = state.isReinforcementQuestion
        _uiState.value = state.copy(
            grade = grade,
            isSaving = true,
            completedCount = completedCount,
            correctCount = state.correctCount + if (grade.isCorrect) 1 else 0,
            pendingReinforcementCount = reinforcementQueue.size,
            saveErrorMessage = null,
        )

        viewModelScope.launch {
            try {
                val updatedReviews = persistResult(
                    question = question,
                    response = response,
                    grade = grade,
                    submittedAt = submittedAt,
                    durationMillis = duration,
                    mode = submittedMode,
                    isReinforcement = wasReinforcement,
                )
                latestReviews = latestReviews + updatedReviews.associateBy { it.groupId }
                updatedReviews.forEach { review ->
                    val delay = BurstPracticeScheduler.delayForProgress(review.box)
                        ?: HIGH_FREQUENCY_DELAY.takeIf {
                            review.lapseCount >= FREQUENT_ERROR_THRESHOLD
                        }
                    reinforcementQueue = if (delay == null) {
                        reinforcementQueue.remove(review.groupId)
                    } else {
                        reinforcementQueue.schedule(review.groupId, completedCount, delay)
                    }
                }
                replenishActivePool()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    pendingReinforcementCount = reinforcementQueue.size,
                )
                refreshProgressCounts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    grade = null,
                    isSaving = false,
                    completedCount = state.completedCount,
                    correctCount = state.correctCount,
                    saveErrorMessage = error.toDisplayMessage("练习记录保存失败"),
                )
            }
        }
    }

    fun nextQuestion() {
        if (_uiState.value.grade == null || _uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            question = null,
            assignments = emptyMap(),
            grade = null,
            isSaving = false,
            activeAssignment = Assignment.A,
            isReinforcementQuestion = false,
            emptyMessage = null,
            saveErrorMessage = null,
        )
        loadNextQuestion()
    }

    fun retryInitialization() {
        viewModelScope.launch {
            try {
                repository.initializeContent()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The repository exposes the failure through initializationState.
            }
        }
    }

    private fun loadNextQuestion() {
        if (latestGroups.isEmpty()) return

        val state = _uiState.value
        val acceptsReinforcement: (Int) -> Boolean = { groupId ->
            when (state.mode) {
                PracticeMode.SMART -> true
                PracticeMode.ERRORS -> isErrorGroup(groupId)
                PracticeMode.RANDOM -> false
            }
        }
        val duePoll = reinforcementQueue.pollDue(state.completedCount, acceptsReinforcement)
        val forcedIds = duePoll.item?.let(::setOf).orEmpty()
        val selection = candidatesFor(state.mode, forcedIds)
        if (selection == null) {
            _uiState.value = state.copy(
                isLoading = false,
                question = null,
                emptyMessage = emptyMessageFor(state.mode),
                pendingReinforcementCount = reinforcementQueue.size,
            )
            return
        }

        questionSequence += 1
        val generated = PairDrillQuestionGenerator.generate(
            questionId = "${clock()}-$questionSequence",
            prioritizedCandidates = selection.candidates,
            requiredTargetGroupIds = selection.requiredTargetIds,
            preferredTargetGroupIds = selection.preferredTargetIds,
            random = random,
        )
        when (generated) {
            is PairDrillGenerationResult.Success -> {
                if (duePoll.item != null) {
                    val targetIds = generated.question.correctPairs
                        .flatMap { pair -> pair.choiceIds.mapNotNull(generated.question.choicesById::get) }
                        .mapTo(mutableSetOf()) { it.sourceGroupId }
                    val consumedDueIds = reinforcementQueue
                        .dueItems(state.completedCount, acceptsReinforcement)
                        .toSet()
                        .intersect(targetIds)
                    reinforcementQueue = duePoll.queue.removeAll(consumedDueIds)
                }
                showQuestion(
                    question = generated.question,
                    isReinforcement = forcedIds.isNotEmpty(),
                )
            }
            is PairDrillGenerationResult.Failure -> {
                _uiState.value = state.copy(
                    isLoading = false,
                    question = null,
                    emptyMessage = "当前词组不足以生成无歧义题目，请切换出题方式。",
                    errorMessage = null,
                )
            }
        }
    }

    private fun showQuestion(
        question: PairDrillQuestion,
        isReinforcement: Boolean,
    ) {
        questionStartedAt = clock()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            question = question,
            assignments = emptyMap(),
            activeAssignment = Assignment.A,
            grade = null,
            isSaving = false,
            isReinforcementQuestion = isReinforcement,
            pendingReinforcementCount = reinforcementQueue.size,
            emptyMessage = null,
            errorMessage = null,
        )
    }

    private fun candidatesFor(
        mode: PracticeMode,
        forcedTargetIds: Set<Int> = emptySet(),
    ): CandidateSelection? {
        val shuffledGroups = latestGroups.shuffled(random)
        val dueIds = reinforcementQueue.dueItems(_uiState.value.completedCount).toSet()
        val waitingIds = reinforcementQueue.items - dueIds
        val active = shuffledGroups.filter { it.uuid in activePoolIds && it.uuid !in waitingIds }
        val newGroups = shuffledGroups.filter { it.uuid !in latestReviews }
        val errors = shuffledGroups.filter { isErrorGroup(it.uuid) && it.uuid !in waitingIds }

        val policy = mode.candidatePolicy(
            active = active,
            unseen = newGroups,
            errors = errors,
            all = shuffledGroups,
        )
        if (policy.requiresFocus && policy.focus.isEmpty() && forcedTargetIds.isEmpty()) return null

        // One strict focus leaves the second target available for a compatible active group.
        val required = forcedTargetIds.ifEmpty {
            policy.focus.firstOrNull()?.let { setOf(it.uuid) }.orEmpty()
        }
        val preferred = policy.focus.mapTo(mutableSetOf()) { it.uuid }.apply {
            addAll(forcedTargetIds)
        }
        val forcedGroups = forcedTargetIds.mapNotNull { id -> latestGroups.firstOrNull { it.uuid == id } }
        val prioritized = (forcedGroups + policy.prioritized).distinctBy { it.uuid }
        return CandidateSelection(prioritized, required, preferred)
    }

    private suspend fun persistResult(
        question: PairDrillQuestion,
        response: PairDrillResponse,
        grade: PairDrillGrade,
        submittedAt: Long,
        durationMillis: Long,
        mode: PracticeMode,
        isReinforcement: Boolean,
    ): List<ReviewState> {
        val groupOutcomes = question.correctPairs.map { pair ->
            val pairChoices = pair.choiceIds.mapNotNull(question.choicesById::get)
            val correct = isPairCorrect(pair.id, grade)
            val groupId = pairChoices.first().sourceGroupId
            val selectedWords = response.assignments.keys
                .mapNotNull(question.choicesById::get)
                .filter { it.sourceGroupId == groupId }
                .map { it.text }
            GroupPracticeOutcome(
                groupId = groupId,
                correct = correct,
                selectedWords = selectedWords,
                answerWords = pairChoices.map { it.text },
            )
        }

        val source = if (isReinforcement) "reinforcement" else mode.sourceCode
        val outcome = QuestionPracticeOutcome(
            questionId = question.id,
            timestamp = submittedAt,
            durationMillis = durationMillis,
            source = source,
            groups = groupOutcomes,
            confusions = confusionObservations(grade),
        )
        return repository.recordQuestionOutcome(outcome) { previous, correct, reviewedAt ->
            val next = BurstPracticeScheduler.review(previous?.box, correct)
            // dueAt remains in the legacy Room schema but question-count scheduling is in memory.
            ReviewSchedule(box = next.progress, dueAt = reviewedAt)
        }
    }

    private fun isPairCorrect(pairId: PairId, grade: PairDrillGrade): Boolean =
        grade.assignmentFeedback.any { feedback ->
            feedback.status == AssignmentFeedbackStatus.CORRECT_PAIR &&
                feedback.matchedPairId == pairId
        }

    private fun confusionObservations(grade: PairDrillGrade): List<ConfusionObservation> =
        grade.assignmentFeedback
            .filter { it.status == AssignmentFeedbackStatus.WRONG_PAIR }
            .flatMap { feedback ->
                val choices = feedback.selectedChoices
                buildList {
                    choices.indices.forEach { firstIndex ->
                        for (secondIndex in firstIndex + 1 until choices.size) {
                            val first = choices[firstIndex]
                            val second = choices[secondIndex]
                            if (first.sourceGroupId != second.sourceGroupId) {
                                add(
                                    ConfusionObservation(
                                        groupId = first.sourceGroupId,
                                        word = first.text,
                                        confusedWith = second.text,
                                        confusedGroupId = second.sourceGroupId,
                                    )
                                )
                                add(
                                    ConfusionObservation(
                                        groupId = second.sourceGroupId,
                                        word = second.text,
                                        confusedWith = first.text,
                                        confusedGroupId = first.sourceGroupId,
                                    )
                                )
                            }
                        }
                    }
                }
            }
            .distinctBy { observation ->
                listOf(
                    observation.groupId,
                    observation.word,
                    observation.confusedWith,
                    observation.confusedGroupId,
                )
            }

    private fun refreshProgressCounts() {
        _uiState.value = _uiState.value.copy(
            inProgressCount = latestReviews.values.count { !BurstPracticeScheduler.isPassed(it.box) },
            newCount = latestGroups.count { it.uuid !in latestReviews },
            errorCount = latestGroups.count { isErrorGroup(it.uuid) },
            passedCount = latestReviews.values.count { BurstPracticeScheduler.isPassed(it.box) },
        )
    }

    private fun replenishActivePool() {
        val groupIds = latestGroups.mapTo(mutableSetOf()) { it.uuid }
        activePoolIds.retainAll { groupId ->
            groupId in groupIds && latestReviews[groupId]?.box?.let {
                !BurstPracticeScheduler.isPassed(it)
            } != false
        }

        latestReviews.values
            .asSequence()
            .filter { !BurstPracticeScheduler.isPassed(it.box) }
            .sortedWith(compareByDescending<ReviewState> { isErrorGroup(it.groupId) }.thenBy { it.updatedAt })
            .map { it.groupId }
            .filter { it in groupIds }
            .forEach { groupId ->
                if (activePoolIds.size < ACTIVE_POOL_SIZE) activePoolIds += groupId
            }

        latestGroups
            .asSequence()
            .filter { it.uuid !in latestReviews }
            .shuffled(random)
            .map { it.uuid }
            .forEach { groupId ->
                if (activePoolIds.size < ACTIVE_POOL_SIZE) activePoolIds += groupId
            }
    }

    private fun isErrorGroup(groupId: Int): Boolean {
        val group = latestGroups.firstOrNull { it.uuid == groupId }
        val review = latestReviews[groupId]
        return group?.errorStates?.values?.any { it > 0 } == true ||
            review?.lastCorrect == false ||
            review?.box?.let { !BurstPracticeScheduler.isPassed(it) } == true ||
            (review?.lapseCount ?: 0) >= FREQUENT_ERROR_THRESHOLD
    }

    private fun emptyMessageFor(mode: PracticeMode): String = when (mode) {
        PracticeMode.SMART -> "有效词组不足，无法继续智能练习。"
        PracticeMode.ERRORS -> "目前没有强化中或高频错词，可以继续智能练习。"
        PracticeMode.RANDOM -> "有效词组不足，无法生成六选项题目。"
    }

    private data class CandidateSelection(
        val candidates: List<Group>,
        val requiredTargetIds: Set<Int>,
        val preferredTargetIds: Set<Int>,
    )

    class Factory(private val repository: GroupRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PracticeViewModel::class.java))
            return PracticeViewModel(repository) as T
        }
    }

    private companion object {
        const val ACTIVE_POOL_SIZE = 24
        const val FREQUENT_ERROR_THRESHOLD = 2
        const val HIGH_FREQUENCY_DELAY = 12
    }
}

private fun Throwable.toDisplayMessage(fallback: String): String =
    localizedMessage?.trim()?.takeIf { it.isNotEmpty() }
        ?: message?.trim()?.takeIf { it.isNotEmpty() }
        ?: fallback
