package com.raffelino.rechenwuerfel

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Countdown für eine Rechenaufgabe auf Basis von Handler + SystemClock.
 * (Ersatz für CountDownTimer, damit der Ablauf auch in JVM-Tests deterministisch ist.)
 */
class TaskTimer(
    private val totalMs: Long,
    private val tickMs: Long,
    private val onTick: (remainingMs: Long) -> Unit,
    private val onFinish: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var running = false
    private var finished = false

    /** Verbleibende Zeit in Millisekunden (vor dem Start die Gesamtzeit, nach Ablauf 0). */
    val remainingMs: Long
        get() = when {
            running -> (endAt - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            finished -> 0L
            else -> totalMs
        }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val left = endAt - SystemClock.uptimeMillis()
            if (left <= 0L) {
                running = false
                finished = true
                onTick(0L)
                onFinish()
            } else {
                onTick(left)
                handler.postDelayed(this, minOf(tickMs, left))
            }
        }
    }

    fun start(): TaskTimer {
        cancel()
        finished = false
        running = true
        endAt = SystemClock.uptimeMillis() + totalMs
        handler.post(tick)
        return this
    }

    fun cancel() {
        running = false
        handler.removeCallbacks(tick)
    }
}
