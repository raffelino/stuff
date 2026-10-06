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
    fun operationsCanBeExcluded() {
        val s = GameSettings(enabledOperations = setOf(Operation.PLUS, Operation.DIVIDE)).sanitized()
        assertTrue(s.isEnabled(Operation.PLUS))
        assertTrue(!s.isEnabled(Operation.MINUS))
        assertEquals(listOf(DiceFace.PLUS, DiceFace.DIVIDE, DiceFace.JOKER, DiceFace.SKIP), s.allowedFaces())
        // Maske hin und zurück
        assertEquals(0b1001, s.operationsMask())
        assertEquals(s.enabledOperations, GameSettings.operationsFromMask(s.operationsMask()))
        // Ohne Rechenart werden alle aktiviert
        assertEquals(Operation.values().toSet(), GameSettings(enabledOperations = emptySet()).sanitized().enabledOperations)
        assertEquals(Operation.values().toSet(), GameSettings.operationsFromMask(0).let { GameSettings(enabledOperations = it).sanitized().enabledOperations })
    }

    @Test
    fun diceRollsOnlyAllowedFaces() {
        DiceRoller.override = null
        val allowed = listOf(DiceFace.TIMES, DiceFace.JOKER, DiceFace.SKIP)
        val seen = HashSet<DiceFace>()
        repeat(300) { seen.add(DiceRoller.roll(allowed)) }
        assertEquals(allowed.toSet(), seen)
        // Leere Liste fällt auf alle Seiten zurück
        repeat(50) { assertTrue(DiceRoller.roll(emptyList()) in DiceFace.values()) }
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
