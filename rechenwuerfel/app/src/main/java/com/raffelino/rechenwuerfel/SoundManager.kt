package com.raffelino.rechenwuerfel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/** Soundeffekte (SoundPool) und Hintergrundmusik (MediaPlayer). */
object SoundManager {

    enum class Sfx(val resId: Int) {
        DICE(R.raw.sfx_dice),
        CORRECT(R.raw.sfx_correct),
        WRONG(R.raw.sfx_wrong),
        STEP(R.raw.sfx_step),
        TIMEOUT(R.raw.sfx_timeout),
        WIN(R.raw.sfx_win),
        JOKER(R.raw.sfx_joker),
        SKIP(R.raw.sfx_skip),
        TICK(R.raw.sfx_tick),
        CLICK(R.raw.sfx_click),
    }

    private var appContext: Context? = null
    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<Sfx, Int>()
    private var music: MediaPlayer? = null
    private var foregroundActivities = 0

    var musicEnabled: Boolean = true
        private set
    var sfxEnabled: Boolean = true
        private set

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE)
        musicEnabled = prefs.getBoolean(GameSettings.KEY_MUSIC, true)
        sfxEnabled = prefs.getBoolean(GameSettings.KEY_SFX, true)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        for (s in Sfx.values()) {
            soundIds[s] = pool.load(ctx, s.resId, 1)
        }
        soundPool = pool
    }

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!sfxEnabled) return
        val id = soundIds[sfx] ?: return
        soundPool?.play(id, volume, volume, 1, 0, 1f)
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        appContext?.getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(GameSettings.KEY_MUSIC, enabled)?.apply()
        if (enabled && foregroundActivities > 0) startMusic() else stopMusic()
    }

    fun setSfxEnabled(enabled: Boolean) {
        sfxEnabled = enabled
        appContext?.getSharedPreferences(GameSettings.PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(GameSettings.KEY_SFX, enabled)?.apply()
    }

    /** Wird in Activity.onStart aufgerufen. */
    fun activityStarted() {
        foregroundActivities++
        if (musicEnabled) startMusic()
    }

    /** Wird in Activity.onStop aufgerufen. */
    fun activityStopped() {
        foregroundActivities--
        if (foregroundActivities <= 0) {
            foregroundActivities = 0
            pauseMusic()
        }
    }

    private fun startMusic() {
        val ctx = appContext ?: return
        val m = music ?: MediaPlayer.create(ctx, R.raw.music_loop)?.also {
            it.isLooping = true
            it.setVolume(0.45f, 0.45f)
            music = it
        } ?: return
        if (!m.isPlaying) {
            try { m.start() } catch (_: IllegalStateException) { }
        }
    }

    private fun pauseMusic() {
        val m = music ?: return
        try { if (m.isPlaying) m.pause() } catch (_: IllegalStateException) { }
    }

    private fun stopMusic() {
        pauseMusic()
        try { music?.seekTo(0) } catch (_: IllegalStateException) { }
    }
}
