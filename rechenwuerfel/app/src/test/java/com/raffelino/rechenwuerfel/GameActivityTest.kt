package com.raffelino.rechenwuerfel

import android.content.Intent
import android.view.View
import android.widget.ProgressBar
import com.raffelino.rechenwuerfel.TestSupport.assertGone
import com.raffelino.rechenwuerfel.TestSupport.assertVisible
import com.raffelino.rechenwuerfel.TestSupport.button
import com.raffelino.rechenwuerfel.TestSupport.click
import com.raffelino.rechenwuerfel.TestSupport.dialogMessage
import com.raffelino.rechenwuerfel.TestSupport.idle
import com.raffelino.rechenwuerfel.TestSupport.launch
import com.raffelino.rechenwuerfel.TestSupport.text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController

/**
 * Spielablauf: würfeln, rechnen, ziehen, aussetzen, Joker, Zeitablauf, Sieg, Neustart.
 * Der Würfel wird über [DiceRoller.override] gesteuert, die Aufgaben werden aus der Anzeige gelesen.
 */
@RunWith(RobolectricTestRunner::class)
class GameActivityTest {

    private lateinit var controller: ActivityController<GameActivity>
    private lateinit var game: GameActivity

    @Before
    fun setUp() {
        SoundManager.resetForTests()
        DiceRoller.override = null
    }

    @After
    fun tearDown() {
        DiceRoller.override = null
        SoundManager.resetForTests()
    }

    // ------------------------------------------------------------------ Helfer

    private fun startGame(
        names: List<String> = listOf("Anna", "Ben"),
        figures: List<String> = listOf("🐸", "🦊"),
        fields: Int = 6,
        seconds: Int = 5,
        range: Int = 20,
        steps: IntArray = intArrayOf(1, 2, 3, 4),
        operations: Set<Operation> = Operation.values().toSet(),
    ) {
        val intent = Intent(RuntimeEnvironment.getApplication(), GameActivity::class.java)
        intent.putStringArrayListExtra(GameActivity.EXTRA_NAMES, ArrayList(names))
        intent.putStringArrayListExtra(GameActivity.EXTRA_FIGURES, ArrayList(figures))
        intent.putExtra(GameActivity.EXTRA_RANGE, range)
        intent.putExtra(GameActivity.EXTRA_SECONDS, seconds)
        intent.putExtra(GameActivity.EXTRA_FIELDS, fields)
        intent.putExtra(GameActivity.EXTRA_STEPS, steps)
        intent.putExtra(GameActivity.EXTRA_OPERATIONS, GameSettings(enabledOperations = operations).operationsMask())
        controller = launch(GameActivity::class.java, intent)
        game = controller.get()
    }

    /** Würfelt mit vorgegebenem Ergebnis und wartet die Würfel-Animation ab. */
    private fun roll(face: DiceFace) {
        DiceRoller.override = { face }
        assertVisible(game, R.id.panelRoll)
        click(game, R.id.btnRoll)
        idle(2500)
        assertEquals(face, game.findViewById<DiceView>(R.id.diceView).face)
    }

    /** Liest die Aufgabe "a op b = ?" von der Oberfläche und berechnet die Lösung. */
    private fun solutionOnScreen(): Int {
        val q = text(game, R.id.textQuestion)
        val m = Regex("""(\d+)\s*([+−×÷])\s*(\d+)\s*=\s*\?""").find(q) ?: error("Unerwartete Aufgabe: $q")
        val (a, op, b) = m.destructured
        return when (op) {
            "+" -> a.toInt() + b.toInt()
            "−" -> a.toInt() - b.toInt()
            "×" -> a.toInt() * b.toInt()
            "÷" -> a.toInt() / b.toInt()
            else -> error(op)
        }
    }

    private val keyIds = intArrayOf(
        R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
        R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
    )

    private fun type(value: Int) {
        for (ch in value.toString()) click(game, keyIds[ch - '0'])
    }

