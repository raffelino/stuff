package com.raffelino.rechenwuerfel

/** Die vier Rechenarten, die auf dem Würfel vorkommen. */
enum class Operation(val symbol: String, val label: String) {
    PLUS("+", "Plus"),
    MINUS("−", "Minus"),
    TIMES("×", "Mal"),
    DIVIDE("÷", "Geteilt");
}

/** Die sechs Seiten des Würfels. */
enum class DiceFace(val symbol: String, val label: String, val operation: Operation?, val color: Int) {
    PLUS("+", "Plus", Operation.PLUS, 0xFF2E7D32.toInt()),
    MINUS("−", "Minus", Operation.MINUS, 0xFFC62828.toInt()),
    TIMES("×", "Mal", Operation.TIMES, 0xFF1565C0.toInt()),
    DIVIDE("÷", "Geteilt", Operation.DIVIDE, 0xFF6A1B9A.toInt()),
    JOKER("🃏", "Joker", null, 0xFFEF6C00.toInt()),
    SKIP("💤", "Aussetzen", null, 0xFF616161.toInt());

    companion object {
        fun forOperation(op: Operation): DiceFace = values().first { it.operation == op }
    }
}

/**
 * Liefert das Würfelergebnis. Standard ist Zufall; Tests können über [override]
 * eine feste Folge vorgeben.
 */
object DiceRoller {
    @Volatile
    var override: (() -> DiceFace)? = null

    fun roll(): DiceFace = override?.invoke() ?: DiceFace.values()[kotlin.random.Random.nextInt(DiceFace.values().size)]
}
