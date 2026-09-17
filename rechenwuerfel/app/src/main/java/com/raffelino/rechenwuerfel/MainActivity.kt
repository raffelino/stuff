package com.raffelino.rechenwuerfel

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Switch

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            click()
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<Button>(R.id.btnRules).setOnClickListener {
            click()
            showRules()
        }
        findViewById<Button>(R.id.btnOptions).setOnClickListener {
            click()
            showOptions()
        }
    }

    private fun showRules() {
        AlertDialog.Builder(this)
            .setTitle(R.string.rules_title)
            .setMessage(R.string.rules_text)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showOptions() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_options, null)
        val music = view.findViewById<Switch>(R.id.switchMusic)
        val sfx = view.findViewById<Switch>(R.id.switchSfx)
        music.isChecked = SoundManager.musicEnabled
        sfx.isChecked = SoundManager.sfxEnabled
        music.setOnCheckedChangeListener { _, checked -> SoundManager.setMusicEnabled(checked) }
        sfx.setOnCheckedChangeListener { _, checked ->
            SoundManager.setSfxEnabled(checked)
            if (checked) click()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.options_title)
            .setView(view)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
