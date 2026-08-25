package com.github.ShinkaiKung.verbalkiller.logic.persistence

import android.content.Context
import com.github.ShinkaiKung.verbalkiller.R
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.GroupDatabase
import com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord
import androidx.room.withTransaction
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ContentInitializationState {
    data object NotStarted : ContentInitializationState
    data object Loading : ContentInitializationState
    data class Ready(val result: ContentImportResult) : ContentInitializationState
    data class Failed(val error: Throwable) : ContentInitializationState
}

/**
 * Single persistence entry point. It intentionally depends only on the legacy [Group] model so
 * question-generation and UI domain models can evolve independently.
 */
class GroupRepository private constructor(
    private val appContext: Context,
    private val database: GroupDatabase,
    private val clock: () -> Long,
) {
    private val groupDao = database.groupDao()
    private val attemptDao = database.practiceAttemptDao()
    private val reviewDao = database.reviewStateDao()
    private val confusionDao = database.confusionDao()
    private val metadataDao = database.contentMetadataDao()
    private val initializationMutex = Mutex()
    private val _initializationState =
        MutableStateFlow<ContentInitializationState>(ContentInitializationState.NotStarted)

    val initializationState: StateFlow<ContentInitializationState> =
        _initializationState.asStateFlow()

    val groups: Flow<List<Group>> = groupDao.observeAllGroups()
        .map { entities -> entities.map { it.toGroup() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val reviewStates: Flow<List<ReviewState>> = reviewDao.observeAll()
        .map { entities -> entities.map { it.toModel() } }
        .distinctUntilChanged()

    val attempts: Flow<List<PracticeAttempt>> = attemptDao.observeAll()
        .map { entities -> entities.map(PersistenceCodec::entityToAttempt) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    val confusions: Flow<List<Confusion>> = confusionDao.observeAll()
        .map { entities -> entities.map { it.toModel() } }
        .distinctUntilChanged()

    val contentMetadata: Flow<List<ContentMetadata>> = metadataDao.observeAll()
        .map { entities -> entities.map { it.toModel() } }
        .distinctUntilChanged()

    val dashboard: Flow<DashboardSnapshot> = combine(
        groups,
        reviewStates,
        attempts,
        confusions,
    ) { currentGroups, currentReviews, currentAttempts, currentConfusions ->
        calculateDashboard(
            groups = currentGroups,
            reviewStates = currentReviews,
            attempts = currentAttempts,
            confusions = currentConfusions,
            now = clock(),
        )
    }.distinctUntilChanged()

    /** Reads, validates and imports raw/words.csv atomically if its SHA-256 changed. */
    suspend fun initializeContent(): ContentImportResult = initializationMutex.withLock {
        (_initializationState.value as? ContentInitializationState.Ready)?.let {
            return@withLock it.result
        }
        _initializationState.value = ContentInitializationState.Loading
        try {
            val bytes = withContext(Dispatchers.IO) {
                appContext.resources.openRawResource(R.raw.words).use { it.readBytes() }
            }
            val parsed = withContext(Dispatchers.Default) { parseWordsCsv(bytes) }
            val result = database.withTransaction {
                val previousEntity = metadataDao.get(BUNDLED_WORDS_SOURCE)
                val previous = previousEntity?.toModel()
                val existingById = groupDao.getAllGroups().associateBy { it.uuid }
                val allBundledGroupsExist = parsed.groups.all { it.uuid in existingById }
                if (
                    previous?.completed == true &&
                    previous.contentHash == parsed.contentHash &&
                    allBundledGroupsExist
                ) {
                    ContentImportResult(previous, changed = false)
                } else {
                    val merged = parsed.groups.map { imported ->
                        val existing = existingById[imported.uuid]?.toGroup()
                        PersistenceCodec.groupToEntity(mergeImportedGroup(existing, imported))
                    }
                    groupDao.upsertGroups(merged)

                    val importedAt = clock()

                    // A v1 database has no metadata row. Backfill its JSON history exactly once;
                    // the enclosing transaction prevents duplicates after an interrupted import.
                    if (previous == null) {
                        val legacyGroups = existingById.values.map { it.toGroup() }
                        val legacyAttempts = legacyAttemptsFor(
                            legacyGroups
                        ).map(PersistenceCodec::attemptToEntity)
                        if (legacyAttempts.isNotEmpty()) attemptDao.insertAll(legacyAttempts)

                        val legacyReviews = legacyReviewStatesFor(legacyGroups, importedAt)
                            .map { it.toEntity() }
                        if (legacyReviews.isNotEmpty()) reviewDao.upsertAll(legacyReviews)
                    }

                    val metadata = ContentMetadata(
                        source = BUNDLED_WORDS_SOURCE,
                        contentHash = parsed.contentHash,
                        contentVersion = nextContentVersion(previous, parsed.contentHash),
                        importedAt = importedAt,
                        rowCount = parsed.groups.size,
                        completed = true,
                        schemaVersion = GroupDatabase.SCHEMA_VERSION,
                    )
                    metadataDao.upsert(metadata.toEntity())
                    ContentImportResult(metadata, changed = true)
                }
            }
            _initializationState.value = ContentInitializationState.Ready(result)
            result
        } catch (cancelled: CancellationException) {
            _initializationState.value = ContentInitializationState.NotStarted
            throw cancelled
        } catch (error: Throwable) {
            _initializationState.value = ContentInitializationState.Failed(error)
            throw error
        }
    }

    /** Convenience for an Application/ViewModel-owned scope; no unstructured GlobalScope is used. */
    fun initializeIn(scope: CoroutineScope): Job = scope.launch {
        try {
            initializeContent()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // initializeContent has already published Failed; application startup must stay alive
            // so the UI can expose retry.
        }
    }

    fun observeGroup(groupId: Int): Flow<Group?> = groupDao.observeGroupById(groupId)
        .map { it?.toGroup() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun observeAttempts(groupId: Int): Flow<List<PracticeAttempt>> =
        attemptDao.observeForGroup(groupId)
            .map { entities -> entities.map(PersistenceCodec::entityToAttempt) }
            .flowOn(Dispatchers.Default)

    fun observeRecentAttempts(limit: Int): Flow<List<PracticeAttempt>> {
        require(limit > 0) { "limit must be greater than zero" }
        return attemptDao.observeRecent(limit)
            .map { entities -> entities.map(PersistenceCodec::entityToAttempt) }
            .flowOn(Dispatchers.Default)
    }

    fun observeReviewState(groupId: Int): Flow<ReviewState?> = reviewDao
        .observeForGroup(groupId)
        .map { it?.toModel() }

    fun observeDueReviews(now: Long): Flow<List<ReviewState>> = reviewDao.observeDue(now)
        .map { entities -> entities.map { it.toModel() } }

    fun observeConfusions(groupId: Int): Flow<List<Confusion>> =
        confusionDao.observeForGroup(groupId).map { entities -> entities.map { it.toModel() } }

    fun observeContentMetadata(source: String = BUNDLED_WORDS_SOURCE): Flow<ContentMetadata?> =
        metadataDao.observe(source).map { it?.toModel() }

    /** Persists every durable effect of one submitted question in one Room transaction. */
    suspend fun recordQuestionOutcome(
        outcome: QuestionPracticeOutcome,
        scheduleReview: (ReviewState?, Boolean, Long) -> ReviewSchedule,
    ): List<ReviewState> = database.withTransaction {
        val updatedReviews = mutableListOf<ReviewState>()

        outcome.groups.forEach { groupOutcome ->
            val groupEntity = groupDao.getGroupById(groupOutcome.groupId.toLong())
                ?: throw IllegalArgumentException("Unknown group id: ${groupOutcome.groupId}")
            attemptDao.insert(
                PersistenceCodec.attemptToEntity(
                    PracticeAttempt(
                        questionId = outcome.questionId,
                        groupId = groupOutcome.groupId,
                        timestamp = outcome.timestamp,
                        isCorrect = groupOutcome.correct,
                        selectedWords = groupOutcome.selectedWords,
                        answerWords = groupOutcome.answerWords,
                        durationMillis = outcome.durationMillis,
                        source = outcome.source,
                    )
                )
            )

            val group = groupEntity.toGroup()
            group.memoryHistory.add(
                MemoryRecord(timestamp = outcome.timestamp, isCorrect = groupOutcome.correct)
            )
            val errorStates = updatedErrorStates(
                current = group.errorStates,
                words = groupOutcome.answerWords,
                correct = groupOutcome.correct,
            )
            group.errorStates.clear()
            group.errorStates.putAll(errorStates)
            groupDao.insertGroup(PersistenceCodec.groupToEntity(group))

            if (groupOutcome.updateReview) {
                val previous = reviewDao.get(groupOutcome.groupId)?.toModel()
                val updated = calculateReviewUpdate(
                    groupId = groupOutcome.groupId,
                    previous = previous,
                    correct = groupOutcome.correct,
                    timestamp = outcome.timestamp,
                    scheduleReview = scheduleReview,
                )
                reviewDao.upsert(updated.toEntity())
                updatedReviews += updated
            }
        }

        outcome.confusions.forEach { observation ->
            requireGroupExists(observation.groupId)
            val word = observation.word.trim()
            val confusedWith = observation.confusedWith.trim()
            val previous = confusionDao.get(observation.groupId, word, confusedWith)
            confusionDao.upsert(
                ConfusionEntity(
                    groupUuid = observation.groupId,
                    word = word,
                    confusedWith = confusedWith,
                    confusedGroupUuid = observation.confusedGroupId
                        ?: previous?.confusedGroupUuid,
                    count = (previous?.count ?: 0) + 1,
                    lastOccurredAt = outcome.timestamp,
                )
            )

            observation.confusedGroupId?.let { distractorGroupId ->
                val distractorEntity = groupDao.getGroupById(distractorGroupId.toLong())
                    ?: throw IllegalArgumentException("Unknown group id: $distractorGroupId")
                val distractor = distractorEntity.toGroup()
                distractor.errorStates[confusedWith] = 3
                groupDao.insertGroup(PersistenceCodec.groupToEntity(distractor))
            }
        }

        updatedReviews
    }

    /**
     * Persists the normalized attempt and appends the legacy JSON history in one transaction.
     * This keeps existing screens/data exports compatible while new code reads practice_attempt.
     */
    suspend fun recordAttempt(
        groupId: Int,
        correct: Boolean,
        now: Long = clock(),
        questionId: String? = null,
        selectedWords: List<String> = emptyList(),
        answerWords: List<String> = emptyList(),
        durationMillis: Long? = null,
        source: String? = null,
    ): Long = database.withTransaction {
        val groupEntity = groupDao.getGroupById(groupId.toLong())
            ?: throw IllegalArgumentException("Unknown group id: $groupId")
        val id = attemptDao.insert(
            PersistenceCodec.attemptToEntity(
                PracticeAttempt(
                    questionId = questionId,
                    groupId = groupId,
                    timestamp = now,
                    isCorrect = correct,
                    selectedWords = selectedWords,
                    answerWords = answerWords,
                    durationMillis = durationMillis,
                    source = source,
                )
            )
        )

        val group = groupEntity.toGroup()
        group.memoryHistory.add(MemoryRecord(timestamp = now, isCorrect = correct))
        groupDao.insertGroup(PersistenceCodec.groupToEntity(group))
        id
    }

    /** Updates legacy per-word error counters without allowing UI code to overwrite content/history. */
    suspend fun updateGroupErrorStates(groupId: Int, errorStates: Map<String, Int>) {
        require(errorStates.values.all { it >= 0 }) { "error state values cannot be negative" }
        database.withTransaction {
            val entity = groupDao.getGroupById(groupId.toLong())
                ?: throw IllegalArgumentException("Unknown group id: $groupId")
            val group = entity.toGroup()
            group.errorStates.clear()
            group.errorStates.putAll(errorStates)
            groupDao.insertGroup(PersistenceCodec.groupToEntity(group))
        }
    }

    suspend fun upsertReviewState(state: ReviewState) {
        database.withTransaction {
            requireGroupExists(state.groupId)
            reviewDao.upsert(state.toEntity())
        }
    }

    suspend fun updateReview(
        groupId: Int,
        correct: Boolean,
        now: Long,
        nextBox: Int,
        dueAt: Long,
    ): ReviewState = database.withTransaction {
        require(nextBox >= 0) { "nextBox cannot be negative" }
        require(dueAt >= now) { "dueAt cannot be before now" }
        requireGroupExists(groupId)
        val previous = reviewDao.get(groupId)
        val updated = ReviewState(
            groupId = groupId,
            box = nextBox,
            dueAt = dueAt,
            lastReviewedAt = now,
            lastCorrect = correct,
            consecutiveCorrect = if (correct) (previous?.consecutiveCorrect ?: 0) + 1 else 0,
            lapseCount = (previous?.lapseCount ?: 0) + if (correct) 0 else 1,
            updatedAt = now,
        )
        reviewDao.upsert(updated.toEntity())
        updated
    }

    suspend fun recordConfusion(
        groupId: Int,
        word: String,
        confusedWith: String,
        now: Long = clock(),
        confusedGroupId: Int? = null,
    ): Confusion = database.withTransaction {
        require(word.isNotBlank()) { "word cannot be blank" }
        require(confusedWith.isNotBlank()) { "confusedWith cannot be blank" }
        requireGroupExists(groupId)
        val normalizedWord = word.trim()
        val normalizedConfusedWith = confusedWith.trim()
        val previous = confusionDao.get(groupId, normalizedWord, normalizedConfusedWith)
        val updated = ConfusionEntity(
            groupUuid = groupId,
            word = normalizedWord,
            confusedWith = normalizedConfusedWith,
            confusedGroupUuid = confusedGroupId ?: previous?.confusedGroupUuid,
            count = (previous?.count ?: 0) + 1,
            lastOccurredAt = now,
        )
        confusionDao.upsert(updated)
        updated.toModel()
    }

    suspend fun clearConfusion(groupId: Int, word: String, confusedWith: String) {
        confusionDao.delete(groupId, word.trim(), confusedWith.trim())
    }

    private suspend fun requireGroupExists(groupId: Int) {
        require(groupDao.getGroupById(groupId.toLong()) != null) { "Unknown group id: $groupId" }
    }

    companion object {
        private val instance = AtomicReference<GroupRepository?>()

        fun getInstance(context: Context): GroupRepository {
            instance.get()?.let { return it }
            val appContext = context.applicationContext
            val created = GroupRepository(
                appContext = appContext,
                database = GroupDatabase.getDatabase(appContext),
                clock = System::currentTimeMillis,
            )
            return if (instance.compareAndSet(null, created)) created else instance.get()!!
        }
    }
}

private fun ContentMetadata.toEntity() = ContentMetadataEntity(
    source = source,
    contentHash = contentHash,
    contentVersion = contentVersion,
    importedAt = importedAt,
    rowCount = rowCount,
    completed = completed,
    schemaVersion = schemaVersion,
)
