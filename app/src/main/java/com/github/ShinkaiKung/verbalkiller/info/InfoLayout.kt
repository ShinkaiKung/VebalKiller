package com.github.ShinkaiKung.verbalkiller.info
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.practice.globalAllGroups
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@Composable
fun InfoLayout() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(globalAllGroups.values.sortedByDescending {
            it.memoryHistory.lastOrNull()?.timestamp ?: 0
        }) { group ->
            GroupCard(group)
        }
    }
}

@Composable
fun GroupCard(group: Group) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Display all words and their memory records
            group.words.forEach { (word, memoryRecords) ->
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Display memory records for each word
                memoryRecords.forEach { record ->
                    val isCorrect = record.isCorrect
                    Text(
                        text = "Timestamp: ${timestampToDateTime(record.timestamp)}, Correct: ${if (isCorrect) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCorrect) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Display group's memory history (optional)
            if (group.memoryHistory.isNotEmpty()) {
                Text(
                    text = "Group Memory History:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                group.memoryHistory.forEach { record ->
                    val isCorrect = record.isCorrect
                    Text(
                        text = "Timestamp: ${timestampToDateTime(record.timestamp)}, Correct: ${if (record.isCorrect) "Yes" else "No"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCorrect) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
fun timestampToDateTime(timestamp: Long): String {
    // 将时间戳转换为 LocalDateTime
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

    // 定义日期时间的格式化方式
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // 格式化为字符串
    return dateTime.format(formatter)
}
