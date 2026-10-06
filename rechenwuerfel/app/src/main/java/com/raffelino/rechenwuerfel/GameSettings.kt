package com.raffelino.rechenwuerfel

import android.content.Context

/** Alle beim Spielstart konfigurierbaren Werte. */
data class GameSettings(
    var numberRange: Int = 100,
    var secondsPerTask: Int = 30,
    var boardFields: Int = 30,
    var stepsPlus: Int = 1,
    var stepsMinus: Int = 2,
    var stepsTimes: Int = 3,
    var stepsDivide: Int = 4,
    /** Rechenarten, die im Spiel vorkommen (mindestens eine). */
    var enabledOperations: Set<Operation> = Operation.values().toSet(),
) {
    fun stepsFor(op: Operation): Int = when (op) {
        Operation.PLUS -> stepsPlus
        Operation.MINUS -> stepsMinus
        Operation.TIMES -> stepsTimes
        Operation.DIVIDE -> stepsDivide
    }

    fun isEnabled(op: Operation): Boolean = op in enabledOperations

    /** Würfelseiten, die mit diesen Einstellungen fallen können: erlaubte Rechenarten, Joker und Aussetzen. */
    fun allowedFaces(): List<DiceFace> = DiceFace.values().filter { it.operation == null || it.operation in enabledOperations }

    /** Begrenzt alle Werte auf sinnvolle Bereiche; ohne aktive Rechenart werden alle aktiviert. */
    fun sanitized(): GameSettings = GameSettings(
        numberRange = numberRange.coerceIn(5, 10000),
        secondsPerTask = secondsPerTask.coerceIn(3, 600),
        boardFields = boardFields.coerceIn(6, 120),
        stepsPlus = stepsPlus.coerceIn(1, 20),
        stepsMinus = stepsMinus.coerceIn(1, 20),
        stepsTimes = stepsTimes.coerceIn(1, 20),
        stepsDivide = stepsDivide.coerceIn(1, 20),
        enabledOperations = if (enabledOperations.isEmpty()) Operation.values().toSet() else enabledOperations.toSet(),
    )

    /** Kompakte Darstellung der aktiven Rechenarten (Bitmaske in Reihenfolge der Operation-Werte). */
    fun operationsMask(): Int = Operation.values().foldIndexed(0) { i, acc, op -> if (op in enabledOperations) acc or (1 shl i) else acc }

    companion object {
        const val PREFS = "rechenwuerfel"
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SFX = "sfx_enabled"
        const val KEY_PLAYER_COUNT = "player_count"
        const val KEY_PLAYER_NAME = "player_name_"
        const val KEY_PLAYER_FIGURE = "player_figure_"

        fun load(ctx: Context): GameSettings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val d = GameSettings()
            return GameSettings(
                numberRange = p.getInt("number_range", d.numberRange),
                secondsPerTask = p.getInt("seconds_per_task", d.secondsPerTask),
                boardFields = p.getInt("board_fields", d.boardFields),
                stepsPlus = p.getInt("steps_plus", d.stepsPlus),
                stepsMinus = p.getInt("steps_minus", d.stepsMinus),
                stepsTimes = p.getInt("steps_times", d.stepsTimes),
                stepsDivide = p.getInt("steps_divide", d.stepsDivide),
                enabledOperations = operationsFromMask(p.getInt("operations_mask", d.operationsMask())),
            ).sanitized()
        }

        fun save(ctx: Context, s: GameSettings) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("number_range", s.numberRange)
                .putInt("seconds_per_task", s.secondsPerTask)
                .putInt("board_fields", s.boardFields)
                .putInt("steps_plus", s.stepsPlus)
                .putInt("steps_minus", s.stepsMinus)
                .putInt("steps_times", s.stepsTimes)
                .putInt("steps_divide", s.stepsDivide)
                .putInt("operations_mask", s.operationsMask())
                .apply()
        }

        fun operationsFromMask(mask: Int): Set<Operation> =
            Operation.values().filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
    }
}
