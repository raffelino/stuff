package com.raffelino.rechenwuerfel

/** Ein Mitspieler mit Name, Figur (Emoji), Farbe und Position auf dem Brett. */
data class Player(val name: String, val figure: String, val color: Int, var position: Int = 0)

object Figures {
    val ALL = listOf("🐸", "🦊", "🐼", "🦁", "🐙", "🦄", "🐢", "🚀", "🐝", "🐬", "🦖", "🤖")

    val COLORS = intArrayOf(
        0xFFE53935.toInt(), // rot
        0xFF1E88E5.toInt(), // blau
        0xFF43A047.toInt(), // grün
        0xFFFB8C00.toInt(), // orange
    )
}
