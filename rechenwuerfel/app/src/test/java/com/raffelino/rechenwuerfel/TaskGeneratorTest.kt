package com.raffelino.rechenwuerfel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TaskGeneratorTest {

    private val ranges = listOf(5, 10, 20, 50, 100, 200, 1000)

    @Test
    fun plusStaysInRange() {
        val rnd = Random(1)
        for (range in ranges) repeat(500) {
            val t = TaskGenerator.generate(Operation.PLUS, range, rnd)
            assertEquals(t.a + t.b, t.result)
            assertTrue("$t", t.a >= 1 && t.b >= 1 && t.result <= range)
        }
    }

    @Test
    fun minusNeverNegative() {
        val rnd = Random(2)
        for (range in ranges) repeat(500) {
            val t = TaskGenerator.generate(Operation.MINUS, range, rnd)
            assertEquals(t.a - t.b, t.result)
            assertTrue("$t", t.a <= range && t.b >= 1 && t.result >= 1)
        }
    }

    @Test
    fun timesStaysInRange() {
        val rnd = Random(3)
        for (range in ranges) repeat(500) {
            val t = TaskGenerator.generate(Operation.TIMES, range, rnd)
            assertEquals(t.a * t.b, t.result)
            assertTrue("$t", t.a >= 1 && t.b >= 1 && t.result <= range)
        }
    }

    @Test
    fun divideHasNoRemainder() {
        val rnd = Random(4)
        for (range in ranges) repeat(500) {
            val t = TaskGenerator.generate(Operation.DIVIDE, range, rnd)
            assertEquals(0, t.a % t.b)
            assertEquals(t.a / t.b, t.result)
            assertTrue("$t", t.a <= range && t.b >= 1 && t.result >= 1)
        }
    }

    @Test
    fun questionAndSolutionText() {
        val t = MathTask(12, 4, Operation.DIVIDE, 3)
        assertEquals("12 ÷ 4 = ?", t.question)
        assertEquals("12 ÷ 4 = 3", t.solution)
    }

    @Test
    fun settingsAreSanitized() {
        val s = GameSettings(numberRange = 0, secondsPerTask = 99999, boardFields = 1, stepsPlus = 0).sanitized()
        assertEquals(5, s.numberRange)
        assertEquals(600, s.secondsPerTask)
        assertEquals(6, s.boardFields)
        assertEquals(1, s.stepsPlus)
        assertEquals(4, s.stepsFor(Operation.DIVIDE))
    }
}
