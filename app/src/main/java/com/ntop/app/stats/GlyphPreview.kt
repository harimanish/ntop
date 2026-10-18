package com.ntop.app.stats

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixObject

// ---------------------------------------------------------------------------
// In-app mirror of the toy's render pipeline, for the GLYPH tab.
//
// The kit builds the exact frame the service would push — same renderer
// classes, same builder calls — and hands back the raw int[] that
// setMatrixFrame() would receive, so what the tab draws on screen is what the
// hardware received, not an artist's impression of it.
//
// render() is pure bitmap math (no binder), so it is safe on the UI thread;
// the arrays are matrix^2 ints, ~2.5 KB at 25x25.
// ---------------------------------------------------------------------------

private const val TAG = "ntop.GlyphPreview"

/** Matrix edge length (25 on Phone 3, 13 on Phone (4a) Pro); 0 when absent. */
fun glyphMatrixLength(): Int = try {
    Common.getDeviceMatrixLength()
} catch (_: Exception) {
    0
}

class GlyphPreviewKit(private val context: Context, private val matrix: Int) {
    private val appContext = context.applicationContext
    private val smiley = SmileyRenderer(matrix)
    private val bars = WaveformBars(matrix)
    private val synth = WaveformSynth(if (matrix >= 25) 11 else 7)
    private val barCount = if (matrix >= 25) 11 else 7

    private var marquee: UsernameMarquee? = null
    private var marqueeFor: String? = null
    private var lastProbe = 0L

    /** All-dark frame, shared so an OFF preview never re-allocates. */
    private val dark = IntArray(matrix * matrix)

    /**
     * One frame of [mode] as the brightness array the matrix would get; a null
     * mode (no mapping) renders dark. [step] drives the animated renderers,
     * [watts] is drawn only when non-null (the toy gates it on charging).
     */
    fun frame(
        mode: GlyphMode?,
        step: Int,
        username: String,
        pct: Int,
        watts: Float?,
        playing: Boolean,
    ): IntArray {
        if (mode == null) return dark
        return try {
            val builder = GlyphMatrixFrame.Builder()
            when (mode) {
                GlyphMode.SMILEY -> builder.addTop(smiley.render(step, BRIGHT_ON))

                GlyphMode.USERNAME -> {
                    val name = username.trim().ifEmpty { "ntop" }
                    var mm = marquee
                    if (mm == null || marqueeFor != name) {
                        // MONOSPACE, exactly like the service — the preview has
                        // to advance differently-cased names in the same font.
                        mm = UsernameMarquee(matrix, name, Typeface.MONOSPACE)
                        marquee = mm
                        marqueeFor = name
                    }
                    builder.addTop(mm.renderAt(step))
                }

                GlyphMode.WAVEFORM -> {
                    // Session-0 output-mix capture (the Glyph Beat route) when
                    // the Visualizer attaches; synthetic bars only as a
                    // fallback on devices where it cannot.
                    val real = if (AudioCapture.ensureStarted()) {
                        AudioCapture.bars(barCount)
                    } else {
                        null
                    }
                    builder.addTop(bars.render(real ?: synth.update(playing), BRIGHT_ON))
                }

                GlyphMode.WATTS -> {
                    val pctText = pct.coerceAtLeast(0).toString()
                    val wattsText = watts?.let { formatWatts(it) }
                    if (wattsText != null) {
                        val topY: Int
                        val lowY: Int
                        if (matrix >= 25) {
                            // Mirrors GlyphToyService.renderWatts: two 6-row
                            // lines with a 1px gap, centred in the matrix.
                            topY = (matrix - (GLYPH_ROWS * 2 + 1)) / 2
                            lowY = topY + GLYPH_ROWS + 1
                        } else {
                            topY = 0
                            lowY = GLYPH_ROWS + 1
                        }
                        builder.addTop(text(wattsText, glyphCenterX(wattsText, matrix), topY))
                        builder.addLow(text(pctText, glyphCenterX(pctText, matrix), lowY))
                    } else {
                        val y = (matrix - GLYPH_ROWS) / 2
                        builder.addTop(text(pctText, glyphCenterX(pctText, matrix), y))
                    }
                }
            }
            builder.build(appContext).render().also { out ->
                if (SystemClock.elapsedRealtime() - lastProbe > 1000L) {
                    lastProbe = SystemClock.elapsedRealtime()
                    var lit = 0
                    var max = 0
                    for (v in out) {
                        if (v != 0) lit++
                        if (v > max) max = v
                    }
                    Log.i(
                        TAG,
                        "frame mode=$mode size=${out.size} lit=$lit max=$max expected=${matrix * matrix}",
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "preview render failed", e)
            dark
        }
    }

    private fun text(s: String, x: Int, y: Int): GlyphMatrixObject =
        GlyphMatrixObject.Builder()
            .setText(s)
            .setPosition(x, y)
            .setBrightness(BRIGHT_ON)
            .build()

    /** Same width/unit rule as the service: 25x25 fits "18.4W", 13x13 does not. */
    private fun formatWatts(w: Float): String =
        if (matrix >= 25) "%.1fW".format(java.util.Locale.US, w)
        else "%.0f".format(java.util.Locale.US, w)
}