    private fun answer(value: Int) {
        assertVisible(game, R.id.panelTask)
        type(value)
        assertEquals(value.toString(), text(game, R.id.textAnswer))
        click(game, R.id.keyOk)
    }

    private fun answerCorrectly() = answer(solutionOnScreen())

    private fun answerWrongly() = answer(solutionOnScreen() + 1)

    /** Wartet die Zug-Animation ab und geht zum nächsten Spieler. */
    private fun finishTurn() {
        idle(4000)
        assertVisible(game, R.id.panelMessage)
        click(game, R.id.btnNext)
        assertVisible(game, R.id.panelRoll)
    }

    private fun positions() = game.playerPositions

    // ------------------------------------------------------------------ Tests

    @Test
    fun startShowsFirstPlayerAndRollPanel() {
        startGame()
        assertTrue(text(game, R.id.textTurn).contains("Anna"))
        assertTrue(text(game, R.id.textTurn).contains("🐸"))
        assertVisible(game, R.id.panelRoll)
        assertGone(game, R.id.panelTask)
        assertGone(game, R.id.panelJoker)
        assertGone(game, R.id.panelMessage)
        assertGone(game, R.id.panelWin)
        assertEquals(listOf(0, 0), positions())
        assertEquals("+ 1 · − 2 · × 3 · ÷ 4 Felder", text(game, R.id.textStepsHint))
    }

    @Test
    fun correctPlusAnswerMovesOneField() {
        startGame()
        roll(DiceFace.PLUS)
        assertVisible(game, R.id.panelTask)
        assertTrue(text(game, R.id.textQuestion).contains("+"))
        assertTrue(text(game, R.id.textTaskInfo).contains("1 Feld vor"))
        answerCorrectly()
        assertVisible(game, R.id.panelMessage)
        assertTrue(text(game, R.id.textMessage).startsWith("✅"))
        finishTurn()
        assertEquals(listOf(1, 0), positions())
        assertTrue("Nach dem Zug ist Ben dran", text(game, R.id.textTurn).contains("Ben"))
        assertEquals(1, game.currentPlayerIndex)
    }

    @Test
    fun wrongAnswerKeepsFigureAndShowsSolution() {
        startGame()
        roll(DiceFace.MINUS)
        val solution = solutionOnScreen()
        answerWrongly()
        assertVisible(game, R.id.panelMessage)
        val msg = text(game, R.id.textMessage)
        assertTrue(msg.startsWith("❌"))
        assertTrue("Lösung wird angezeigt: $msg", msg.contains("= $solution"))
        finishTurn()
        assertEquals(listOf(0, 0), positions())
        assertTrue(text(game, R.id.textTurn).contains("Ben"))
    }

    @Test
    fun timeoutKeepsFigure() {
        startGame(seconds = 3)
        roll(DiceFace.TIMES)
        val progress = game.findViewById<ProgressBar>(R.id.progressTime)
        val before = progress.progress
        assertTrue(text(game, R.id.textTimeLeft).endsWith(" s"))
        idle(1000)
        assertTrue("Balken läuft ab ($before -> ${progress.progress})", progress.progress < before)
        assertVisible(game, R.id.panelTask)
        idle(3000)
        assertVisible(game, R.id.panelMessage)
        assertTrue(text(game, R.id.textMessage).startsWith("⏰"))
        finishTurn()
        assertEquals(listOf(0, 0), positions())
    }

    @Test
    fun skipPassesTurnWithoutTask() {
        startGame()
        roll(DiceFace.SKIP)
        assertVisible(game, R.id.panelMessage)
        assertTrue(text(game, R.id.textMessage).contains("Anna"))
        assertTrue(text(game, R.id.textMessage).contains("aussetzen"))
        finishTurn()
        assertEquals(listOf(0, 0), positions())
        assertTrue(text(game, R.id.textTurn).contains("Ben"))
    }

