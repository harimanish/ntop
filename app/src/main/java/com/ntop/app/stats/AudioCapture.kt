package com.ntop.app.stats

import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Real output-mix audio capture for the WAVEFORM glyph.
//
// The per-app route is closed on modern Android: AudioPlaybackConfiguration
// only exposes AudioAttributes, MediaSession has no public session id, and
// hidden API calls are blocked at targetSdk 36. The supported escape hatch is
// session 0 - the *global output mix* - which the reference documents as
// "the Visualizer will apply to the output mix" and gates on
// MODIFY_AUDIO_SETTINGS, a NORMAL permission granted at install with no
// dialog. Glyph Beat drives its mirrored waveform exactly this way; we take
// the waveform half of the same capture and leave FFT unused for now.
//
// Callbacks arrive on the main looper at up to ~20 Hz; the handler only
// copies the byte array. Peak extraction and smoothing happen on the caller's
// render tick, so the audio callback never does allocation-heavy work.
// ---------------------------------------------------------------------------

private const val TAG = "ntop.AudioCapture"

/** No callback for this long means playback stopped; the bars decay to zero. */
private const val STALE_MS = 700L

/** Per-tick release factor: fast attack (max), gentle fall-off. */
private const val RELEASE = 0.72f

/**
 * Failed inits wait this long before retrying. The render loop calls
 * ensureStarted() ~11x/s, so without a cooldown a permanently refused effect
 * would log a stack trace on every frame.
 */
private const val RETRY_MS = 5_000L

object AudioCapture {

    private val lock = Any()

    @Volatile
    private var visualizer: Visualizer? = null

    @Volatile
    private var waveform: ByteArray? = null

    @Volatile
    private var waveformAt = 0L

    @Volatile
    private var live = false

    /** When the last init attempt ran — failures respect [RETRY_MS]. */
    @Volatile
    private var lastAttempt = 0L

    /** Smoothed bar levels, reused every frame — render loop is single-threaded. */
    private var smoothed = FloatArray(0)

    /** True when the output-mix Visualizer was created successfully. */
    fun isLive(): Boolean = live

    /**
     * Creates the session-0 Visualizer on first use. Idempotent — safe to call
     * every render tick; once init has succeeded or failed this is a no-op.
     * Must run on the main thread: Visualizer delivers callbacks through the
     * main looper.
     */
    fun ensureStarted(): Boolean {
        if (live || visualizer != null) return live
        synchronized(lock) {
            if (live || visualizer != null) return live
            val now = SystemClock.elapsedRealtime()
            if (now - lastAttempt < RETRY_MS) return false
            lastAttempt = now
            var v: Visualizer? = null
            return try {
                v = Visualizer(0)
                val range = Visualizer.getCaptureSizeRange()
                val size = range.lastOrNull()?.takeIf { it > 0 } ?: 1024
                v.setCaptureSize(size)
                v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                // getMaxCaptureRate is milliHertz; clamp to a sane 10-40 Hz.
                val maxRate = Visualizer.getMaxCaptureRate()
                val rate = if (maxRate in 1..40_000) maxRate else 20_000
                v.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (waveform != null) {
                                this@AudioCapture.waveform = waveform.clone()
                                waveformAt = SystemClock.elapsedRealtime()
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) = Unit
                    },
                    rate,
                    true,
                    false,
                )
                if (v.setEnabled(true) != AudioEffect.SUCCESS) {
                    throw IllegalStateException("setEnabled returned an error")
                }
                visualizer = v
                live = true
                Log.i(TAG, "output-mix visualizer on session 0, size=$size rate=${rate / 1000}Hz")
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Visualizer(0) unavailable; waveform keeps the synthetic fallback", t)
                runCatching { v?.release() }
                live = false
                false
            }
        }
    }

    /**
     * Bar heights in the renderer's 0..100 scale, or null when capture is not
     * available (the caller then keeps its synthetic fallback). A stale buffer
     * means playback stopped: the bars decay instead of freezing mid-song.
     */
    fun bars(count: Int): IntArray? {
        if (!live) return null
        if (count <= 0) return IntArray(0)
        if (smoothed.size != count) smoothed = FloatArray(count)
        val out = IntArray(count)
        val w = waveform
        if (w == null || SystemClock.elapsedRealtime() - waveformAt > STALE_MS) {
            for (i in 0 until count) {
                smoothed[i] *= RELEASE
                if (smoothed[i] < 0.02f) smoothed[i] = 0f
                out[i] = (smoothed[i] * 100f).toInt().coerceIn(0, 100)
            }
            return out
        }
        val per = (w.size / count).coerceAtLeast(1)
        for (i in 0 until count) {
            var peak = 0
            val start = i * per
            val end = minOf(start + per, w.size)
            var j = start
            while (j < end) {
                val d = abs((w[j].toInt() and 0xFF) - 128)
                if (d > peak) peak = d
                j++
            }
            // sqrt lifts quiet passages so the matrix still shows shape at
            // low volume without letting loud parts clip at the rim.
            val level = sqrt(peak / 128f).coerceIn(0f, 1f)
            smoothed[i] = maxOf(level, smoothed[i] * RELEASE)
            out[i] = (smoothed[i] * 100f).toInt().coerceIn(0, 100)
        }
        return out
    }

    /**
     * Releases the effect so a toy that stops binding does not keep the audio
     * path powered. The next [ensureStarted] recreates it.
     */
    fun stop() {
        synchronized(lock) {
            runCatching { visualizer?.setEnabled(false) }
            runCatching { visualizer?.release() }
            visualizer = null
            waveform = null
            waveformAt = 0L
            live = false
            lastAttempt = 0L
            smoothed = FloatArray(0)
        }
    }
}
