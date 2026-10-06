package com.raffelino.rechenwuerfel

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.raffelino.rechenwuerfel.TestSupport.assertNextActivity
import com.raffelino.rechenwuerfel.TestSupport.button
import com.raffelino.rechenwuerfel.TestSupport.click
import com.raffelino.rechenwuerfel.TestSupport.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spieler-Setup und Einstellungen. */
@RunWith(RobolectricTestRunner::class)
class SetupActivityTest {

    @Before
    fun setUp() {
        SoundManager.resetForTests()
    }

    @After
    fun tearDown() {
        SoundManager.resetForTests()
    }

    private fun playerRow(activity: SetupActivity, index: Int): View =
        activity.findViewById<LinearLayout>(R.id.playersContainer).getChildAt(index)

    private fun nameField(activity: SetupActivity, index: Int): EditText =
        playerRow(activity, index).findViewById(R.id.editName)

    private fun figureButton(activity: SetupActivity, player: Int, figure: Int): TextView =
        playerRow(activity, player).findViewById<LinearLayout>(R.id.figureBar).getChildAt(figure) as TextView

    private fun selectedFigure(activity: SetupActivity, player: Int): String {
        val bar = playerRow(activity, player).findViewById<LinearLayout>(R.id.figureBar)
        for (i in 0 until bar.childCount) {
            val t = bar.getChildAt(i) as TextView
            if (t.isSelected) return t.text.toString()
        }
        return ""
    }

    @Test
    fun defaultsAreTwoPlayersWithDistinctFiguresAndDefaultSettings() {
        val activity = launch(SetupActivity::class.java).get()
        assertTrue(button(activity, R.id.btnCount2).isSelected)
        assertEquals(View.VISIBLE, playerRow(activity, 0).visibility)
        assertEquals(View.VISIBLE, playerRow(activity, 1).visibility)
        assertEquals(View.GONE, playerRow(activity, 2).visibility)
        assertEquals(View.GONE, playerRow(activity, 3).visibility)

        assertEquals("Spieler 1", nameField(activity, 0).text.toString())
        assertEquals("Spieler 2", nameField(activity, 1).text.toString())
        assertEquals(Figures.ALL[0], selectedFigure(activity, 0))
        assertEquals(Figures.ALL[1], selectedFigure(activity, 1))

        assertEquals("100", activity.findViewById<EditText>(R.id.editRange).text.toString())
        assertEquals("30", activity.findViewById<EditText>(R.id.editSeconds).text.toString())
        assertEquals("30", activity.findViewById<EditText>(R.id.editFields).text.toString())
        assertEquals("1", activity.findViewById<EditText>(R.id.editStepsPlus).text.toString())
        assertEquals("2", activity.findViewById<EditText>(R.id.editStepsMinus).text.toString())
        assertEquals("3", activity.findViewById<EditText>(R.id.editStepsTimes).text.toString())
        assertEquals("4", activity.findViewById<EditText>(R.id.editStepsDivide).text.toString())
    }

    @Test
    fun playerCountShowsAndHidesRows() {
        val activity = launch(SetupActivity::class.java).get()
        click(activity, R.id.btnCount4)
        for (i in 0 until 4) assertEquals(View.VISIBLE, playerRow(activity, i).visibility)
        assertTrue(button(activity, R.id.btnCount4).isSelected)
        assertFalse(button(activity, R.id.btnCount2).isSelected)

        click(activity, R.id.btnCount1)
        assertEquals(View.VISIBLE, playerRow(activity, 0).visibility)
        for (i in 1 until 4) assertEquals(View.GONE, playerRow(activity, i).visibility)
    }

    @Test
    fun choosingAnotherPlayersFigureSwapsFigures() {
        val activity = launch(SetupActivity::class.java).get()
        // Spieler 1 nimmt die Figur von Spieler 2 -> Spieler 2 bekommt die alte Figur von Spieler 1
        figureButton(activity, 0, 1).performClick()
        assertEquals(Figures.ALL[1], selectedFigure(activity, 0))
        assertEquals(Figures.ALL[0], selectedFigure(activity, 1))

        // Freie Figur wählen
        figureButton(activity, 1, 7).performClick()
        assertEquals(Figures.ALL[7], selectedFigure(activity, 1))
        assertEquals(Figures.ALL[1], selectedFigure(activity, 0))
        // Belegte Figuren werden bei den anderen Spielern abgedunkelt
        assertEquals(0.3f, figureButton(activity, 0, 7).alpha)
        assertEquals(1f, figureButton(activity, 0, 5).alpha)
    }

