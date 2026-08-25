package com.github.ShinkaiKung.verbalkiller.logic.persistence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.ShinkaiKung.verbalkiller.logic.GroupEntity

/** A persisted result for one vocabulary group within a practice question. */
data class PracticeAttempt(
    val id: Long = 0,
    val questionId: String? = null,
    val groupId: Int,
    val timestamp: Long,
    val isCorrect: Boolean,
    val selectedWords: List<String> = emptyList(),
    val answerWords: List<String> = emptyList(),
    val durationMillis: Long? = null,
    val source: String? = null,
)

/** Scheduling state is deliberately algorithm-neutral so a ViewModel can choose the policy. */
data class ReviewState(
    val groupId: Int,
    val box: Int,
    val dueAt: Long,
    val lastReviewedAt: Long? = null,
    val lastCorrect: Boolean? = null,
    val consecutiveCorrect: Int = 0,
    val lapseCount: Int = 0,
    val updatedAt: Long,
)

data class Confusion(
    val groupId: Int,
    val word: String,
    val confusedWith: String,
    val confusedGroupId: Int? = null,
    val count: Int,
    val lastOccurredAt: Long,
)

data class ContentMetadata(
    val source: String,
    val contentHash: String,
    val contentVersion: Long,
    val importedAt: Long,
    val rowCount: Int,
    val completed: Boolean,
    val schemaVersion: Int,
)

data class DashboardSnapshot(
    val totalGroups: Int,
    val practicedGroups: Int,
    val dueReviews: Int,
    val totalAttempts: Int,
    val correctAttempts: Int,
    val accuracy: Double?,
    val confusionEvents: Int,
    val lastAttemptAt: Long?,
)

data class ReviewSchedule(
    val box: Int,
    val dueAt: Long,
) {
    init {
        require(box in 0..4) { "box must be between 0 and 4" }
    }
}

data class GroupPracticeOutcome(
    val groupId: Int,
    val correct: Boolean,
    val selectedWords: List<String> = emptyList(),
    val answerWords: List<String> = emptyList(),
    val updateReview: Boolean = true,
)

data class ConfusionObservation(
    val groupId: Int,
    val word: String,
    val confusedWith: String,
    val confusedGroupId: Int? = null,
) {
    init {
        require(word.isNotBlank()) { "word cannot be blank" }
        require(confusedWith.isNotBlank()) { "confusedWith cannot be blank" }
    }
}

data class QuestionPracticeOutcome(
    val questionId: String,
    val timestamp: Long,
    val durationMillis: Long? = null,
    val source: String? = null,
    val groups: List<GroupPracticeOutcome>,
    val confusions: List<ConfusionObservation> = emptyList(),
) {
    init {
        require(questionId.isNotBlank()) { "questionId cannot be blank" }
        require(durationMillis == null || durationMillis >= 0) {
            "durationMillis cannot be negative"
        }
        require(groups.isNotEmpty()) { "at least one group outcome is required" }
        require(groups.map { it.groupId }.distinct().size == groups.size) {
            "group outcomes must have unique group ids"
        }
    }
}

@Entity(
    tableName = "practice_attempt",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["groupUuid"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["groupUuid"]),
        Index(value = ["timestamp"]),
        Index(value = ["questionId"]),
    ],
)
data class PracticeAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String? = null,
    val groupUuid: Int,
    val timestamp: Long,
    val isCorrect: Boolean,
    val selectedWordsJson: String,
    val answerWordsJson: String,
    val durationMillis: Long? = null,
    val source: String? = null,
)

@Entity(
    tableName = "review_state",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["groupUuid"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["dueAt"]), Index(value = ["lastReviewedAt"])],
)
data class ReviewStateEntity(
    @PrimaryKey val groupUuid: Int,
    val box: Int,
    val dueAt: Long,
    val lastReviewedAt: Long? = null,
    val lastCorrect: Boolean? = null,
    val consecutiveCorrect: Int = 0,
    val lapseCount: Int = 0,
    val updatedAt: Long,
)

@Entity(
    tableName = "confusion",
    primaryKeys = ["groupUuid", "word", "confusedWith"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["groupUuid"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["lastOccurredAt"]), Index(value = ["confusedGroupUuid"])],
)
data class ConfusionEntity(
    val groupUuid: Int,
    val word: String,
    val confusedWith: String,
    val confusedGroupUuid: Int? = null,
    val count: Int,
    val lastOccurredAt: Long,
)

@Entity(tableName = "content_metadata")
data class ContentMetadataEntity(
    @PrimaryKey val source: String,
    val contentHash: String,
    val contentVersion: Long,
    val importedAt: Long,
    val rowCount: Int,
    val completed: Boolean,
    val schemaVersion: Int,
)

internal fun ReviewStateEntity.toModel() = ReviewState(
    groupId = groupUuid,
    box = box,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    lastCorrect = lastCorrect,
    consecutiveCorrect = consecutiveCorrect,
    lapseCount = lapseCount,
    updatedAt = updatedAt,
)

internal fun ReviewState.toEntity() = ReviewStateEntity(
    groupUuid = groupId,
    box = box,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    lastCorrect = lastCorrect,
    consecutiveCorrect = consecutiveCorrect,
    lapseCount = lapseCount,
    updatedAt = updatedAt,
)

internal fun ConfusionEntity.toModel() = Confusion(
    groupId = groupUuid,
    word = word,
    confusedWith = confusedWith,
    confusedGroupId = confusedGroupUuid,
    count = count,
    lastOccurredAt = lastOccurredAt,
)

internal fun ContentMetadataEntity.toModel() = ContentMetadata(
    source = source,
    contentHash = contentHash,
    contentVersion = contentVersion,
    importedAt = importedAt,
    rowCount = rowCount,
    completed = completed,
    schemaVersion = schemaVersion,
)
