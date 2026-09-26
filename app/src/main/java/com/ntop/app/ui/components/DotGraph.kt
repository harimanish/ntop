package com.ntop.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Dot-matrix graph, in the language of Nothing's own widgets and the Glyph
// Matrix: the series is sampled onto a fixed pixel lattice and drawn as
// discrete dots rather than an anti-aliased stroke.
//
// The lattice is square — the same spacing horizontally and vertically — so it
// reads as an LED matrix rather than as a chart. A faint grid of unlit dots
// sits underneath, the way a real matrix shows its inactive pixels, and the lit
// trace snaps onto that same lattice.
//
// No animation happens here: the caller's entrance animation already staggers
// the reveal, and re-walking a lattice every frame is the one thing that would
// make this cost more than it is worth.
// ---------------------------------------------------------------------------

/** One sample, already normalised to 0..1. */
private class Sample(val v: Float)

@Composable
fun DotGraph(
    values: List<Float>,
    max: Float,
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White,
    gridColor: Color = Color.White,
    /** Lattice spacing for the trace, in device pixels. */
    pitch: Float = 4f,
    /** Inactive-lattice spacing. Coarser than the trace: the background is
     *  texture, not data, and it was ~98% of the frame cost at 1:1 pitch. */
    gridPitch: Float = 8f,
    /** Alpha for the inactive lattice dots. */
    gridAlpha: Float = 0.12f,
) {
    // Recomputed only when the series or the scale actually changes.
    val samples = remember(values, max) {
        val scale = if (max > 0f) max else 1f
        values.map { Sample((it / scale).coerceIn(0f, 1f)) }
    }
    Canvas(modifier = modifier) {
        drawDotGraph(samples, dotColor, gridColor, gridAlpha, pitch, gridPitch)
    }
}
private fun DrawScope.drawDotGraph(
    samples: List<Sample>,
    dotColor: Color,
    gridColor: Color,
    gridAlpha: Float,
    pitch: Float,
    gridPitch: Float,
) {
    if (samples.isEmpty()) return

    val cols = (size.width / pitch).toInt().coerceAtLeast(2)
    val rows = (size.height / pitch).toInt().coerceAtLeast(2)
    val stepX = size.width / cols
    val stepY = size.height / rows
    val traceR = (min(stepX, stepY) * 0.32f).coerceAtLeast(0.9f)

    fun cellX(c: Int) = (c + 0.5f) * stepX
    fun cellY(row: Int) = size.height - (row + 0.5f) * stepY

    // Inactive lattice, on its own coarser pitch. Batched: thousands of
    // individual drawCircle calls would dominate the frame, so each layer is a
    // single drawPoints with round caps — same pixels, far cheaper.
    val gCols = (size.width / gridPitch).toInt().coerceAtLeast(2)
    val gRows = (size.height / gridPitch).toInt().coerceAtLeast(2)
    val gStepX = size.width / gCols
    val gStepY = size.height / gRows
    val gridR = (min(gStepX, gStepY) * 0.30f).coerceAtLeast(0.9f)
    val gridPts = ArrayList<Offset>(gCols * gRows)
    for (row in 0 until gRows) {
        val y = size.height - (row + 0.5f) * gStepY
        for (c in 0 until gCols) gridPts.add(Offset((c + 0.5f) * gStepX, y))
    }
    drawPoints(
        points = gridPts,
        pointMode = PointMode.Points,
        color = gridColor.copy(alpha = gridAlpha),
        strokeWidth = gridR * 2f,
        cap = StrokeCap.Round,
    )

    val n = samples.size
    val tracePts = ArrayList<Offset>(n)
    val echoPts = ArrayList<Offset>(n)
    for ((i, s) in samples.withIndex()) {
        val c = if (n == 1) 0 else (i * (cols - 1)) / (n - 1)
        val row = (s.v * (rows - 1)).roundToInt().coerceIn(0, rows - 1)
        val x = cellX(c)
        tracePts.add(Offset(x, cellY(row)))
        // One dimmer row below keeps the trace reading as continuous.
        if (row > 0) echoPts.add(Offset(x, cellY(row - 1)))
    }
    if (echoPts.isNotEmpty()) {
        drawPoints(echoPts, PointMode.Points, dotColor.copy(alpha = 0.40f), traceR * 2f, StrokeCap.Round)
    }
    drawPoints(tracePts, PointMode.Points, dotColor, traceR * 2f, StrokeCap.Round)
}

