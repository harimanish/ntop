package com.ntop.app.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Signals that decide which glyph the toy is showing.
//
//   foregroundApp — needs PACKAGE_USAGE_STATS (declared; user grants it in
//                   Settings). Null when access is off or nothing is resumed.
//   audioPlaying  — AudioManager.activePlaybackConfigurations needs no
//                   permission, so it is safe to poll.
//
// Why there is no Visualizer here: capturing real PCM needs an audio session
// id, and on API 36 the public SDK does not expose one.
// AudioPlaybackConfiguration publishes only getAudioAttributes() — getPlayState()
// and getAudioSessionId() are @SystemApi — and the public MediaSession stub has
// no getSessionId() either. Hidden-API reflection is blocked at targetSdk 36,
// so Visualizer(sessionId) cannot be constructed for another app's playback.
// The waveform below is therefore synthesised, but it is driven by the real
// play/pause signal, so it moves only while audio is actually playing.
// ---------------------------------------------------------------------------

private const val TAG = "ntop.GlyphToy"

/**
 * Most recent package moved to the foreground. Walks usage events back from
 * now and takes the newest ACTIVITY_RESUMED, which stays correct when a
 * package resumes an already-created activity.
 */
fun foregroundApp(context: Context, lookbackMs: Long = 5_000L): String? = try {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = SystemClock.elapsedRealtime()
    val events = usm.queryEvents(now - lookbackMs, now)
    val event = UsageEvents.Event()
    var found: String? = null
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) found = event.packageName
    }
    found
} catch (e: Exception) {
    // SecurityException when Usage Access is off. "Unknown" is the right
    // answer: the toy falls back to the always-on default instead of guessing.
    Log.w(TAG, "foregroundApp unavailable", e)
    null
}
/** True when any app currently has an active audio playback configuration. */
fun audioPlaying(context: Context): Boolean = try {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.activePlaybackConfigurations.isNotEmpty()
} catch (e: Exception) {
    false
}

/**
 * Synthetic waveform bars, 0..100 per bar.
 *
 * Superimposes a few incommensurate sines so the shape never visibly loops,
 * and gates the whole envelope on [playing] so a paused session shows a flat
 * line instead of a fake dancing one.
 */
class WaveformSynth(
    private val barCount: Int,
) {
    private var bars = IntArray(barCount)
    private var tick = 0

    fun update(playing: Boolean): IntArray {
        tick++
        bars = if (!playing) {
            IntArray(barCount)
        } else {
            IntArray(barCount) { i ->
                val phase = i.toDouble() / barCount
                val t = tick * 0.28
                val a = sin(2 * PI * 1.00 * phase + t)
                val b = sin(2 * PI * 2.30 * phase + t * 1.7) * 0.5
                val c = sin(2 * PI * 0.60 * phase + t * 0.6) * 0.3
                val env = abs(a + b + c) / 1.8
                // Envelope swells and fades so it breathes like real audio.
                val swell = 0.55 + 0.45 * sin(t * 0.35)
                ((env * swell) * 100).toInt().coerceIn(0, 100)
            }
        }
        return bars
    }
}
