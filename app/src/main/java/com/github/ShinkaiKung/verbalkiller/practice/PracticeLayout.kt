@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.ShinkaiKung.verbalkiller.practice

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.ShinkaiKung.verbalkiller.domain.Assignment
import com.github.ShinkaiKung.verbalkiller.domain.ChoiceFeedbackStatus
import com.github.ShinkaiKung.verbalkiller.domain.ChoiceId
import com.github.ShinkaiKung.verbalkiller.domain.PairDrillChoice

@Composable
fun PracticeLayout(viewModel: PracticeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val errorMessage = state.errorMessage

    when {
        state.isLoading && state.question == null -> LoadingPractice()
        errorMessage != null && state.question == null -> PracticeError(
            message = errorMessage,
            onRetry = viewModel::retryInitialization,
        )
        else -> PracticeContent(
            state = state,
            onModeSelected = viewModel::selectMode,
            onAssignmentSelected = viewModel::selectAssignment,
            onChoiceClicked = viewModel::toggleChoice,
            onSubmit = viewModel::submit,
            onNext = viewModel::nextQuestion,
        )
    }
}

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    onModeSelected: (PracticeMode) -> Unit,
    onAssignmentSelected: (Assignment) -> Unit,
    onChoiceClicked: (ChoiceId) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ModeSelector(
                selected = state.mode,
                enabled = !state.isSaving && !(state.isReinforcementQuestion && state.grade == null),
                onSelected = onModeSelected,
            )
        }

        val question = state.question
        if (question == null) {
            item { EmptyPracticeState(state.emptyMessage ?: "暂时没有可练习题目") }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isReinforcementQuestion) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "轮内强化 · 重新生成干扰项与顺序",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        Text(
                            text = state.mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "A、B 各选一组同义词（每组 2 个）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AssignmentSelector(
                        active = state.activeAssignment,
                        countA = state.selectedInA,
                        countB = state.selectedInB,
                        enabled = state.grade == null,
                        onSelected = onAssignmentSelected,
                    )
                }
            }

            items(question.choices, key = { it.id.value }) { choice ->
                ChoiceButton(
                    choice = choice,
                    assignment = state.assignments[choice.id],
                    feedbackStatus = state.grade?.choiceFeedback
                        ?.firstOrNull { it.choice.id == choice.id }?.status,
                    enabled = state.grade == null,
                    onClick = { onChoiceClicked(choice.id) },
                )
            }

            item {
                if (state.grade == null) {
                    Button(
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("提交答案")
                    }
                } else {
                    Button(
                        onClick = onNext,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isSaving) "保存中…" else "下一题")
                    }
                }
            }

            if (state.grade != null) {
                item { AnswerFeedback(state) }
            }
        }

        state.saveErrorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ModeSelector(
    selected: PracticeMode,
    enabled: Boolean,
    onSelected: (PracticeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PracticeMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                enabled = enabled,
                label = {
                    Text(
                        text = mode.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AssignmentSelector(
    active: Assignment,
    countA: Int,
    countB: Int,
    enabled: Boolean,
    onSelected: (Assignment) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = active == Assignment.A,
            onClick = { onSelected(Assignment.A) },
            enabled = enabled,
            label = {
                Text(
                    text = "A · $countA/2",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = active == Assignment.B,
            onClick = { onSelected(Assignment.B) },
            enabled = enabled,
            label = {
                Text(
                    text = "B · $countB/2",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChoiceButton(
    choice: PairDrillChoice,
    assignment: Assignment?,
    feedbackStatus: ChoiceFeedbackStatus?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (containerColor, contentColor) = choiceColors(assignment, feedbackStatus)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = buildString {
                if (assignment != null) append("${assignment.name} · ")
                append(choice.text)
                when (feedbackStatus) {
                    ChoiceFeedbackStatus.CORRECTLY_GROUPED -> append("  ✓")
                    ChoiceFeedbackStatus.MISSED_TARGET -> append("  · 正确项")
                    ChoiceFeedbackStatus.INCORRECTLY_GROUPED_TARGET,
                    ChoiceFeedbackStatus.INCORRECTLY_SELECTED_DISTRACTOR -> append("  ×")
                    else -> Unit
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (assignment != null || feedbackStatus != null) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun choiceColors(
    assignment: Assignment?,
    status: ChoiceFeedbackStatus?,
): Pair<Color, Color> = when (status) {
    ChoiceFeedbackStatus.CORRECTLY_GROUPED,
    ChoiceFeedbackStatus.MISSED_TARGET -> MaterialTheme.colorScheme.secondaryContainer to
        MaterialTheme.colorScheme.onSecondaryContainer
    ChoiceFeedbackStatus.INCORRECTLY_GROUPED_TARGET,
    ChoiceFeedbackStatus.INCORRECTLY_SELECTED_DISTRACTOR -> MaterialTheme.colorScheme.errorContainer to
        MaterialTheme.colorScheme.onErrorContainer
    ChoiceFeedbackStatus.CORRECTLY_REJECTED_DISTRACTOR -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
    null -> when (assignment) {
        Assignment.A -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Assignment.B -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        null -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun AnswerFeedback(state: PracticeUiState) {
    val question = state.question ?: return
    val grade = state.grade ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (grade.isCorrect) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = buildString {
                    append(
                        if (grade.isCorrect) {
                            "完全正确 · 未通过词组会按 6/12 题间隔重现"
                        } else {
                            "答错词组将在隔 3 题后重现"
                        }
                    )
                    append(" · 本次 ${state.correctCount}/${state.completedCount}")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("正确词对", style = MaterialTheme.typography.labelLarge)
            question.correctPairs.forEach { pair ->
                val words = pair.choiceIds.mapNotNull(question.choicesById::get)
                    .joinToString("  ↔  ") { it.text }
                Text(words, fontWeight = FontWeight.SemiBold)
                Text(pair.explanation ?: "暂无中文义项", style = MaterialTheme.typography.bodyMedium)
            }
            val distractors = question.choices.filter { it.pairId == null }
            if (distractors.isNotEmpty()) {
                Text("干扰项", style = MaterialTheme.typography.labelLarge)
                distractors.forEach { choice ->
                    Text("${choice.text}：${choice.explanation ?: "暂无中文义项"}")
                }
            }
        }
    }
}

@Composable
private fun LoadingPractice() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("正在校验并载入词库…")
    }
}

@Composable
private fun PracticeError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("词库载入失败", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun EmptyPracticeState(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
