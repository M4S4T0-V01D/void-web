package dev.m4s4t0.voidweb

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * The few interface sounds (art/make_sounds.py), preloaded so they play the instant something happens.
 * Quiet when the phone is on silent or vibrate, and switchable in Settings.
 */
object Sounds {
    enum class Kind(val raw: Int, val volume: Float) {
        OPEN(R.raw.web_open, 0.5f),
        TAB_OPEN(R.raw.tab_open, 0.35f),
        TAB_CLOSE(R.raw.tab_close, 0.4f),
        DONE(R.raw.done_chime, 0.5f),
    }

    private var pool: SoundPool? = null
    private val ids = HashMap<Kind, Int>()
    private val loaded = HashSet<Int>()
    private var pending: Kind? = null
    private lateinit var app: Context

    fun init(ctx: Context) {
        if (pool != null) return
        app = ctx.applicationContext
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        // The opening sound is requested before it has finished loading; play it once it has.
        p.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded += id
            pending?.let { k -> if (ids[k] == id) { pending = null; play(k) } }
        }
        for (k in Kind.entries) ids[k] = p.load(app, k.raw, 1)
        pool = p
    }

    fun play(kind: Kind) {
        if (!Prefs.sounds || !::app.isInitialized) return
        val am = app.getSystemService(AudioManager::class.java)
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[kind] ?: return
        if (id !in loaded) {
            pending = kind
            return
        }
        pool?.play(id, kind.volume, kind.volume, 1, 0, 1f)
    }
}
