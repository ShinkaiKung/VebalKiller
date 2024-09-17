import android.content.Context
import com.github.ShinkaiKung.verbalkiller.R
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.GroupDatabase
import com.github.ShinkaiKung.verbalkiller.logic.isDatabaseExists
import com.github.ShinkaiKung.verbalkiller.logic.saveGroupToDatabase
import com.github.ShinkaiKung.verbalkiller.practice.globalAllGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

suspend fun readCsvFromRes(context: Context) : MutableList<Group> {
    val inputStream: InputStream = context.resources.openRawResource(R.raw.words)

    val groupList = mutableListOf<Group>()

    inputStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            val group = Group(UUID.randomUUID().toString())

            val tokens = line.split(",")

            tokens.forEach {
                val word = it.trim().replace("\"", "")
                group.words[word] = mutableListOf()
            }

            groupList.add(group)

            saveGroupToDatabase(context, group)
        }
    }
    println(groupList)
    return groupList
}

// 从数据库加载所有 Group 到内存
suspend fun loadGroupsToMemory(context: Context): MutableList<Group> {
    val db = GroupDatabase.getDatabase(context)
    val groupDao = db.groupDao()
    // 查询所有的 GroupEntity
    val groupEntities = groupDao.getAllGroups()
    // 将 GroupEntity 转换为 Group 对象并加载到内存中
    val groups = groupEntities.map { it.toGroup() }.toMutableList()
    // 将 groups 加载到内存
    // 你可以在这里根据需求处理 groups 对象，例如保存到全局变量、缓存或其它内存结构
    println("Loaded groups into memory: $groups")
    return groups
}

// 启动时执行的操作
suspend fun initDatabase(context: Context): MutableList<Group> {
    val dbName = "group_database"

    if (isDatabaseExists(context, dbName)) {
        println("读取所有 Group 到内存")
        return loadGroupsToMemory(context)
    } else {
        println("保存 Group 到数据库")
        return readCsvFromRes(context)
    }
}

// 在你的 Activity 或 Application 中调用初始化
fun onAppStart(context: Context) {
    // 在这里你可以使用协程处理异步操作
    GlobalScope.launch(Dispatchers.IO) {
        val allGroups = initDatabase(context)
        allGroups.forEach { group ->
            globalAllGroups[group.uuid] = group
        }
    }
}

