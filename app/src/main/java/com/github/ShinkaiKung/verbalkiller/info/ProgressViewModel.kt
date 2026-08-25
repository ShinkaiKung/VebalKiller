package com.github.ShinkaiKung.verbalkiller.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.ShinkaiKung.verbalkiller.domain.BurstPracticeScheduler
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.persistence.Confusion
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ContentInitializationState
import com.github.ShinkaiKung.verbalkiller.logic.persistence.GroupRepository
import com.github.ShinkaiKung.verbalkiller.logic.persistence.PracticeAttempt
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ReviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ProgressFilter(val label: String, val description: String) {
    ALL("全部", "显示全部词组"),
    NEW("未练", "尚未进入轮内强化"),
    IN_PROGRESS("强化中", "连续答对不足 3 次"),
    PASSED("本轮通过", "已经连续答对 3 次"),
    FREQUENT_ERRORS("高频错词", "累计答错至少 2 次"),
}

data class GroupProgressItem(
    val group: Group,
    val reviewState: ReviewState?,
    val attemptCount: Int,
    val correctCount: Int,
    val errorCount: Int,
    val lastAttemptAt: Long?,
)

data class RetentionMetric(
    val correct: Int,
    val total: Int,
) {
    val rate: Double?
        get() = if (total == 0) null else correct.toDouble() / total
}

data class ProgressUiState(
    val isLoading: Boolean = true,
    val totalGroups: Int = 0,
    val newGroups: Int = 0,
    val inProgressGroups: Int = 0,
    val passedGroups: Int = 0,
    val frequentErrorGroups: Int = 0,
    val totalAttempts: Int = 0,
    val overallRetention: RetentionMetric = RetentionMetric(0, 0),
    val sevenDayRetention: RetentionMetric = RetentionMetric(0, 0),
    val thirtyDayRetention: RetentionMetric = RetentionMetric(0, 0),
    val topConfusions: List<Confusion> = emptyList(),
    val groups: List<GroupProgressItem> = emptyList(),
    val query: String = "",
    val filter: ProgressFilter = ProgressFilter.ALL,
    val errorMessage: String? = null,
)

