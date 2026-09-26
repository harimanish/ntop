package com.ntop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ntop.app.ui.theme.LucentBorder
import com.ntop.app.ui.theme.NothingBlackSoft
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ntop.app.ui.theme.GeistMono
import com.ntop.app.ui.theme.NDotDisplay
import com.ntop.app.ui.theme.Muted
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// ntop btop-style graphs: monochrome white-on-translucent, dotted grid,
// Geist Mono readouts. No red — red stays on the charging pill/dot only.
// ---------------------------------------------------------------------------

/** Line + fill history graph. Values are raw; [max] scales them to 0..1. */
@Composable
fun BtopGraph(
    values: List<Float>,
    max: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White,
) {
    val pts = remember(values, max) {
        val m = if (max > 0) max else 1f
        values.map { (it / m).coerceIn(0f, 1f) }
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Dotted horizontal grid (25/50/75%).
        val dotR = 1.2.dp.toPx()
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            var x = dotR * 2
            val y = h * (1f - f)
            while (x < w) {
                drawCircle(Color.White.copy(alpha = 0.14f), dotR, Offset(x, y))
                x += 10.dp.toPx()
            }
        }
        if (pts.size < 2) return@Canvas
        val stepX = w / (59f) // HISTORY_CAP - 1 slots; latest at right edge
        val off = (59 - (pts.size - 1)).coerceAtLeast(0)
        val path = Path()
        pts.forEachIndexed { i, v ->
            val x = (off + i) * stepX
            val y = h * (1f - v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Fill under the line.
        val fill = Path().apply {
            addPath(path)
            lineTo((off + pts.size - 1) * stepX, h)
            lineTo(off * stepX, h)
            close()
        }
        drawPath(fill, lineColor.copy(alpha = 0.08f))
        drawPath(path, lineColor, style = Stroke(width = 1.5.dp.toPx()))
        // Head dot.
        val lx = (off + pts.size - 1) * stepX
        val ly = h * (1f - pts.last())
        drawCircle(lineColor, 2.5.dp.toPx(), Offset(lx, ly))
    }
}

/** Labeled graph card: caption row + graph + current value readout. */
@Composable
fun GraphCard(
    label: String,
    readout: String,
    values: List<Float>,
    max: Float,
    accent: Color = Color.White,
) {
    GlassCardVariant {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel(label, color = Muted)
                Spacer(Modifier.weight(1f))
                Text(
                    text = readout,
                    style = TextStyle(
                        // Nothing's own dot-matrix display face for the
                        // numerals, matching the Glyph Matrix readout.
                        fontFamily = NDotDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp,
                    ),
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            DotGraph(
                values = values,
                max = max,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                dotColor = accent,
            )
        }
    }
}

/**
 * Per-core frequency as a dot meter, one row per core — the same LED-matrix
 * language as [DotGraph], applied to a bar shape instead of a trace. Dots are
 * batched per row so eight cores stay cheap.
 */
@Composable
fun CoreBars(cores: List<Float>, maxMhz: String, accent: Color = Color.White) {
    GlassCardVariant {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("CPU CORES", color = Muted)
                Spacer(Modifier.weight(1f))
                Text(
                    text = maxMhz,
                    style = TextStyle(
                        fontFamily = NDotDisplay,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp,
                    ),
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((cores.size * 14).dp),
            ) {
                if (cores.isEmpty()) return@Canvas
                val rows = cores.size
                val rowH = size.height / rows
                val dotPitch = (rowH * 0.5f).coerceAtLeast(2f)
                val r = (dotPitch * 0.32f).coerceAtLeast(0.9f)
                val cols = (size.width / dotPitch).toInt().coerceAtLeast(1)
                val stepX = size.width / cols
                val lit = accent.copy(alpha = 0.92f)
                val off = Color.White.copy(alpha = 0.10f)

                for (i in cores.indices) {
                    val y = i * rowH + rowH / 2f
                    val n = (cores[i].coerceIn(0f, 1f) * cols).roundToInt()
                    val dim = ArrayList<Offset>(cols - n)
                    val bright = ArrayList<Offset>(n)
                    for (c in 0 until cols) {
                        val p = Offset((c + 0.5f) * stepX, y)
                        if (c < n) bright.add(p) else dim.add(p)
                    }
                    if (dim.isNotEmpty()) {
                        drawPoints(dim, PointMode.Points, off, r * 2f, StrokeCap.Round)
                    }
                    if (bright.isNotEmpty()) {
                        drawPoints(bright, PointMode.Points, lit, r * 2f, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

/**
 * Per-core dot meters without their own card or header, for use inside a
 * [Panel]. Each core is a row of dots; lit dots are frequency, unlit are the
 * matrix's inactive pixels.
 */
@Composable
fun CoreDots(
    cores: List<Float>,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
    rowHeight: Int = 12,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((cores.size * rowHeight).dp),
    ) {
        if (cores.isEmpty()) return@Canvas
        val rowH = size.height / cores.size
        val dotPitch = (rowH * 0.5f).coerceAtLeast(2f)
        val r = (dotPitch * 0.32f).coerceAtLeast(0.9f)
        val cols = (size.width / dotPitch).toInt().coerceAtLeast(1)
        val stepX = size.width / cols
        val lit = accent.copy(alpha = 0.92f)
        val off = Color.White.copy(alpha = 0.10f)

        for (i in cores.indices) {
            val y = i * rowH + rowH / 2f
            val n = (cores[i].coerceIn(0f, 1f) * cols).roundToInt()
            val dim = ArrayList<Offset>(cols - n)
            val bright = ArrayList<Offset>(n)
            for (c in 0 until cols) {
                val p = Offset((c + 0.5f) * stepX, y)
                if (c < n) bright.add(p) else dim.add(p)
            }
            if (dim.isNotEmpty()) {
                drawPoints(dim, PointMode.Points, off, r * 2f, StrokeCap.Round)
            }
            if (bright.isNotEmpty()) {
                drawPoints(bright, PointMode.Points, lit, r * 2f, StrokeCap.Round)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Panels: one framed compartment per subject, hairline-divided internally.
// Nothing's widgets group related readouts into a single panel with dividers
// rather than giving every stat its own card, which is also what cuts the
// scroll length on a phone screen.
// ---------------------------------------------------------------------------

/** A labelled compartment holding [PanelRow] children. */
@Composable
fun Panel(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NothingBlackSoft)
            .border(1.dp, LucentBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        MicroLabel(label, color = Muted)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** One row inside a [Panel]; draws a hairline above itself unless first. */
@Composable
fun PanelRow(label: String, readout: String, showDivider: Boolean = true) {
    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LucentBorder),
        )
        Spacer(Modifier.height(12.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MicroLabel(label, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(
            text = readout,
            style = TextStyle(
                fontFamily = NDotDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
            ),
            color = Color.White,
        )
    }
}
