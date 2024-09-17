package com.github.ShinkaiKung.verbalkiller.logic

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
