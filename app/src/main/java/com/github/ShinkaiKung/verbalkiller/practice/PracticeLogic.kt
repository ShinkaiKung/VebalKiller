package com.github.ShinkaiKung.verbalkiller.practice

import android.content.Context
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord
import com.github.ShinkaiKung.verbalkiller.logic.updateGroupInDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

var globalAllGroups: MutableMap<String, Group> = mutableMapOf() // uuid: group

// 记忆记录类，存储每次练习的时间和正确性
data class MemoryRecord(
    val timestamp: Long,             // 练习时间（以分钟为单位的时间戳）
    val isCorrect: Boolean           // 是否回答正确
)

// Group 类，包含多个 Word 对象
data class Group(
    val uuid: String,
    val words: MutableMap<String, MutableList<MemoryRecord>> = mutableMapOf(), // Word 集合
    val memoryHistory: MutableList<MemoryRecord> = mutableListOf() // 组的记忆历史
)


val groups: List<Group>
    get() {
        return globalAllGroups.values.toList()
    }

fun get4Groups(): List<Group> {
    return groups.shuffled().take(8).sortedBy { it.memoryHistory.size }.take(4)
}

fun get6Words(selectedGroups: List<Group>): Map<String, Group> {
    if (selectedGroups.size != 4) {
        return emptyMap()
    }
    val result = mutableMapOf<String, Group>()

    // Step 1: 从前两个组中各选择 2 个单词
    repeat(2) { i ->
        val groupIndex = i // 使用第一个和第二个组
        val groupWords = selectedGroups[groupIndex].words.keys.shuffled().take(4)
            .sortedBy { word ->
                selectedGroups[groupIndex].words[word]?.size ?: 0
            }.take(2)
        for (w in groupWords) {
            result[w] = selectedGroups[groupIndex]
        }
    }

    repeat(2) { i ->
        val groupIndex = 2 + i
        val groupWords = selectedGroups[groupIndex].words.keys.shuffled().first()

        result[groupWords] = selectedGroups[groupIndex]

    }

    return result

}

fun checkAnswer(buttonColors: List<Int>, wordsGroups: Map<String, Group?>): List<Int> {
    val res = buttonColors.toMutableList()
    val correctAnswers = mutableListOf<String>()
    val groupUuids = mutableSetOf<String>()
    wordsGroups.forEach {
        if (it.value != null) {
            if (it.value!!.uuid in groupUuids) {
                correctAnswers.add(it.value!!.uuid)
            }
            groupUuids.add(it.value!!.uuid)

        }
    }

    val words = wordsGroups.keys.toList()
    val selectedGroupA = mutableMapOf<Int, String>() // index: uuid
    val selectedGroupB = mutableMapOf<Int, String>() // index: uuid
    val selectedGroupAUuid = mutableSetOf<String>()
    val selectedGroupBUuid = mutableSetOf<String>()
    words.forEachIndexed { index, word ->
        val uuid = wordsGroups[word]?.uuid
        if (buttonColors[index] == 1) {
            selectedGroupA[index] = uuid!!
            selectedGroupAUuid.add(uuid)
        } else if (buttonColors[index] == 2) {
            selectedGroupB[index] = uuid!!
            selectedGroupBUuid.add(uuid)
        }
    }

    val occurredCorrect = mutableMapOf<String, Int>() // uuid: color
    if (selectedGroupAUuid.size != 1) {
        selectedGroupA.forEach {
            val currentUuid = it.value
            // 选择了错误答案，消除这个的颜色
            if (!correctAnswers.contains(currentUuid)) {
                res[it.key] = 0
            }
            // 选择了部分正确答案，将这个答案和另一个答案标为颜色 3
            else {
                val color: Int = if (occurredCorrect.contains(currentUuid)) {
                    occurredCorrect[currentUuid]!!
                } else {
                    if (occurredCorrect.values.contains(3)) {
                        4
                    } else 3
                }
                words.forEachIndexed { index, word ->
                    if (wordsGroups[word]?.uuid == currentUuid) {
                        res[index] = color
                    }
                }
                occurredCorrect[currentUuid] = color
            }
        }
    }
    if (selectedGroupBUuid.size != 1) {
        selectedGroupB.forEach {
            val currentUuid = it.value
            // 选择了错误答案，消除这个的颜色
            if (!correctAnswers.contains(currentUuid)) {
                res[it.key] = 0
            }
            // 选择了部分正确答案
            else {
                val color: Int = if (occurredCorrect.contains(currentUuid)) {
                    occurredCorrect[currentUuid]!!
                } else {
                    if (occurredCorrect.values.contains(3)) {
                        4
                    } else 3
                }
                words.forEachIndexed { index, word ->
                    if (wordsGroups[word]?.uuid == currentUuid) {
                        res[index] = color
                    }
                }
                occurredCorrect[currentUuid] = color
            }
        }
    }

    println("res: $res")

    return res
}

fun updateGroupState(context: Context, buttonColors: List<Int>, checkedColors: List<Int>, wordsGroups: Map<String, Group?>) {
    val errorWordSet = mutableSetOf<String>()
    val wordList = wordsGroups.keys.toList()
    wordList.forEachIndexed { index, word ->
        if (buttonColors[index] != checkedColors[index]) {
            errorWordSet.add(word)
        }
    }
    val now = System.currentTimeMillis()
    val groupMap = mutableMapOf<String, Group>() // uuid: group
    wordsGroups.forEach {
        val group = it.value
        if (group != null) {
            groupMap[group.uuid] = group
        }
    }
    groupMap.forEach { uuid, group ->
        val words = group.words
        var groupIsCorrect = true
        words.filterKeys { wordList.contains(it) }.forEach {
            val word = it.key
            if(errorWordSet.contains(word)) {
                group.words[word]?.add(MemoryRecord(now, false))
                groupIsCorrect = false
            } else {
                group.words[word]?.add(MemoryRecord(now, true))
            }
        }
        group.memoryHistory.add(MemoryRecord(now, groupIsCorrect))

    }
    println("groupMap: $groupMap")
    // write to db
    GlobalScope.launch(Dispatchers.IO) {
        println("write to db: $groupMap")
        groupMap.forEach {
            updateGroupInDatabase(context, it.value)
        }
    }
    // update cache
    groupMap.forEach {
        globalAllGroups[it.key] = it.value
    }

}
