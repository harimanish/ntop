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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ntop.app.ui.theme.NDotDisplay
import com.ntop.app.ui.theme.Muted
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Panels & meters: monochrome white-on-translucent, dotted LED language.
// No red — red stays on the charging pill/dot only.
// ---------------------------------------------------------------------------

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
