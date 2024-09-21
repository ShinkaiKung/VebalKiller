package com.github.ShinkaiKung.verbalkiller.practice

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord
import com.github.ShinkaiKung.verbalkiller.logic.updateGroupInDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

var globalAllGroups: MutableMap<Int, Group> = mutableMapOf() // uuid: group

val globalGroups: List<Group>
    get() {
        return globalAllGroups.values.toList()
    }

var globalSubGroups = globalGroups.take(100)

var globalSubGroupsDesc = mutableStateOf("")

fun getSubGroupsWithIndex(start: Int, end: Int) {
    val localSubGroups = mutableListOf<Group>()
    for (i in start until end) {
        localSubGroups.add(globalGroups.getOrNull(i) ?: break)
    }
    globalSubGroups = localSubGroups
    globalSubGroupsDesc.value = "${start + 1}-${end}"
}

fun getSubGroupsWithErrorState() {
    val localSubGroups = mutableListOf<Group>()
    globalGroups.forEach {
        if (it.errorStates.filter { it.value != 0 }.isNotEmpty()) {
            localSubGroups.add(it)
        }
    }
    globalSubGroups = localSubGroups
    globalSubGroupsDesc.value = "errors"
}

fun getUnpracticedGroups() {
    val localSubGroups = mutableListOf<Group>()
    globalGroups.forEach {
        if (it.errorStates.size != it.words.size) {
            localSubGroups.add(it)
        }
    }
    globalSubGroups = localSubGroups
    globalSubGroupsDesc.value = "unpracticed"
}

fun getAllGroups() {
    globalSubGroups = globalGroups
    globalSubGroupsDesc.value = "all"
}

fun getGroupsToPractice(size: Int): List<Group> {
    return globalSubGroups.shuffled().take(2 * size).sortedBy { it.memoryHistory.size }.take(size)
}

fun get6Words(selectedGroups: List<Group>): List<Pair<String, Group>> {
    if (selectedGroups.size != 4) {
        return emptyList()
    }
    val result = mutableListOf<Pair<String, Group>>()

    // Step 1: 从前两个组中各选择 2 个单词
    repeat(2) { i ->
        val groupIndex = i // 使用第一个和第二个组
        val groupWords = selectedGroups[groupIndex].words.shuffled().take(2)
        for (w in groupWords) {
            result.add(Pair(w, selectedGroups[groupIndex]))
        }
    }

    repeat(2) { i ->
        val groupIndex = 2 + i
        val groupWords = selectedGroups[groupIndex].words.shuffled().first()
        result.add(Pair(groupWords, selectedGroups[groupIndex]))
    }

    return result.shuffled()
}

fun checkAnswer(buttonColors: List<Int>, wordsGroups: List<Pair<String, Group>>): List<Int> {
    var rearrangedButtonColors = buttonColors.toMutableList()
    var firstColor = rearrangedButtonColors[0]
    rearrangedButtonColors.forEachIndexed { index, color ->
        if (firstColor == 0 && color != 0) {
            firstColor = color
        }
        if (firstColor == 2) {
            if (color == 2) {
                rearrangedButtonColors[index] = -1
            } else if (color == 1) {
                rearrangedButtonColors[index] = 2
            }
        }
    }
    rearrangedButtonColors = rearrangedButtonColors.map { kotlin.math.abs(it) }.toMutableList()

    val res = buttonColors.toMutableList()
    for (i in wordsGroups.indices) {
        if (rearrangedButtonColors[i] == 1 || rearrangedButtonColors[i] == 2) {
            for (j in i + 1 until wordsGroups.size) {
                if (rearrangedButtonColors[i] == rearrangedButtonColors[j]) {
                    val word_i = wordsGroups[i].first
                    val word_j = wordsGroups[j].first
                    if (wordsGroups[i].second.words.contains(word_j) && wordsGroups[j].second.words.contains(
                            word_i
                        )
                    ) {
                        // do nothing
                    } else if (wordsGroups[i].second.words.contains(word_j)
                        || wordsGroups[j].second.words.contains(word_i)
                    ) {
                        // do nothing
                    } else {
                        res[i] = 0
                        res[j] = 0
                    }
                }
            }
        }
    }

    val correctGroupIds = mutableListOf<Int>()
    val occurredGroupIds = mutableSetOf<Int>()
    wordsGroups.forEach { pair ->
        val id = pair.second.uuid
        if (occurredGroupIds.contains(id)) {
            correctGroupIds.add(id)
        }
        occurredGroupIds.add(id)
    }
    if (correctGroupIds.size != 2) {
        return res
    }

    var atLeastOneError = false
    for (i in 0 until res.size) {
        if (res[i] == 0 && correctGroupIds.contains(wordsGroups[i].second.uuid)) {
            for (j in i + 1 until res.size) {
                if (res[j] == 0 && correctGroupIds.contains(wordsGroups[j].second.uuid)) {
                    if (wordsGroups[i].second.uuid == wordsGroups[j].second.uuid) {
                        res[i] = if (!atLeastOneError) 3 else 4
                        res[j] = res[i]
                        if (!atLeastOneError) {
                            atLeastOneError = true
                        }
                    }
                }
            }
        }
    }

    return res
}

fun updateGroupState(
    context: Context,
    buttonColors: List<Int>,
    checkedColors: List<Int>,
    wordsGroups: List<Pair<String, Group>>
) {
    val updatedGroups = mutableListOf<Group>()
    val hasOccurredGroupIds = mutableSetOf<Int>()
    for (i in buttonColors.indices) {
        // if practice words
        if (checkedColors[i] != 0) {
            val group = wordsGroups[i].second
            val word = wordsGroups[i].first

            // if error
            var isCorrect: Boolean
            if (buttonColors[i] != checkedColors[i]) {
                group.errorStates[word] = 3
                isCorrect = false
            } else {
                if (group.errorStates.contains(word)) {
                    if (group.errorStates[word] != 0) {
                        group.errorStates[word] = group.errorStates[word]!! - 1
                    }
                } else {
                    group.errorStates[word] = 0
                }
                isCorrect = true
            }
            if (!hasOccurredGroupIds.contains(group.uuid)) {
                val now = System.currentTimeMillis()
                group.memoryHistory.add(MemoryRecord(now, isCorrect))
                hasOccurredGroupIds.add(group.uuid)
            }
            updatedGroups.add(group)
        }

    }
    // write to db
    GlobalScope.launch(Dispatchers.IO) {
        println("write to db: $updatedGroups")
        updatedGroups.forEach {
            updateGroupInDatabase(context, it)
        }
    }
    // update cache
    updatedGroups.forEach {
        globalAllGroups[it.uuid] = it
    }

}