    @Test
    fun startPassesConfigurationToGameAndPersistsIt() {
        val activity = launch(SetupActivity::class.java).get()
        click(activity, R.id.btnCount3)
        nameField(activity, 0).setText("Anna")
        nameField(activity, 1).setText("Ben")
        nameField(activity, 2).setText("   ") // leer -> Standardname
        figureButton(activity, 2, 9).performClick()

        click(activity, R.id.chipRange50)
        click(activity, R.id.chipTime10)
        activity.findViewById<EditText>(R.id.editFields).setText("12")
        activity.findViewById<EditText>(R.id.editStepsPlus).setText("2")
        activity.findViewById<EditText>(R.id.editStepsMinus).setText("3")
        activity.findViewById<EditText>(R.id.editStepsTimes).setText("4")
        activity.findViewById<EditText>(R.id.editStepsDivide).setText("5")

        click(activity, R.id.btnStart)
        val intent = assertNextActivity(activity, GameActivity::class.java)

        assertEquals(listOf("Anna", "Ben", "Spieler 3"), intent.getStringArrayListExtra(GameActivity.EXTRA_NAMES))
        assertEquals(
            listOf(Figures.ALL[0], Figures.ALL[1], Figures.ALL[9]),
            intent.getStringArrayListExtra(GameActivity.EXTRA_FIGURES),
        )
        assertEquals(50, intent.getIntExtra(GameActivity.EXTRA_RANGE, -1))
        assertEquals(10, intent.getIntExtra(GameActivity.EXTRA_SECONDS, -1))
        assertEquals(12, intent.getIntExtra(GameActivity.EXTRA_FIELDS, -1))
        assertEquals(listOf(2, 3, 4, 5), intent.getIntArrayExtra(GameActivity.EXTRA_STEPS)!!.toList())

        // Einstellungen wurden gespeichert
        val ctx = RuntimeEnvironment.getApplication()
        val saved = GameSettings.load(ctx)
        assertEquals(50, saved.numberRange)
        assertEquals(10, saved.secondsPerTask)
        assertEquals(12, saved.boardFields)
        assertEquals(5, saved.stepsDivide)
        val prefs = ctx.getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE)
        assertEquals(3, prefs.getInt(GameSettings.KEY_PLAYER_COUNT, 0))
        assertEquals("Anna", prefs.getString(GameSettings.KEY_PLAYER_NAME + 0, null))
        assertEquals(Figures.ALL[9], prefs.getString(GameSettings.KEY_PLAYER_FIGURE + 2, null))

        // Beim nächsten Öffnen sind Spieler und Werte wieder da
        val again = launch(SetupActivity::class.java).get()
        assertTrue(button(again, R.id.btnCount3).isSelected)
        assertEquals("Anna", nameField(again, 0).text.toString())
        assertEquals(Figures.ALL[9], selectedFigure(again, 2))
        assertEquals("50", again.findViewById<EditText>(R.id.editRange).text.toString())
    }

    @Test
    fun operationsCanBeDeselectedAndArePersisted() {
        val activity = launch(SetupActivity::class.java).get()
        val checkMinus = activity.findViewById<android.widget.CheckBox>(R.id.checkMinus)
        val checkTimes = activity.findViewById<android.widget.CheckBox>(R.id.checkTimes)
        assertTrue(checkMinus.isChecked && checkTimes.isChecked)
        checkMinus.performClick()
        checkTimes.performClick()
        assertFalse(checkMinus.isChecked)
        assertFalse(activity.findViewById<EditText>(R.id.editStepsMinus).isEnabled)
        assertTrue(activity.findViewById<EditText>(R.id.editStepsPlus).isEnabled)

        click(activity, R.id.btnStart)
        val intent = assertNextActivity(activity, GameActivity::class.java)
        assertEquals(0b1001, intent.getIntExtra(GameActivity.EXTRA_OPERATIONS, -1))
        assertEquals(setOf(Operation.PLUS, Operation.DIVIDE), GameSettings.load(RuntimeEnvironment.getApplication()).enabledOperations)

        val again = launch(SetupActivity::class.java).get()
        assertFalse(again.findViewById<android.widget.CheckBox>(R.id.checkMinus).isChecked)
        assertTrue(again.findViewById<android.widget.CheckBox>(R.id.checkDivide).isChecked)
    }

    @Test
    fun atLeastOneOperationIsRequired() {
        val activity = launch(SetupActivity::class.java).get()
        for (id in listOf(R.id.checkPlus, R.id.checkMinus, R.id.checkTimes, R.id.checkDivide)) {
            activity.findViewById<android.widget.CheckBox>(id).performClick()
        }
        click(activity, R.id.btnStart)
        assertEquals(null, org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals("Bitte mindestens eine Rechenart auswählen.", org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun invalidInputsAreSanitized() {
        val activity = launch(SetupActivity::class.java).get()
        activity.findViewById<EditText>(R.id.editRange).setText("")
        activity.findViewById<EditText>(R.id.editSeconds).setText("0")
        activity.findViewById<EditText>(R.id.editFields).setText("2")
        activity.findViewById<EditText>(R.id.editStepsPlus).setText("0")
        click(activity, R.id.btnStart)
        val intent = assertNextActivity(activity, GameActivity::class.java)
        assertEquals(100, intent.getIntExtra(GameActivity.EXTRA_RANGE, -1)) // leer -> Standard
        assertEquals(3, intent.getIntExtra(GameActivity.EXTRA_SECONDS, -1))  // Minimum
        assertEquals(6, intent.getIntExtra(GameActivity.EXTRA_FIELDS, -1))   // Minimum
        assertEquals(1, intent.getIntArrayExtra(GameActivity.EXTRA_STEPS)!![0])
    }
}
