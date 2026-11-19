package com.flashcards.german

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

enum class Sound(val res: Int) {
    Correct(R.raw.sfx_correct), Wrong(R.raw.sfx_wrong), Done(R.raw.sfx_done),
}

/** Short UI sound effects. Low latency via SoundPool; follows the phone's silent mode. */
object Sfx {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Sound, Int>()
    private var prefs: android.content.SharedPreferences? = null
    var enabled: Boolean = true
        private set

    fun init(ctx: Context) {
        if (pool != null) return
        prefs = ctx.getSharedPreferences("sfx", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean("enabled", true) ?: true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build().also { p ->
            Sound.entries.forEach { ids[it] = p.load(ctx, it.res, 1) }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun play(s: Sound) {
        if (!enabled) return
        val id = ids[s] ?: return
        pool?.play(id, 1f, 1f, 1, 0, 1f)
    }
}
