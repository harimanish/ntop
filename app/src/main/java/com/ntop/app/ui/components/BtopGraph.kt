package com.ntop.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ntop.app.ui.theme.GeistMono
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
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(8.dp))
            BtopGraph(
                values = values,
                max = max,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                lineColor = accent,
            )
        }
    }
}

/** Per-core frequency bars (0..1 load proxy each). */
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
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp,
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
                val rowH = size.height / cores.size.coerceAtLeast(1)
                val barH = (rowH * 0.55f).coerceAtLeast(2.dp.toPx())
                cores.forEachIndexed { i, v ->
                    val y = i * rowH + (rowH - barH) / 2
                    drawRect(Color.White.copy(alpha = 0.10f), Offset(0f, y), size.copy(width = size.width, height = barH))
                    drawRect(accent.copy(alpha = 0.85f), Offset(0f, y), size.copy(width = size.width * v.coerceIn(0f, 1f), height = barH))
                }
            }
        }
    }
}
