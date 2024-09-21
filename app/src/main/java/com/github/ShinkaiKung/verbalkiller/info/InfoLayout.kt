package com.github.ShinkaiKung.verbalkiller.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.practice.globalAllGroups
import com.github.ShinkaiKung.verbalkiller.practice.globalSubGroups
import com.github.ShinkaiKung.verbalkiller.practice.globalSubGroupsDesc
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@Composable
fun InfoLayout() {
    val listState = rememberLazyListState() // State to track the scroll position
    val scope = rememberCoroutineScope() // Coroutine scope to handle scroll events
    var showButton by remember { mutableStateOf(false) } // State to control button visibility

    // Track scroll position and decide when to show the "Back to Top" button
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            showButton = index > 0 // Show button if the first item is not visible
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (globalSubGroupsDesc.value.isNotEmpty()) {
                items(globalSubGroups.sortedByDescending {
                    it.memoryHistory.lastOrNull()?.timestamp ?: 0
                }) { group ->
                    GroupCard(group)
                }
            } else {
                items(globalAllGroups.values.sortedByDescending {
                    it.memoryHistory.lastOrNull()?.timestamp ?: 0
                }) { group ->
                    GroupCard(group)
                }
            }
        }

        // "Back to Top" button
        if (showButton) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0) // Scroll back to the top
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Text("Top")
            }
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
            Text(
                text = "${group.uuid}: ${group.words.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (group.chineseMeaning.isNotEmpty()) {
                Text(
                    text = group.chineseMeaning,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (group.errorStates.isNotEmpty()) {
                val errorWords = group.errorStates.filter { it.value != 0 }.keys
                if (errorWords.isNotEmpty()) {
                    Text(
                        text = "Errors: ${errorWords.joinToString(", ")} ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Display group's memory history (optional)
            if (group.memoryHistory.isNotEmpty()) {
                Text(
                    text = "Latest Practice: ${timestampToDateTime(group.memoryHistory.last().timestamp)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal)
                )
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
