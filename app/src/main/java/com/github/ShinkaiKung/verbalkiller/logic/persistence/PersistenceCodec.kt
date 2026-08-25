package com.github.ShinkaiKung.verbalkiller.logic.persistence

import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.logic.GroupEntity
import com.github.ShinkaiKung.verbalkiller.logic.MemoryRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal object PersistenceCodec {
    private val gson = Gson()
    // Build parameterized types explicitly. Anonymous TypeToken subclasses can lose their
    // generic signature under R8 full-mode optimization and then fail during class initialization.
    private val wordsType = TypeToken.getParameterized(
        LinkedHashSet::class.java,
        String::class.java,
    ).type
    private val historyType = TypeToken.getParameterized(
        MutableList::class.java,
        MemoryRecord::class.java,
    ).type
    private val errorStatesType = TypeToken.getParameterized(
        MutableMap::class.java,
        String::class.java,
        Int::class.javaObjectType,
    ).type
    private val stringListType = TypeToken.getParameterized(
        List::class.java,
        String::class.java,
    ).type

    fun groupToEntity(group: Group): GroupEntity = GroupEntity(
        uuid = group.uuid,
        wordsJson = gson.toJson(group.words),
        memoryHistoryJson = gson.toJson(group.memoryHistory),
        chineseMeaning = group.chineseMeaning,
        errorStatesJson = gson.toJson(group.errorStates),
    )

    fun entityToGroup(entity: GroupEntity): Group = Group(
        uuid = entity.uuid,
        words = parseOrDefault(entity.wordsJson, wordsType, linkedSetOf()),
        memoryHistory = parseOrDefault(entity.memoryHistoryJson, historyType, mutableListOf()),
        chineseMeaning = entity.chineseMeaning,
        errorStates = parseOrDefault(entity.errorStatesJson, errorStatesType, mutableMapOf()),
    )

    fun attemptToEntity(attempt: PracticeAttempt): PracticeAttemptEntity = PracticeAttemptEntity(
        id = attempt.id,
        questionId = attempt.questionId,
        groupUuid = attempt.groupId,
        timestamp = attempt.timestamp,
        isCorrect = attempt.isCorrect,
        selectedWordsJson = gson.toJson(attempt.selectedWords),
        answerWordsJson = gson.toJson(attempt.answerWords),
        durationMillis = attempt.durationMillis,
        source = attempt.source,
    )

    fun entityToAttempt(entity: PracticeAttemptEntity): PracticeAttempt = PracticeAttempt(
        id = entity.id,
        questionId = entity.questionId,
        groupId = entity.groupUuid,
        timestamp = entity.timestamp,
        isCorrect = entity.isCorrect,
        selectedWords = parseOrDefault(entity.selectedWordsJson, stringListType, emptyList()),
        answerWords = parseOrDefault(entity.answerWordsJson, stringListType, emptyList()),
        durationMillis = entity.durationMillis,
        source = entity.source,
    )

    private fun <T> parseOrDefault(json: String, type: java.lang.reflect.Type, default: T): T {
        return runCatching { gson.fromJson<T>(json, type) }.getOrNull() ?: default
    }
}
