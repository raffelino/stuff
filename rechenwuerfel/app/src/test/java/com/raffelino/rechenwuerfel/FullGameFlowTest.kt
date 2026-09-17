package com.raffelino.rechenwuerfel

import android.widget.EditText
import android.widget.LinearLayout
import com.raffelino.rechenwuerfel.TestSupport.assertNextActivity
import com.raffelino.rechenwuerfel.TestSupport.assertVisible
import com.raffelino.rechenwuerfel.TestSupport.click
import com.raffelino.rechenwuerfel.TestSupport.idle
import com.raffelino.rechenwuerfel.TestSupport.launch
import com.raffelino.rechenwuerfel.TestSupport.text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ende-zu-Ende: Hauptmenü -> Spiel konfigurieren -> komplettes Spiel bis zum Sieg -> zurück ins Menü.
 */
@RunWith(RobolectricTestRunner::class)
class FullGameFlowTest {

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

    private val keyIds = intArrayOf(
        R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
        R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
    )

    private fun solve(game: GameActivity) {
        val q = text(game, R.id.textQuestion)
        val m = Regex("""(\d+)\s*([+−×÷])\s*(\d+)""").find(q)!!
        val (a, op, b) = m.destructured
        val result = when (op) {
            "+" -> a.toInt() + b.toInt()
            "−" -> a.toInt() - b.toInt()
            "×" -> a.toInt() * b.toInt()
            else -> a.toInt() / b.toInt()
        }
        assertTrue("Ergebnis im Zahlenraum 50: $q = $result", result in 0..50)
        for (ch in result.toString()) click(game, keyIds[ch - '0'])
        click(game, R.id.keyOk)
    }

    private fun playTurn(game: GameActivity, face: DiceFace) {
        DiceRoller.override = { face }
        click(game, R.id.btnRoll)
        idle(2500)
        when (face) {
            DiceFace.SKIP -> Unit
            DiceFace.JOKER -> { click(game, R.id.btnJokerTimes); solve(game) }
            else -> solve(game)
        }
        idle(4000)
    }

    @Test
    fun menuToConfiguredGameToWinAndBack() {
        // 1. Hauptmenü
        val menu = launch(MainActivity::class.java).get()
        click(menu, R.id.btnPlay)
        val setupIntent = assertNextActivity(menu, SetupActivity::class.java)

        // 2. Setup: drei Spieler, Namen, Figuren, Zahlenraum 50, 10 Sekunden, 8 Felder, Schritte 2/2/3/4
        val setup = launch(SetupActivity::class.java, setupIntent).get()
        click(setup, R.id.btnCount3)
        val container = setup.findViewById<LinearLayout>(R.id.playersContainer)
        val names = listOf("Mia", "Leo", "Zoe")
        names.forEachIndexed { i, n -> container.getChildAt(i).findViewById<EditText>(R.id.editName).setText(n) }
        container.getChildAt(2).findViewById<LinearLayout>(R.id.figureBar).getChildAt(10).performClick()
        click(setup, R.id.chipRange50)
        click(setup, R.id.chipTime10)
        setup.findViewById<EditText>(R.id.editFields).setText("8")
        setup.findViewById<EditText>(R.id.editStepsPlus).setText("2")
        click(setup, R.id.btnStart)
        val gameIntent = assertNextActivity(setup, GameActivity::class.java)
        assertEquals(names, gameIntent.getStringArrayListExtra(GameActivity.EXTRA_NAMES))
        assertEquals(Figures.ALL[10], gameIntent.getStringArrayListExtra(GameActivity.EXTRA_FIGURES)!![2])

        // 3. Spiel: Felder 0..7, Ziel = 7
        val game = launch(GameActivity::class.java, gameIntent).get()
        assertTrue(text(game, R.id.textTurn).contains("Mia"))
        assertEquals("+ 2 · − 2 · × 3 · ÷ 4 Felder", text(game, R.id.textStepsHint))

        playTurn(game, DiceFace.DIVIDE)   // Mia -> 4
        click(game, R.id.btnNext)
        assertTrue(text(game, R.id.textTurn).contains("Leo"))
        playTurn(game, DiceFace.JOKER)    // Leo (Joker -> Mal) -> 3
        click(game, R.id.btnNext)
        assertTrue(text(game, R.id.textTurn).contains("Zoe"))
        playTurn(game, DiceFace.SKIP)     // Zoe setzt aus
        click(game, R.id.btnNext)
        assertEquals(listOf(4, 3, 0), game.playerPositions)
        playTurn(game, DiceFace.PLUS)     // Mia -> 6
        click(game, R.id.btnNext)
        playTurn(game, DiceFace.MINUS)    // Leo -> 5
        click(game, R.id.btnNext)
        playTurn(game, DiceFace.TIMES)    // Zoe -> 3
        click(game, R.id.btnNext)
        assertEquals(listOf(6, 5, 3), game.playerPositions)
        playTurn(game, DiceFace.PLUS)     // Mia -> 7 = Ziel

        assertVisible(game, R.id.panelWin)
        assertTrue(text(game, R.id.textWinner).contains("Mia"))
        assertEquals(listOf(7, 5, 3), game.playerPositions)

        // 4. Zurück ins Menü
        click(game, R.id.btnMenu)
        assertTrue(game.isFinishing)
    }
}
