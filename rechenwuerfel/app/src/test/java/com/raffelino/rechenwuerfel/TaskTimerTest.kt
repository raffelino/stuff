package com.raffelino.rechenwuerfel

import com.raffelino.rechenwuerfel.TestSupport.idle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskTimerTest {

    @Test
    fun ticksDownAndFinishes() {
        val ticks = ArrayList<Long>()
        var finished = false
        val timer = TaskTimer(1000L, 100L, { ticks.add(it) }, { finished = true }).start()
        idle()
        assertEquals(listOf(1000L), ticks)
        idle(450)
        assertTrue("Zwischenstände: $ticks", ticks.size >= 4 && ticks.last() in 500L..600L)
        assertFalse(finished)
        assertTrue(timer.remainingMs in 500L..600L)
        idle(600)
        assertTrue(finished)
        assertEquals(0L, ticks.last())
        assertEquals(0L, timer.remainingMs)
    }

    @Test
    fun cancelStopsTicks() {
        val ticks = ArrayList<Long>()
        var finished = false
        val timer = TaskTimer(1000L, 100L, { ticks.add(it) }, { finished = true }).start()
        idle(300)
        timer.cancel()
        val count = ticks.size
        idle(2000)
        assertEquals(count, ticks.size)
        assertFalse(finished)
    }
}
