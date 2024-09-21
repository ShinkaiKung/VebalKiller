package com.github.ShinkaiKung.verbalkiller

import com.github.ShinkaiKung.verbalkiller.practice.checkAnswer

import com.github.ShinkaiKung.verbalkiller.logic.Group
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}


class CheckAnswerTest {

    private val wordsGroupsA: List<Pair<String, Group>> = listOf(
        "A0" to Group(0, mutableSetOf("A0", "A1", "A2")),
        "A1" to Group(0, mutableSetOf("A0", "A1", "A2")),
        "B0" to Group(1, mutableSetOf("B0", "B1", "B2")),
        "B1" to Group(1, mutableSetOf("B0", "B1", "B2")),
        "C0" to Group(2, mutableSetOf("C0", "C1", "C2")),
        "D0" to Group(3, mutableSetOf("D0", "D1", "D2")),
    )

    @Test
    fun checkAnswer1() {
        val buttonColors: List<Int> = listOf(1, 1, 2, 2, 0, 0)
        assertEquals(listOf(1, 1, 2, 2, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer2() {
        val buttonColors: List<Int> = listOf(1, 1, 2, 0, 2, 0)
        assertEquals(listOf(1, 1, 3, 3, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer3() {
        val buttonColors: List<Int> = listOf(1, 1, 2, 0, 0, 2)
        assertEquals(listOf(1, 1, 3, 3, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer4() {
        val buttonColors: List<Int> = listOf(1, 1, 0, 0, 2, 2)
        assertEquals(listOf(1, 1, 3, 3, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer5() {
        val buttonColors: List<Int> = listOf(1, 0, 2, 2, 1, 0)
        assertEquals(listOf(3, 3, 2, 2, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer6() {
        val buttonColors: List<Int> = listOf(1, 0, 2, 2, 0, 1)
        assertEquals(listOf(3, 3, 2, 2, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer7() {
        val buttonColors: List<Int> = listOf(0, 0, 1, 1, 2, 2)
        assertEquals(listOf(3, 3, 1, 1, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer8() {
        val buttonColors: List<Int> = listOf(0, 0, 2, 2, 1, 1)
        assertEquals(listOf(3, 3, 2, 2, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer9() {
        val buttonColors: List<Int> = listOf(1, 0, 1, 0, 2, 2)
        assertEquals(listOf(3, 3, 4, 4, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer10() {
        val buttonColors: List<Int> = listOf(1, 0, 1, 2, 0, 2)
        assertEquals(listOf(3, 3, 4, 4, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }

    @Test
    fun checkAnswer11() {
        val buttonColors: List<Int> = listOf(1, 2, 1, 2, 0, 0)
        assertEquals(listOf(3, 3, 4, 4, 0, 0), checkAnswer(buttonColors, wordsGroupsA))
    }


}