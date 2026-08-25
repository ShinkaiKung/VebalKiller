package com.github.ShinkaiKung.verbalkiller.logic.persistence

import com.github.ShinkaiKung.verbalkiller.logic.Group
import java.security.MessageDigest

const val BUNDLED_WORDS_SOURCE = "raw/words.csv"
const val LEGACY_HISTORY_SOURCE = "legacy-memory-history"

data class ParsedContent(
    val groups: List<Group>,
    val contentHash: String,
)

data class ContentImportResult(
    val metadata: ContentMetadata,
    val changed: Boolean,
)

class ContentFormatException(message: String) : IllegalArgumentException(message)

/** Pure parser used by both Android import and local unit tests. */
fun parseWordsCsv(bytes: ByteArray): ParsedContent {
    val decoded = bytes.toString(Charsets.UTF_8)
    val text = if (decoded.firstOrNull() == '\uFEFF') decoded.drop(1) else decoded
    val groupsById = linkedMapOf<Int, Group>()

    text.lineSequence().forEachIndexed { index, rawLine ->
        val lineNumber = index + 1
        if (rawLine.isBlank()) return@forEachIndexed

        val columns = parseCsvRow(rawLine, lineNumber)
        if (columns.size < 3) {
            throw ContentFormatException("Line $lineNumber must contain id, word and synonyms")
        }

        val uuid = columns[0].trim().toIntOrNull()
            ?: throw ContentFormatException("Line $lineNumber has an invalid group id")
        if (groupsById.containsKey(uuid)) {
            throw ContentFormatException("Line $lineNumber duplicates group id $uuid")
        }

        val primaryWord = columns[1].trim()
        if (primaryWord.isEmpty()) {
            throw ContentFormatException("Line $lineNumber has an empty primary word")
        }

        val words = linkedSetOf(primaryWord)
        columns[2].split(';').mapTo(words) { it.trim() }
        words.removeAll { it.isEmpty() }
        if (words.size < 2) {
            throw ContentFormatException("Line $lineNumber must contain at least two distinct words")
        }

        groupsById[uuid] = Group(
            uuid = uuid,
            words = words,
            chineseMeaning = columns.drop(3).joinToString(",").trim(),
        )
    }

    if (groupsById.isEmpty()) throw ContentFormatException("The bundled word list is empty")
    return ParsedContent(groupsById.values.toList(), sha256(bytes))
}

/** Replaces bundled content while retaining all user-owned progress. */
fun mergeImportedGroup(existing: Group?, imported: Group): Group = Group(
    uuid = imported.uuid,
    words = imported.words.toMutableSet(),
    memoryHistory = existing?.memoryHistory?.toMutableList() ?: mutableListOf(),
    chineseMeaning = imported.chineseMeaning,
    errorStates = existing?.errorStates?.toMutableMap() ?: mutableMapOf(),
)

/** Converts v1 JSON history to normalized attempts during the first v2 content transaction. */
fun legacyAttemptsFor(groups: Iterable<Group>): List<PracticeAttempt> = groups.flatMap { group ->
    group.memoryHistory.map { record ->
        PracticeAttempt(
            groupId = group.uuid,
            timestamp = record.timestamp,
            isCorrect = record.isCorrect,
            source = LEGACY_HISTORY_SOURCE,
        )
    }
}

/** Builds a conservative v2 review queue from the progress that v1 persisted in JSON. */
fun legacyReviewStatesFor(groups: Iterable<Group>, now: Long): List<ReviewState> =
    groups.mapNotNull { group ->
        val history = group.memoryHistory.sortedBy { it.timestamp }
        if (history.isEmpty()) {
            if (group.errorStates.values.none { it > 0 }) return@mapNotNull null
            return@mapNotNull ReviewState(
                groupId = group.uuid,
                box = 0,
                dueAt = now,
                lastReviewedAt = null,
                lastCorrect = null,
                consecutiveCorrect = 0,
                lapseCount = 0,
                updatedAt = now,
            )
        }

        val last = history.last()
        val consecutiveCorrect = history.asReversed().takeWhile { it.isCorrect }.size
        val box = if (last.isCorrect) (consecutiveCorrect - 1).coerceIn(0, 4) else 0
        ReviewState(
            groupId = group.uuid,
            box = box,
            dueAt = saturatingAdd(last.timestamp, REVIEW_INTERVAL_MILLIS[box]),
            lastReviewedAt = last.timestamp,
            lastCorrect = last.isCorrect,
            consecutiveCorrect = consecutiveCorrect,
            lapseCount = history.count { !it.isCorrect },
            updatedAt = now,
        )
    }

fun nextContentVersion(previous: ContentMetadata?, newHash: String): Long = when {
    previous == null -> 1L
    previous.contentHash == newHash -> previous.contentVersion
    else -> previous.contentVersion + 1L
}

fun calculateDashboard(
    groups: List<Group>,
    reviewStates: List<ReviewState>,
    attempts: List<PracticeAttempt>,
    confusions: List<Confusion>,
    now: Long,
): DashboardSnapshot {
    val correctAttempts = attempts.count { it.isCorrect }
    return DashboardSnapshot(
        totalGroups = groups.size,
        practicedGroups = attempts.asSequence().map { it.groupId }.distinct().count(),
        dueReviews = reviewStates.count { it.dueAt <= now },
        totalAttempts = attempts.size,
        correctAttempts = correctAttempts,
        accuracy = if (attempts.isEmpty()) null else correctAttempts.toDouble() / attempts.size,
        confusionEvents = confusions.sumOf { it.count },
        lastAttemptAt = attempts.maxOfOrNull { it.timestamp },
    )
}

private fun parseCsvRow(line: String, lineNumber: Int): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                fields += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    if (inQuotes) throw ContentFormatException("Line $lineNumber has an unterminated quoted field")
    fields += current.toString()
    return fields
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun saturatingAdd(left: Long, right: Long): Long =
    runCatching { Math.addExact(left, right) }.getOrDefault(Long.MAX_VALUE)

private const val DAY_MILLIS = 86_400_000L
private val REVIEW_INTERVAL_MILLIS = longArrayOf(1, 3, 7, 14, 30)
    .map { days -> days * DAY_MILLIS }
