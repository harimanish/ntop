package com.ntop.app.stats

import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.pow
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
// dialog. Glyph Beat drives its mirrored visualizer exactly this way: its
// bars ride the FFT capture (see [spectrum]), so both halves of the callback
// are kept - FFT for the spectrum styles, samples for the WAVE style.
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

    /** Latest FFT capture (real/imag pairs) and when it arrived. */
    @Volatile
    private var fft: ByteArray? = null

    @Volatile
    private var fftAt = 0L

    @Volatile
    private var live = false

    /** Capture size in bytes and the stream's sampling rate in milliHertz. */
    @Volatile
    private var captureSize = 1024

    @Volatile
    private var sampleRateMhz = 48_000_000

    /** When the last init attempt ran — failures respect [RETRY_MS]. */
    @Volatile
    private var lastAttempt = 0L

    /** Smoothed bar levels, reused every frame — render loop is single-threaded. */
    private var smoothed = FloatArray(0)

    /** Smoothed spectrum bands, same contract as [smoothed]. */
    private var specSmoothed = FloatArray(0)

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
                captureSize = size
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
                        ) {
                            if (samplingRate > 0) sampleRateMhz = samplingRate
                            if (fft != null) {
                                this@AudioCapture.fft = fft.clone()
                                fftAt = SystemClock.elapsedRealtime()
                            }
                        }
                    },
                    rate,
                    true,
                    true,
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
     *
     * [release] is the per-tick fall factor (higher = smoother), [gain] the
     * user's sensitivity multiplier.
     */
    fun bars(count: Int, release: Float = RELEASE, gain: Float = 1f): IntArray? {
        if (!live) return null
        if (count <= 0) return IntArray(0)
        if (smoothed.size != count) smoothed = FloatArray(count)
        val out = IntArray(count)
        val w = waveform
        if (w == null || SystemClock.elapsedRealtime() - waveformAt > STALE_MS) {
            for (i in 0 until count) {
                smoothed[i] *= release
                if (smoothed[i] < 0.02f) smoothed[i] = 0f
                out[i] = (smoothed[i] * 100f * gain).toInt().coerceIn(0, 100)
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
            smoothed[i] = maxOf(level, smoothed[i] * release)
            out[i] = (smoothed[i] * 100f * gain).toInt().coerceIn(0, 100)
        }
        return out
    }

    /**
     * Output-mix FFT -> [count] log-spaced bands on the renderer's 0..100
     * scale, or null when capture is unavailable (the caller keeps its
     * synthetic fallback). This is the data behind the spectrum styles — the
     * Glyph Beat mirrored visualizer; [bars] stays for the sample path (WAVE).
     *
     * [release] is the shared smoothing knob. Spectra glide both ways, so here
     * it maps to a symmetric EMA (a high value reads as a slow, soft V).
     */
    fun spectrum(count: Int, release: Float, gain: Float): IntArray? {
        if (!live) return null
        if (count <= 0) return IntArray(0)
        if (specSmoothed.size != count) specSmoothed = FloatArray(count)
        val out = IntArray(count)
        val f = fft
        if (f == null || SystemClock.elapsedRealtime() - fftAt > STALE_MS) {
            for (i in 0 until count) {
                specSmoothed[i] *= release
                if (specSmoothed[i] < 0.02f) specSmoothed[i] = 0f
                out[i] = (specSmoothed[i] * 100f * gain).toInt().coerceIn(0, 100)
            }
            return out
        }
        // Visualizer FFT layout: fft[0] = real(DC), fft[1] = real(Nyquist),
        // then real/imag pairs for bins 1..size/2-1; bin k is at k*sr/size Hz.
        val size = minOf(captureSize, f.size).coerceAtLeast(4)
        val half = size / 2
        val sr = (sampleRateMhz / 1000f).takeIf { it >= 8_000f } ?: 48_000f
        val fMin = 45f
        val fMax = minOf(16_000f, sr * 0.47f)
        val ratio = fMax / fMin
        // High smoothing -> small steps: 0.5 jumps, 0.95 glides.
        val k = (1f - release).coerceIn(0.05f, 0.5f) * 0.8f
        for (b in 0 until count) {
            val lo = fMin * ratio.pow(b.toFloat() / count)
            val hi = fMin * ratio.pow((b + 1f) / count)
            val k0 = (lo * size / sr).toInt().coerceIn(1, half - 1)
            val k1 = (hi * size / sr).toInt().coerceIn(k0, half - 1)
            // Mean of the band's bins, not the peak: the peak pinned every
            // loud low band to full scale (a flat plateau), while the mean
            // keeps the descending bass-to-treble shape the mirror turns
            // into the Glyph Beat hump. Log bands are wide at the top, so
            // the mean is also what tames the treble tail.
            var sum = 0f
            var bins = 0
            for (bin in k0..k1) {
                val re = f[2 * bin].toInt() and 0xFF
                val im = f[2 * bin + 1].toInt() and 0xFF
                sum += sqrt((re * re + im * im).toFloat())
                bins++
            }
            val mag = if (bins > 0) sum / bins else 0f
            // Direct magnitude ratio: the FFT means of loud bands sit near
            // full scale, so the sample path's sqrt lifted everything into a
            // flat blob. Sensitivity stays the user's make-up gain.
            val level = (mag / 255f).coerceIn(0f, 1f) * 100f
            specSmoothed[b] += (level - specSmoothed[b]) * k
            out[b] = (specSmoothed[b] * gain).toInt().coerceIn(0, 100)
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
            fft = null
            fftAt = 0L
            live = false
            lastAttempt = 0L
            smoothed = FloatArray(0)
            specSmoothed = FloatArray(0)
        }
    }
}
