package com.github.ShinkaiKung.verbalkiller.logic

// 记忆记录类，存储每次练习的时间和正确性
data class MemoryRecord(
    val timestamp: Long,             // 练习时间毫秒时间戳
    val isCorrect: Boolean          // 是否回答正确
)

// Group 类，包含多个 Word 对象
data class Group(
    val uuid: Int, // generated in csv
    val words: MutableSet<String> = mutableSetOf(), // Word 集合
    val memoryHistory: MutableList<MemoryRecord> = mutableListOf(), // 组的记忆历史
    val chineseMeaning: String = "",
    val errorStates: MutableMap<String, Int> = mutableMapOf()
)
