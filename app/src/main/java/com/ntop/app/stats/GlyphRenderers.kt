package com.ntop.app.stats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import kotlin.math.sin
import com.nothing.ketchum.GlyphMatrixObject

// ---------------------------------------------------------------------------
// Bitmap renderers for the Glyph Toy.
//
// The SDK's text path only understands the bundled NDot55 font (a-z, 0-9 and
// nine symbols), so a username like "@man" or "n_t" cannot be drawn with
// setText(). Bitmaps go through setImageSource(), which render() hands to
// GlyphMatrixUtils.convertToGlyphMatrix — and that path *does* honour scale,
// unlike the text path.
//
// convertToGlyphMatrix throws on a non-square bitmap, so every renderer below
// produces a matrix-sized square and draws into it.
// ---------------------------------------------------------------------------

private const val GLYPH_H = 6

/**
 * Scrolling marquee. Renders [text] once into a wide bitmap, then each call
 * blits a matrix-wide window at [offsetPx] so the name slides continuously and
 * loops. Any character works because this is the app's own font, not NDot55.
 */
class UsernameMarquee(
    private val matrix: Int,
    text: String,
    private val typeface: Typeface,
) {
    /** Width in px of the full rendered string, including the gap for looping. */
    private val stripWidth: Int
    private val strip: Bitmap
    private val window = Bitmap.createBitmap(matrix, matrix, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(window)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }

    init {
        paint.textSize = matrix.toFloat() * 0.62f
        val gap = (matrix * 0.5f).toInt()
        val measured = paint.measureText(text)
        stripWidth = (measured + gap).toInt().coerceAtLeast(1)
        strip = Bitmap.createBitmap(stripWidth, matrix, Bitmap.Config.ARGB_8888)
        Canvas(strip).drawText(text, 0f, paint.textSize * 0.82f, paint)
    }

    /** True when the text is narrow enough to sit still instead of scrolling. */
    fun fits(): Boolean = stripWidth <= matrix

    fun renderAt(offsetPx: Int): GlyphMatrixObject {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val off = if (fits()) {
            (matrix - stripWidth) / 2
        } else {
            offsetPx.coerceAtLeast(0)
        }
        canvas.drawBitmap(strip, -off.toFloat(), 0f, null)
        return GlyphMatrixObject.Builder()
            .setImageSource(window)
            .setPosition(0, 0)
            .setBrightness(BRIGHT_ON)
            .build()
    }
}

/**
 * Vertical bar waveform. Each bar is centred, so the shape reads as symmetric
 * around the horizontal midline like the built-in Glyph Beat toy.
 */
class WaveformBars(
    private val matrix: Int,
) {
    private val window = Bitmap.createBitmap(matrix, matrix, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(window)
    private val paint = Paint().apply { color = Color.WHITE }

    private val barWidth: Int = (matrix / 16f).toInt().coerceAtLeast(1)
    private val gap: Int = (barWidth * 0.6f).toInt().coerceAtLeast(1)
    private val centreY: Float = matrix / 2f

    fun render(bars: IntArray, brightness: Int = BRIGHT_ON): GlyphMatrixObject {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val pitch = barWidth + gap
        val total = bars.size * pitch - gap
        var x = (matrix - total) / 2
        for (v in bars) {
            val h = ((v.coerceIn(0, 100) / 100f) * (matrix / 2f - 1f)).toInt().coerceAtLeast(1)
            canvas.drawRect(
                x.toFloat(),
                centreY - h,
                (x + barWidth).toFloat(),
                centreY + h,
                paint,
            )
            x += pitch
        }
        return GlyphMatrixObject.Builder()
            .setImageSource(window)
            .setPosition(0, 0)
            .setBrightness(brightness)
            .build()
    }
}

/** Font advance widths in matrix pixels, mirroring the SDK's letter maps. */

/**
 * Animated smiley: outlined face that bobs gently, blinks on a slow cycle and
 * alternates between a closed smile and an open mouth. Drawn as vector shapes
 * into a reusable square bitmap, so it stays crisp at 25x25 and 13x13 alike.
 */
class SmileyRenderer(
    private val matrix: Int,
) {
    private val window = Bitmap.createBitmap(matrix, matrix, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(window)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = (matrix * 0.06f).coerceAtLeast(1.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val mouth = RectF()

    fun render(tick: Int, brightness: Int): GlyphMatrixObject {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val c = matrix / 2f
        val r = matrix * 0.36f
        // Gentle vertical drift so the face is never completely static.
        val bob = sin(tick * 0.09f) * matrix * 0.018f
        val cy = c + bob

        canvas.drawCircle(c, cy, r, stroke)

        // Blink for a few ticks roughly every 12 seconds.
        val blinking = (tick % 135) < 5
        val eyeDx = r * 0.38f
        val eyeY = cy - r * 0.28f
        val eyeR = (r * 0.14f).coerceAtLeast(1.2f)
        for (side in intArrayOf(-1, 1)) {
            val ex = c + side * eyeDx
            if (blinking) {
                canvas.drawLine(ex - eyeR, eyeY, ex + eyeR, eyeY, stroke)
            } else {
                canvas.drawCircle(ex, eyeY, eyeR, fill)
            }
        }

        // Mouth alternates every ~4s: arc-only smile, then a filled open mouth.
        val mouthY = cy + r * 0.10f
        val mw = r * 0.55f
        val open = (tick / 40) % 2 == 0
        if (open) {
            mouth.set(c - mw, mouthY - r * 0.24f, c + mw, mouthY + r * 0.30f)
            canvas.drawArc(mouth, 0f, 180f, true, fill)
        } else {
            mouth.set(c - mw, mouthY - r * 0.12f, c + mw, mouthY + r * 0.46f)
            canvas.drawArc(mouth, 0f, 180f, false, stroke)
        }

        return GlyphMatrixObject.Builder()
            .setImageSource(window)
            .setPosition(0, 0)
            .setBrightness(brightness)
            .build()
    }
}
private const val W_DIGIT = 4
private const val W_DOT = 1
private const val W_WIDE = 5

fun glyphTextWidth(s: String): Int =
    s.sumOf { c ->
        when {
            c == '.' -> W_DOT
            c == 'w' || c == 'W' || c == '+' -> W_WIDE
            else -> W_DIGIT
        }
    } + (s.length - 1).coerceAtLeast(0)

fun glyphCenterX(text: String, matrix: Int): Int =
    ((matrix - glyphTextWidth(text)) / 2).coerceAtLeast(0)

const val BRIGHT_ON = 255
// 160, not 90: the AOD panel is already heavily dimmed by the display itself,
// so a 35%-of-full frame is effectively invisible. Still clearly dimmer awake.
const val BRIGHT_AOD = 160

/** Glyph rows are 6 tall; used to place the two-line watts layout. */
internal val GLYPH_ROWS = GLYPH_H
