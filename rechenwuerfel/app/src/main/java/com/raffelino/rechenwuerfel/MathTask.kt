package com.raffelino.rechenwuerfel

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Eine Rechenaufgabe mit Lösung. */
data class MathTask(val a: Int, val b: Int, val op: Operation, val result: Int) {
    val question: String get() = "$a ${op.symbol} $b = ?"
    val solution: String get() = "$a ${op.symbol} $b = $result"
}

/**
 * Erzeugt Rechenaufgaben, deren Zahlen und Ergebnisse im Zahlenraum 0..range liegen.
 * Bei Minus und Geteilt gibt es keine negativen Zahlen und keine Reste.
 */
object TaskGenerator {

    fun generate(op: Operation, range: Int, rnd: Random = Random.Default): MathTask {
        val n = max(range, 2)
        return when (op) {
            Operation.PLUS -> {
                val a = rnd.nextInt(1, n)          // 1 .. n-1
                val b = rnd.nextInt(1, n - a + 1)  // 1 .. n-a
                MathTask(a, b, op, a + b)
            }
            Operation.MINUS -> {
                val a = rnd.nextInt(2, n + 1)      // 2 .. n
                val b = rnd.nextInt(1, a)          // 1 .. a-1
                MathTask(a, b, op, a - b)
            }
            Operation.TIMES -> {
                val maxFactor = factorLimit(n)
                val a = rnd.nextInt(1, maxFactor + 1)
                val bMax = max(1, min(maxFactor, n / a))
                val b = rnd.nextInt(1, bMax + 1)
                MathTask(a, b, op, a * b)
            }
            Operation.DIVIDE -> {
                val maxFactor = factorLimit(n)
                val divisor = rnd.nextInt(1, maxFactor + 1)
                val qMax = max(1, min(maxFactor, n / divisor))
                val quotient = rnd.nextInt(1, qMax + 1)
                MathTask(divisor * quotient, divisor, op, quotient)
            }
        }
    }

    /** Größter Faktor fürs kleine Einmaleins; bei großen Zahlenräumen etwas größer. */
    private fun factorLimit(n: Int): Int = when {
        n < 10 -> max(2, n)
        n <= 100 -> 10
        n <= 400 -> 12
        else -> 20
    }
}
