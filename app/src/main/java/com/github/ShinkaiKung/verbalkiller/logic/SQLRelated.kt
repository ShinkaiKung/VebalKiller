package com.github.ShinkaiKung.verbalkiller.logic

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "group_table")
data class GroupEntity(
//    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @PrimaryKey val uuid: String,
    val wordsJson: String,  // JSON 字符串表示 words
    val memoryHistoryJson: String // JSON 字符串表示 memoryHistory
) {
    // 辅助函数将 JSON 转换回 Group 对象
    fun toGroup(): Group {
        val gson = Gson()
        val words: MutableMap<String, MutableList<MemoryRecord>> = gson.fromJson(
            wordsJson, object : com.google.gson.reflect.TypeToken<Map<String, MutableList<MemoryRecord>>>() {}.type
        )
        val memoryHistory: MutableList<MemoryRecord> = gson.fromJson(
            memoryHistoryJson, object : com.google.gson.reflect.TypeToken<MutableList<MemoryRecord>>() {}.type
        )
        return Group(uuid, words, memoryHistory)
    }

    companion object {
        // 辅助函数将 Group 对象转换为 GroupEntity
        fun fromGroup(group: Group): GroupEntity {
            val gson = Gson()
            val wordsJson = gson.toJson(group.words)
            val memoryHistoryJson = gson.toJson(group.memoryHistory)
            return GroupEntity(uuid = group.uuid, wordsJson = wordsJson, memoryHistoryJson = memoryHistoryJson)
        }
    }
}

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("SELECT * FROM group_table WHERE uuid = :groupUuId")
    suspend fun getGroupById(groupUuId: Long): GroupEntity?

    @Query("SELECT * FROM group_table")
    suspend fun getAllGroups(): List<GroupEntity>
}


@Database(entities = [GroupEntity::class], version = 1)
abstract class GroupDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: GroupDatabase? = null

        fun getDatabase(context: Context): GroupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GroupDatabase::class.java,
                    "group_database"
                ).build()
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
