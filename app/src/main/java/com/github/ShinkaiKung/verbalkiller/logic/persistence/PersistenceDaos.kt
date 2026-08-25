package com.github.ShinkaiKung.verbalkiller.logic.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeAttemptDao {
    @Query("SELECT * FROM practice_attempt ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<PracticeAttemptEntity>>

    @Query(
        "SELECT * FROM practice_attempt WHERE groupUuid = :groupId " +
            "ORDER BY timestamp DESC, id DESC"
    )
    fun observeForGroup(groupId: Int): Flow<List<PracticeAttemptEntity>>

    @Query("SELECT * FROM practice_attempt ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PracticeAttemptEntity>>

    @Insert
    suspend fun insert(entity: PracticeAttemptEntity): Long

    @Insert
    suspend fun insertAll(entities: List<PracticeAttemptEntity>): List<Long>
}

@Dao
interface ReviewStateDao {
    @Query("SELECT * FROM review_state ORDER BY dueAt, groupUuid")
    fun observeAll(): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_state WHERE dueAt <= :now ORDER BY dueAt, groupUuid")
    fun observeDue(now: Long): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_state WHERE groupUuid = :groupId")
    fun observeForGroup(groupId: Int): Flow<ReviewStateEntity?>

    @Query("SELECT * FROM review_state WHERE groupUuid = :groupId")
    suspend fun get(groupId: Int): ReviewStateEntity?

    @Upsert
    suspend fun upsert(entity: ReviewStateEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ReviewStateEntity>)
}

@Dao
interface ConfusionDao {
    @Query("SELECT * FROM confusion ORDER BY lastOccurredAt DESC")
    fun observeAll(): Flow<List<ConfusionEntity>>

    @Query("SELECT * FROM confusion WHERE groupUuid = :groupId ORDER BY lastOccurredAt DESC")
    fun observeForGroup(groupId: Int): Flow<List<ConfusionEntity>>

    @Query(
        "SELECT * FROM confusion WHERE groupUuid = :groupId " +
            "AND word = :word AND confusedWith = :confusedWith"
    )
    suspend fun get(groupId: Int, word: String, confusedWith: String): ConfusionEntity?

    @Upsert
    suspend fun upsert(entity: ConfusionEntity)

    @Query(
        "DELETE FROM confusion WHERE groupUuid = :groupId " +
            "AND word = :word AND confusedWith = :confusedWith"
    )
    suspend fun delete(groupId: Int, word: String, confusedWith: String)
}

@Dao
interface ContentMetadataDao {
    @Query("SELECT * FROM content_metadata ORDER BY source")
    fun observeAll(): Flow<List<ContentMetadataEntity>>

    @Query("SELECT * FROM content_metadata WHERE source = :source")
    fun observe(source: String): Flow<ContentMetadataEntity?>

    @Query("SELECT * FROM content_metadata WHERE source = :source")
    suspend fun get(source: String): ContentMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContentMetadataEntity)
}
