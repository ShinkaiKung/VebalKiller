@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.ShinkaiKung.verbalkiller.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ShinkaiKung.verbalkiller.logic.persistence.Confusion
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InfoLayout(viewModel: ProgressViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading && state.totalGroups == 0) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("正在汇总强化进度…")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.errorMessage?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("数据载入失败", fontWeight = FontWeight.Bold)
                        Text(message)
                        Button(onClick = viewModel::retryInitialization) { Text("重试") }
                    }
                }
            }
        }

        item { DashboardOverview(state) }
        item { RetentionOverview(state) }
        if (state.topConfusions.isNotEmpty()) {
            item { ConfusionOverview(state.topConfusions) }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索单词、释义或编号") },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressFilter.entries.chunked(FILTER_COLUMNS).forEach { rowFilters ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowFilters.forEach { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.selectFilter(filter) },
                                label = {
                                    Text(
                                        text = filter.label,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(FILTER_COLUMNS - rowFilters.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    text = "${state.filter.label}：${state.filter.description}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                text = "词组 · ${state.groups.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(state.groups, key = { it.group.uuid }) { item ->
            GroupProgressCard(item)
        }
        if (state.groups.isEmpty() && state.errorMessage == null) {
            item {
                Text(
                    text = "当前筛选没有结果",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun DashboardOverview(state: ProgressUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("突破概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("词组", state.totalGroups.toString(), Modifier.weight(1f))
                Metric("未练", state.newGroups.toString(), Modifier.weight(1f))
                Metric("强化中", state.inProgressGroups.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("本轮通过", state.passedGroups.toString(), Modifier.weight(1f))
                Metric("高频错词", state.frequentErrorGroups.toString(), Modifier.weight(1f))
                Metric("作答记录", state.totalAttempts.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RetentionOverview(state: ProgressUiState) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("词组作答正确率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RetentionValue("全部", state.overallRetention)
                RetentionValue("近 7 天", state.sevenDayRetention)
                RetentionValue("近 30 天", state.thirtyDayRetention)
            }
            Text(
                "按词组级作答记录计算各时间窗口内的正确率；它不等同于长期记忆保持率。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RetentionValue(label: String, metric: RetentionMetric) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = metric.rate?.let { String.format(Locale.ROOT, "%.0f%%", it * 100) } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text("$label · ${metric.correct}/${metric.total}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ConfusionOverview(confusions: List<Confusion>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("常见混淆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            confusions.forEach { confusion ->
                Text("${confusion.word}  ↔  ${confusion.confusedWith} · ${confusion.count} 次")
            }
        }
    }
}

@Composable
private fun GroupProgressCard(item: GroupProgressItem) {
    var expanded by remember(item.group.uuid) { mutableStateOf(false) }
    val review = item.reviewState
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${item.group.uuid} · ${item.group.words.joinToString(", ")}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.group.chineseMeaning.isNotBlank()) {
                Text(item.group.chineseMeaning)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("作答正确 ${item.correctCount}/${item.attemptCount}", style = MaterialTheme.typography.bodySmall)
                Text(
                    review?.let {
                        if (it.box >= 3) "本轮通过" else "强化进度 ${it.box.coerceAtLeast(0)}/3"
                    } ?: "未练",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.errorCount > 0 || review?.lastCorrect == false || (review?.lapseCount ?: 0) >= 2) {
                Text(
                    text = when {
                        (review?.lapseCount ?: 0) >= 2 -> "高频错词 · 累计答错 ${review?.lapseCount}"
                        item.errorCount > 0 -> "待强化词 ${item.errorCount}"
                        else -> "上次答错"
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (expanded) {
                review?.let {
                    Text(
                        "连续答对：${it.box.coerceIn(0, 3)}/3",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("累计答错：${it.lapseCount}", style = MaterialTheme.typography.bodySmall)
                }
                item.lastAttemptAt?.let {
                    Text("最近练习：${timestampToDateTime(it)}", style = MaterialTheme.typography.bodySmall)
                }
                val errorWords = item.group.errorStates.filterValues { it > 0 }.keys
                if (errorWords.isNotEmpty()) {
                    Text("待强化词：${errorWords.joinToString(", ")}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun timestampToDateTime(timestamp: Long): String {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private const val FILTER_COLUMNS = 3