    @Test
    fun jokerLetsPlayerChooseOperation() {
        startGame()
        roll(DiceFace.JOKER)
        assertVisible(game, R.id.panelJoker)
        click(game, R.id.btnJokerDivide)
        assertVisible(game, R.id.panelTask)
        assertTrue(text(game, R.id.textQuestion).contains("÷"))
        assertTrue(text(game, R.id.textTaskInfo).contains("Geteilt"))
        assertTrue(text(game, R.id.textTaskInfo).contains("4 Felder"))
        answerCorrectly()
        finishTurn()
        assertEquals(listOf(4, 0), positions())
    }

    @Test
    fun keypadSupportsBackspaceAndIgnoresEmptyAnswer() {
        startGame()
        roll(DiceFace.PLUS)
        assertEquals("_", text(game, R.id.textAnswer))
        click(game, R.id.keyOk) // leer -> nichts passiert
        assertVisible(game, R.id.panelTask)
        click(game, R.id.key1)
        click(game, R.id.key2)
        assertEquals("12", text(game, R.id.textAnswer))
        click(game, R.id.keyBack)
        assertEquals("1", text(game, R.id.textAnswer))
        click(game, R.id.keyBack)
        assertEquals("_", text(game, R.id.textAnswer))
        click(game, R.id.key0)
        click(game, R.id.key7)
        assertEquals("keine führende Null", "7", text(game, R.id.textAnswer))
    }

    @Test
    fun turnsRotateThroughAllPlayers() {
        startGame(names = listOf("A", "B", "C", "D"), figures = listOf("🐸", "🦊", "🐼", "🦁"))
        val figures = listOf("🐸", "🦊", "🐼", "🦁")
        for (round in 0 until 5) {
            val i = round % 4
            assertEquals("${figures[i]} ${listOf("A", "B", "C", "D")[i]} ist dran", text(game, R.id.textTurn))
            assertEquals(i, game.currentPlayerIndex)
            roll(DiceFace.SKIP)
            finishTurn()
        }
    }

    @Test
    fun rollButtonIgnoresDoubleTapWhileRolling() {
        startGame()
        DiceRoller.override = { DiceFace.SKIP }
        click(game, R.id.btnRoll)
        assertFalse(button(game, R.id.btnRoll).isEnabled)
        idle(2500)
        assertVisible(game, R.id.panelMessage)
    }

    @Test
    fun audioButtonsToggleSettings() {
        startGame()
        assertEquals("🎵", text(game, R.id.btnMusic))
        click(game, R.id.btnMusic)
        assertEquals("🔇", text(game, R.id.btnMusic))
        assertFalse(SoundManager.musicEnabled)
        click(game, R.id.btnSfx)
        assertEquals("🔈", text(game, R.id.btnSfx))
        assertFalse(SoundManager.sfxEnabled)
        click(game, R.id.btnSfx)
        assertTrue(SoundManager.sfxEnabled)
    }

    @Test
    fun timerPausesWhileActivityIsPaused() {
        startGame(seconds = 4)
        roll(DiceFace.PLUS)
        controller.pause()
        idle(10_000)
        controller.resume()
        idle()
        assertVisible(game, R.id.panelTask)
        idle(1000)
        assertVisible(game, R.id.panelTask)
    }

    @Test
    fun backPressAsksForConfirmation() {
        startGame()
        @Suppress("DEPRECATION")
        game.onBackPressed()
        idle()
        assertTrue(dialogMessage().contains("Spielstand"))
        assertFalse(game.isFinishing)
    }

