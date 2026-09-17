package com.raffelino.rechenwuerfel

import android.app.Activity
import android.os.Bundle

/** Kümmert sich um Start/Stopp der Hintergrundmusik über alle Bildschirme hinweg. */
abstract class BaseActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
    }

    override fun onStart() {
        super.onStart()
        SoundManager.activityStarted()
    }

    override fun onStop() {
        SoundManager.activityStopped()
        super.onStop()
    }

    protected fun click() = SoundManager.play(SoundManager.Sfx.CLICK, 0.7f)
}
