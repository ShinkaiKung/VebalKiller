package com.github.ShinkaiKung.verbalkiller.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.ShinkaiKung.verbalkiller.logic.Group
import com.github.ShinkaiKung.verbalkiller.ui.theme.md_theme_error_one
import com.github.ShinkaiKung.verbalkiller.ui.theme.md_theme_error_two
import com.github.ShinkaiKung.verbalkiller.ui.theme.md_theme_group_one
import com.github.ShinkaiKung.verbalkiller.ui.theme.md_theme_group_two


@Composable
fun PracticeLayout() {
    val context = LocalContext.current
    val isGroupASelected = remember { mutableStateOf(false) }
    val isGroupBSelected = remember { mutableStateOf(false) }
    // 用于存储当前选择的颜色
    var selectedColor by remember { mutableStateOf<Color?>(null) }
    // 用于存储每个按钮的状态（是否使用选择的颜色），初始状态都为 false
    val buttonStates = remember { mutableStateListOf(false, false, false, false, false, false) }
    val buttonColors = remember { mutableStateListOf(0, 0, 0, 0, 0, 0) }
    val words = remember { mutableStateListOf<Pair<String, Group>>() }
    val hasConfirmed = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (words.size == 0) {
            val newWords = get6Words(getGroupsToPractice(4))
            words.clear()
            words.addAll(newWords)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        // 第一行的两个按钮：绿色和黄色
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    selectedColor = md_theme_group_one
                    isGroupASelected.value = true
                    isGroupBSelected.value = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = md_theme_group_one),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (!isGroupASelected.value) 0.dp else 16.dp
                )
            ) {
                Text("A")
            }
            Button(
                onClick = {
                    selectedColor = md_theme_group_two
                    isGroupASelected.value = false
                    isGroupBSelected.value = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = md_theme_group_two),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (!isGroupBSelected.value) 0.dp else 16.dp
                )
            ) {
                Text("B")
            }
        }

        Column {
            // 通过 repeat 或 forEachIndexed 循环创建 6 个按钮
            repeat(6) { index ->
                Button(
                    onClick = {
                        if (selectedColor != null) {
                            // 切换按钮状态
                            buttonStates[index] = !buttonStates[index]
                            if (buttonStates[index]) {
                                if (selectedColor == md_theme_group_one) {
                                    buttonColors[index] = 1
                                } else if (selectedColor == md_theme_group_two) {
                                    buttonColors[index] = 2
                                }
                            } else {
                                buttonColors[index] = 0
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        // 根据按钮状态选择颜色
                        containerColor = if (buttonColors[index] == 1) md_theme_group_one
                        else if (buttonColors[index] == 2) md_theme_group_two
                        else if (buttonColors[index] == 3) md_theme_error_one
                        else if (buttonColors[index] == 4) md_theme_error_two
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = words.getOrNull(index)?.first ?: "",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }


        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!hasConfirmed.value) {
                        // confirm
                        val checkRes = checkAnswer(buttonColors, words)
                        updateGroupState(context, buttonColors, checkRes, words)
                        buttonColors.clear()
                        buttonColors.addAll(checkRes)
                        hasConfirmed.value = true
                    }
                },
                colors = if (!hasConfirmed.value) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Text("Confirm", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Button(
                onClick = {
                    // next
                    for (i in buttonStates.indices) {
                        buttonStates[i] = false
                        buttonColors[i] = 0
                        selectedColor = null
                    }
                    hasConfirmed.value = false
                    val newWords = get6Words(getGroupsToPractice(4))
                    words.clear()
                    words.addAll(newWords)
                    println("selected words: $words")
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text("Next", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun ColorChangingButtonsPreview() {
    PracticeLayout()
}