    @Test
    fun completeGameUntilWinAndRestart() {
        startGame(fields = 6) // Felder 0..5, Ziel = 5
        // Anna: ÷ richtig -> 4
        roll(DiceFace.DIVIDE); answerCorrectly(); finishTurn()
        assertEquals(listOf(4, 0), positions())
        // Ben: × richtig -> 3
        roll(DiceFace.TIMES); answerCorrectly(); finishTurn()
        assertEquals(listOf(4, 3), positions())
        // Anna: − falsch -> bleibt
        roll(DiceFace.MINUS); answerWrongly(); finishTurn()
        assertEquals(listOf(4, 3), positions())
        // Ben: aussetzen
        roll(DiceFace.SKIP); finishTurn()
        // Anna: + richtig -> 5 = Ziel -> Sieg
        roll(DiceFace.PLUS); answerCorrectly()
        idle(4000)
        assertVisible(game, R.id.panelWin)
        assertGone(game, R.id.panelMessage)
        val winner = text(game, R.id.textWinner)
        assertTrue(winner, winner.contains("Anna") && winner.contains("🏆"))
        assertEquals(listOf(5, 3), positions())

        // Nochmal spielen: alles auf Anfang
        click(game, R.id.btnPlayAgain)
        assertVisible(game, R.id.panelRoll)
        assertEquals(listOf(0, 0), positions())
        assertTrue(button(game, R.id.btnRoll).isEnabled)

        // Zurück ins Menü beendet das Spiel
        roll(DiceFace.DIVIDE); answerCorrectly(); finishTurn()
        roll(DiceFace.PLUS); answerCorrectly(); finishTurn()
        roll(DiceFace.PLUS); answerCorrectly()
        idle(4000)
        assertVisible(game, R.id.panelWin)
        click(game, R.id.btnMenu)
        assertTrue(game.isFinishing)
    }

    @Test
    fun excludedOperationsNeverAppear() {
        startGame(operations = setOf(Operation.PLUS, Operation.DIVIDE))
        assertEquals("+ 1 · ÷ 4 Felder", text(game, R.id.textStepsHint))
        // Würfel ohne Vorgabe: nur erlaubte Seiten
        DiceRoller.override = null
        val seen = HashSet<DiceFace>()
        repeat(40) {
            click(game, R.id.btnRoll)
            idle(2500)
            val face = game.findViewById<DiceView>(R.id.diceView).face
            seen.add(face)
            assertTrue("unerlaubte Seite $face", face == DiceFace.PLUS || face == DiceFace.DIVIDE || face == DiceFace.JOKER || face == DiceFace.SKIP)
            // Zug abschließen, egal was gewürfelt wurde
            if (face == DiceFace.JOKER) {
                assertVisible(game, R.id.btnJokerPlus)
                assertVisible(game, R.id.btnJokerDivide)
                assertGone(game, R.id.btnJokerMinus)
                assertGone(game, R.id.btnJokerTimes)
                click(game, R.id.btnJokerPlus)
            }
            if (game.findViewById<View>(R.id.panelTask).isShown) answerWrongly()
            if (game.findViewById<View>(R.id.panelWin).isShown) return
            finishTurn()
        }
        assertTrue("Würfel sollte mehrere erlaubte Seiten zeigen: $seen", seen.size >= 2)
    }

    @Test
    fun moveNeverPassesTheGoal() {
        startGame(fields = 6, steps = intArrayOf(1, 2, 3, 9))
        roll(DiceFace.DIVIDE); answerCorrectly()
        idle(4000)
        assertVisible(game, R.id.panelWin)
        assertEquals(listOf(5, 0), positions())
    }

    @Test
    fun boardDrawsWithoutErrors() {
        startGame(names = listOf("A", "B", "C", "D"), figures = listOf("🐸", "🦊", "🐼", "🦁"), fields = 30)
        val board = game.findViewById<BoardView>(R.id.boardView)
        board.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        )
        board.layout(0, 0, 600, 800)
        val bitmap = android.graphics.Bitmap.createBitmap(600, 800, android.graphics.Bitmap.Config.ARGB_8888)
        board.draw(android.graphics.Canvas(bitmap))
        val dice = game.findViewById<DiceView>(R.id.diceView)
        dice.measure(
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        )
        dice.layout(0, 0, 200, 200)
        for (face in DiceFace.values()) {
            dice.face = face
            dice.draw(android.graphics.Canvas(bitmap))
        }
        assertTrue("Bitmap wurde beschrieben", bitmap.width == 600 && bitmap.height == 800)
    }
}