class ProgressViewModel(
    private val repository: GroupRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private var latestSnapshot = ProgressSnapshot()

    init {
        viewModelScope.launch {
            combine(
                repository.initializationState,
                repository.groups,
                repository.reviewStates,
                repository.attempts,
                repository.confusions,
            ) { initialization, groups, reviews, attempts, confusions ->
                ProgressSnapshot(initialization, groups, reviews, attempts, confusions)
            }.collect { snapshot ->
                latestSnapshot = snapshot
                rebuild()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(TIME_REFRESH_MILLIS)
                rebuild()
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        rebuild()
    }

    fun selectFilter(filter: ProgressFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        rebuild()
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

    private fun rebuild() {
        val now = clock()
        val reviewsByGroup = latestSnapshot.reviews.associateBy { it.groupId }
        val attemptsByGroup = latestSnapshot.attempts.groupBy { it.groupId }
        val groupItems = latestSnapshot.groups.map { group ->
            val attempts = attemptsByGroup[group.uuid].orEmpty()
            val review = reviewsByGroup[group.uuid]
            GroupProgressItem(
                group = group,
                reviewState = review,
                attemptCount = attempts.size,
                correctCount = attempts.count { it.isCorrect },
                errorCount = group.errorStates.count { it.value > 0 },
                lastAttemptAt = attempts.maxOfOrNull { it.timestamp },
            )
        }

        val query = _uiState.value.query.trim().lowercase()
        val visible = groupItems.asSequence()
            .filter { item ->
                query.isEmpty() || item.group.words.any { it.lowercase().contains(query) } ||
                    item.group.chineseMeaning.lowercase().contains(query) ||
                    item.group.uuid.toString() == query
            }
            .filter { item ->
                when (_uiState.value.filter) {
                    ProgressFilter.ALL -> true
                    ProgressFilter.NEW -> item.reviewState == null
                    ProgressFilter.IN_PROGRESS -> item.reviewState?.box?.let {
                        !BurstPracticeScheduler.isPassed(it)
                    } == true
                    ProgressFilter.PASSED -> item.reviewState?.box?.let {
                        BurstPracticeScheduler.isPassed(it)
                    } == true
                    ProgressFilter.FREQUENT_ERRORS -> item.reviewState
                        ?.lapseCount
                        ?.let { it >= FREQUENT_ERROR_THRESHOLD } == true
                }
            }
            .sortedWith(
                compareBy<GroupProgressItem> { itemSortPriority(it) }
                    .thenByDescending { it.lastAttemptAt ?: 0L }
                    .thenBy { it.group.uuid }
            )
            .toList()

        val sevenDay = retentionSince(latestSnapshot.attempts, now - DAYS_7)
        val thirtyDay = retentionSince(latestSnapshot.attempts, now - DAYS_30)
        val overall = RetentionMetric(
            correct = latestSnapshot.attempts.count { it.isCorrect },
            total = latestSnapshot.attempts.size,
        )
        val initialization = latestSnapshot.initialization
        _uiState.value = _uiState.value.copy(
            isLoading = initialization is ContentInitializationState.NotStarted ||
                initialization is ContentInitializationState.Loading,
            totalGroups = groupItems.size,
            newGroups = groupItems.count { it.reviewState == null },
            inProgressGroups = groupItems.count { item ->
                item.reviewState?.box?.let { !BurstPracticeScheduler.isPassed(it) } == true
            },
            passedGroups = groupItems.count { item ->
                item.reviewState?.box?.let(BurstPracticeScheduler::isPassed) == true
            },
            frequentErrorGroups = groupItems.count { item ->
                (item.reviewState?.lapseCount ?: 0) >= FREQUENT_ERROR_THRESHOLD
            },
            totalAttempts = latestSnapshot.attempts.size,
            overallRetention = overall,
            sevenDayRetention = sevenDay,
            thirtyDayRetention = thirtyDay,
            topConfusions = latestSnapshot.confusions.sortedByDescending { it.count }.take(5),
            groups = visible,
            errorMessage = (initialization as? ContentInitializationState.Failed)
                ?.error?.toDisplayMessage("词库初始化失败"),
        )
    }

    private fun retentionSince(attempts: List<PracticeAttempt>, since: Long): RetentionMetric {
        val recent = attempts.filter { it.timestamp >= since }
        return RetentionMetric(recent.count { it.isCorrect }, recent.size)
    }

    private fun itemSortPriority(item: GroupProgressItem): Int = when {
        (item.reviewState?.lapseCount ?: 0) >= FREQUENT_ERROR_THRESHOLD -> 0
        item.reviewState?.box?.let { !BurstPracticeScheduler.isPassed(it) } == true -> 1
        item.reviewState == null -> 2
        else -> 3
    }

    private data class ProgressSnapshot(
        val initialization: ContentInitializationState = ContentInitializationState.NotStarted,
        val groups: List<Group> = emptyList(),
        val reviews: List<ReviewState> = emptyList(),
        val attempts: List<PracticeAttempt> = emptyList(),
        val confusions: List<Confusion> = emptyList(),
    )

    class Factory(private val repository: GroupRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            return ProgressViewModel(repository) as T
        }
    }

    private companion object {
        const val FREQUENT_ERROR_THRESHOLD = 2
        const val DAY_MILLIS = 86_400_000L
        const val DAYS_7 = 7L * DAY_MILLIS
        const val DAYS_30 = 30L * DAY_MILLIS
        const val TIME_REFRESH_MILLIS = 60_000L
    }
}

private fun Throwable.toDisplayMessage(fallback: String): String =
    localizedMessage?.trim()?.takeIf { it.isNotEmpty() }
        ?: message?.trim()?.takeIf { it.isNotEmpty() }
        ?: fallback
