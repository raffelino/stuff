package com.raffelino.rechenwuerfel

import android.content.Context
import android.widget.Switch
import com.raffelino.rechenwuerfel.TestSupport.click
import com.raffelino.rechenwuerfel.TestSupport.dialogMessage
import com.raffelino.rechenwuerfel.TestSupport.latestDialog
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

/** Hauptmenü: Buttons, Anleitung, Optionen, Navigation zum Setup. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Before
    fun setUp() {
        SoundManager.resetForTests()
    }

    @After
    fun tearDown() {
        SoundManager.resetForTests()
    }

    @Test
    fun menuShowsTitleAndButtons() {
        val activity = launch(MainActivity::class.java).get()
        assertTrue(text(activity, R.id.btnPlay).contains("Spielen"))
        assertTrue(text(activity, R.id.btnRules).contains("Anleitung"))
        assertTrue(text(activity, R.id.btnOptions).contains("Optionen"))
        assertTrue(activity.findViewById<android.view.View>(R.id.btnPlay).isShown)
    }

    @Test
    fun playOpensSetupScreen() {
        val activity = launch(MainActivity::class.java).get()
        click(activity, R.id.btnPlay)
        TestSupport.assertNextActivity(activity, SetupActivity::class.java)
    }

    @Test
    fun rulesDialogExplainsTheGame() {
        val activity = launch(MainActivity::class.java).get()
        click(activity, R.id.btnRules)
        val message = dialogMessage()
        assertTrue(message.contains("Joker"))
        assertTrue(message.contains("Aussetzen"))
        assertTrue(message.contains("Ziel"))
    }

    @Test
    fun optionsDialogTogglesMusicAndSoundAndPersists() {
        val activity = launch(MainActivity::class.java).get()
        assertTrue(SoundManager.musicEnabled)
        assertTrue(SoundManager.sfxEnabled)

        click(activity, R.id.btnOptions)
        val dialog = latestDialog()
        val music = dialog.findViewById<Switch>(R.id.switchMusic)
        val sfx = dialog.findViewById<Switch>(R.id.switchSfx)
        assertTrue(music.isChecked)
        assertTrue(sfx.isChecked)

        music.performClick()
        sfx.performClick()
        assertFalse(SoundManager.musicEnabled)
        assertFalse(SoundManager.sfxEnabled)

        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean(GameSettings.KEY_MUSIC, true))
        assertFalse(prefs.getBoolean(GameSettings.KEY_SFX, true))

        // Erneut öffnen: Zustand bleibt erhalten
        dialog.dismiss()
        click(activity, R.id.btnOptions)
        assertFalse(latestDialog().findViewById<Switch>(R.id.switchMusic).isChecked)
    }

    @Test
    fun soundSettingsSurviveRestart() {
        launch(MainActivity::class.java).get()
        SoundManager.setMusicEnabled(false)
        SoundManager.resetForTests()
        launch(MainActivity::class.java).get()
        assertFalse("Musik-Einstellung muss aus den Preferences geladen werden", SoundManager.musicEnabled)
        assertTrue(SoundManager.sfxEnabled)
    }
}
