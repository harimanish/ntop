package com.bathealth.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bathealth.app.ui.theme.NothingRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Phone (3) homage, hand-drawn in Canvas after the About-phone render:
// transparent white back, triple cameras, flash disc, dot-matrix Glyph
// circle, red marker square, etched arc, seams and screws.
//
// The Glyph waveform is LIVE: dot amplitude follows `level`, so the render
// reacts to the battery like a real Glyph Matrix. Pure vector — crisp at
// any size, zero binary assets.
// ---------------------------------------------------------------------------
@Composable
fun Phone3Render(level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset.Zero, size),
                    cornerRadius = CornerRadius(w * 0.11f, w * 0.11f),
                ),
            )
        }

        // Body: light transparent-back gradient + dark edge.
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF5F5F3), Color(0xFFDCDCD9), Color(0xFFE8E8E6)),
            ),
        )
        drawPath(path = body, color = Color(0xFF2A2A2C), style = Stroke(width = w * 0.012f))

        // Inner details are placed inside the body by construction (no clip
        // needed): keep strokes within the rounded frame on every size.
        run {
            // Panel seams.
            seam(Offset(w * 0.38f, h * 0.36f), Offset(w * 0.38f, h * 0.97f), w)
            seam(Offset(w * 0.06f, h * 0.36f), Offset(w * 0.94f, h * 0.36f), w)
            seam(Offset(w * 0.06f, h * 0.62f), Offset(w * 0.94f, h * 0.62f), w)
            // Etched concentric arc, right side, fully inside the frame.
            drawArc(
                color = Color(0xFF9A9A96).copy(alpha = 0.55f),
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.24f, h * 0.30f),
                size = Size(w * 0.68f, w * 0.68f),
                style = Stroke(width = w * 0.006f),
            )
            // Lenses: main top-left, twin mids.
            lens(Offset(w * 0.24f, h * 0.115f), w * 0.125f, w)
            lens(Offset(w * 0.24f, h * 0.285f), w * 0.105f, w)
            lens(Offset(w * 0.62f, h * 0.285f), w * 0.105f, w)
            // Flash disc.
            flash(Offset(w * 0.55f, h * 0.115f), w * 0.105f, w)
            // Glyph Matrix with live waveform.
            glyphMatrix(Offset(w * 0.82f, h * 0.115f), w * 0.115f, level.coerceIn(0, 100), w)
            // Red marker square between the mid lenses.
            drawRect(
                color = NothingRed,
                topLeft = Offset(w * 0.415f, h * 0.272f),
                size = Size(w * 0.045f, w * 0.045f),
            )
            // Screws.
            screw(Offset(w * 0.09f, h * 0.035f), w)
            screw(Offset(w * 0.91f, h * 0.035f), w)
            screw(Offset(w * 0.09f, h * 0.965f), w)
            screw(Offset(w * 0.91f, h * 0.965f), w)
            screw(Offset(w * 0.09f, h * 0.62f), w)
            screw(Offset(w * 0.91f, h * 0.62f), w)
            // Bottom vent ticks.
            repeat(3) { i ->
                drawLine(
                    color = Color(0xFF9A9A96),
                    start = Offset(w * (0.40f + i * 0.07f), h * 0.93f),
                    end = Offset(w * (0.44f + i * 0.07f), h * 0.93f),
                    strokeWidth = w * 0.008f,
                )
            }
        }
    }
}

private fun DrawScope.seam(from: Offset, to: Offset, w: Float) {
    drawLine(
        color = Color(0xFF9A9A96).copy(alpha = 0.7f),
        start = from,
        end = to,
        strokeWidth = w * 0.006f,
    )
}

private fun DrawScope.lens(center: Offset, r: Float, w: Float) {
    // Housing ring.
    drawCircle(color = Color(0xFF3A3A3C), radius = r, center = center)
    drawCircle(
        color = Color(0xFF0B0B0C),
        radius = r * 0.82f,
        center = center,
    )
    // Glass: dark radial with blue core + glint.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2B3A55), Color(0xFF050507)),
            center = center + Offset(-r * 0.2f, -r * 0.2f),
            radius = r,
        ),
        radius = r * 0.62f,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = r * 0.10f,
        center = center + Offset(-r * 0.28f, -r * 0.30f),
    )
    drawArc(
        color = Color.White.copy(alpha = 0.35f),
        startAngle = 140f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(center.x - r * 0.82f, center.y - r * 0.82f),
        size = Size(r * 1.64f, r * 1.64f),
        style = Stroke(width = w * 0.008f),
    )
}

private fun DrawScope.flash(center: Offset, r: Float, w: Float) {
    drawCircle(color = Color.White, radius = r, center = center)
    drawCircle(color = Color(0xFFB9B9B6), radius = r, center = center, style = Stroke(width = w * 0.008f))
    drawCircle(color = Color(0xFFE2E2DE), radius = r * 0.35f, center = center)
}

private fun DrawScope.glyphMatrix(center: Offset, r: Float, level: Int, w: Float) {
    drawCircle(color = Color.Black, radius = r, center = center)
    drawCircle(
        color = Color(0xFF3A3A3C),
        radius = r,
        center = center,
        style = Stroke(width = w * 0.008f),
    )
    // Dot-matrix waveform inside the disc; dots outside the disc radius are
    // skipped by distance check (no clip needed). Amplitude follows charge.
    val frac = level / 100f
    val cols = 9
    val rows = 7
    val dotR = r / 16f
    for (i in 0 until cols) {
        val fx = i / (cols - 1f) * 2f - 1f // -1..1 across the disc
        val env = cos(fx * PI / 2).toFloat() // taper at disc edges
        val wave = 0.6f + 0.4f * sin(i * 1.3f)
        val amp = env * (0.5f + 2.4f * frac) * wave
        for (j in 0 until rows) {
            val fy = j / (rows - 1f) * 2f - 1f // -1..1 vertical
            val dx = fx * 0.78f
            val dy = fy * 0.78f
            if (dx * dx + dy * dy > 0.86f * 0.86f) continue
            val lit = abs(fy) <= abs(amp) * 0.55f
            drawCircle(
                color = if (lit) Color.White else Color.White.copy(alpha = 0.14f),
                radius = dotR,
                center = Offset(
                    center.x + dx * r,
                    center.y + dy * r,
                ),
            )
        }
    }
}

private fun DrawScope.screw(center: Offset, w: Float) {
    val r = w * 0.020f
    drawCircle(color = Color(0xFFB9B9B6), radius = r, center = center)
    drawCircle(color = Color(0xFF6E6E6A), radius = r, center = center, style = Stroke(width = w * 0.005f))
    drawLine(
        color = Color(0xFF55554F),
        start = center + Offset(-r * 0.6f, 0f),
        end = center + Offset(r * 0.6f, 0f),
        strokeWidth = w * 0.005f,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PhonePreviewLow() {
    Phone3Render(
        level = 18,
        modifier = Modifier
            .padding(16.dp)
            .height(260.dp)
            .aspectRatio(0.52f),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PhonePreviewHigh() {
    Phone3Render(
        level = 85,
        modifier = Modifier
            .padding(16.dp)
            .height(260.dp)
            .aspectRatio(0.52f),
    )
}
