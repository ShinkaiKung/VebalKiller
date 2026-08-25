package com.github.ShinkaiKung.verbalkiller.logic

import android.content.Context
import androidx.room.*
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ConfusionDao
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ConfusionEntity
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ContentMetadataDao
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ContentMetadataEntity
import com.github.ShinkaiKung.verbalkiller.logic.persistence.MIGRATION_1_2
import com.github.ShinkaiKung.verbalkiller.logic.persistence.PersistenceCodec
import com.github.ShinkaiKung.verbalkiller.logic.persistence.PracticeAttemptDao
import com.github.ShinkaiKung.verbalkiller.logic.persistence.PracticeAttemptEntity
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ReviewStateDao
import com.github.ShinkaiKung.verbalkiller.logic.persistence.ReviewStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "group_table")
data class GroupEntity(
    @PrimaryKey val uuid: Int,
    val wordsJson: String,  // JSON 字符串表示 words
    val memoryHistoryJson: String, // JSON 字符串表示 memoryHistory
    val chineseMeaning: String = "",
    val errorStatesJson: String
) {
    fun toGroup(): Group = PersistenceCodec.entityToGroup(this)

    companion object {
        fun fromGroup(group: Group): GroupEntity = PersistenceCodec.groupToEntity(group)
    }
}

@Dao
interface GroupDao {

    @Upsert
    suspend fun insertGroup(group: GroupEntity)

    @Upsert
    suspend fun upsertGroups(groups: List<GroupEntity>)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("SELECT * FROM group_table WHERE uuid = :groupUuId")
    suspend fun getGroupById(groupUuId: Long): GroupEntity?

    @Query("SELECT * FROM group_table ORDER BY uuid")
    suspend fun getAllGroups(): List<GroupEntity>

    @Query("SELECT * FROM group_table ORDER BY uuid")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_table WHERE uuid = :groupUuId")
    fun observeGroupById(groupUuId: Int): Flow<GroupEntity?>
}


@Database(
    entities = [
        GroupEntity::class,
        PracticeAttemptEntity::class,
        ReviewStateEntity::class,
        ConfusionEntity::class,
        ContentMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class GroupDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun practiceAttemptDao(): PracticeAttemptDao
    abstract fun reviewStateDao(): ReviewStateDao
    abstract fun confusionDao(): ConfusionDao
    abstract fun contentMetadataDao(): ContentMetadataDao

    companion object {
        const val SCHEMA_VERSION = 2

        @Volatile
        private var INSTANCE: GroupDatabase? = null

        fun getDatabase(context: Context): GroupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GroupDatabase::class.java,
                    "group_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

suspend fun saveGroupToDatabase(context: Context, group: Group) {
    val db = GroupDatabase.getDatabase(context)
    val groupDao = db.groupDao()

    val groupEntity = GroupEntity.fromGroup(group)

    withContext(Dispatchers.IO) {
        groupDao.insertGroup(groupEntity)
    }
}

suspend fun getGroupFromDatabase(context: Context, groupUuid: Long): Group? {
    val db = GroupDatabase.getDatabase(context)
    val groupDao = db.groupDao()

    return withContext(Dispatchers.IO) {
        val groupEntity = groupDao.getGroupById(groupUuid)
        groupEntity?.toGroup()
    }
}

suspend fun updateGroupInDatabase(context: Context, group: Group) {
    val db = GroupDatabase.getDatabase(context)
    val groupDao = db.groupDao()

    val groupEntity = GroupEntity.fromGroup(group)

    withContext(Dispatchers.IO) {
        groupDao.updateGroup(groupEntity)
    }
}

fun isDatabaseExists(context: Context, dbName: String): Boolean {
    val dbFile: File = context.getDatabasePath(dbName)
    return dbFile.exists()
}
