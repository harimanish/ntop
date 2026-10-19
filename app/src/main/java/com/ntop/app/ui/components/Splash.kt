package com.ntop.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntop.app.ui.theme.GeistMono
import com.ntop.app.ui.theme.NothingBlack
import com.ntop.app.ui.theme.NothingRed
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Launch animation: the icon's dots fly in from every direction and assemble
// the logo.
//
// The mark is the adaptive-icon foreground itself - a dot-matrix "n" made of
// thirteen white dots plus the red marker - so the splash is the icon, not an
// illustration of it. Each dot starts off-screen on its own bearing (golden
// angle), follows a bowed quadratic path and lands with an overshoot, leaving
// a short motion streak and a ring flash. The app's graph lattice fades in
// underneath as they settle, then the wordmark, then the red marker pulses
// once before the whole frame dissolves into the UI that was loading behind.
//
// One master clock drives everything: 14 coroutines would be 14 sources of
// drift, and the exit fade has to be frame-locked to the last landing.
// ---------------------------------------------------------------------------

/** Total run, including the fade into the app. */
private const val TOTAL_MS = 1750f

/** Where the dissolve starts, as a fraction of TOTAL_MS. */
private const val EXIT_START = 0.84f

/** Dot geometry in icon-viewport units (the 108-unit adaptive-icon canvas). */
private data class LogoDot(val x: Float, val y: Float, val red: Boolean)

private val LOGO: List<LogoDot> = buildList {
    // Top row of the "n" (the red marker is its top-right corner).
    add(LogoDot(-10f, -20f, false))
    add(LogoDot(0f, -20f, false))
    add(LogoDot(10f, -20f, false))
    add(LogoDot(20f, -20f, true))
    // Left stem.
    for (y in -12..20 step 8) add(LogoDot(-20f, y.toFloat(), false))
    // Right stem.
    for (y in -12..20 step 8) add(LogoDot(20f, y.toFloat(), false))
}

/** Icon units from the left stem to the right stem, dot edges included. */
private const val LOGO_SPAN = 46.8f

private val OVERSHOOT = CubicBezierEasing(0.2f, 1.55f, 0.42f, 1f)

private data class Flight(
    val angle: Float,
    val distance: Float,
    val delay: Float,
    val duration: Float,
    val bow: Float,
)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun quadBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return p0 * (u * u) + p1 * (2f * u * t) + p2 * (t * t)
}

/**
 * Full-screen launch splash over the app shell. [onFinished] fires once the
 * dissolve completes; the caller drops this composable then.
 */
@Composable
fun NtopSplash(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    val wordStyle = remember {
        TextStyle(
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 5.sp,
        )
    }
    val word = remember {
        measurer.measure(AnnotatedString("ntop"), wordStyle)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(NothingBlack)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val unit = min(w, h) * 0.46f / LOGO_SPAN
        val cx = w / 2f
        val cy = h * 0.46f

        // Bearing, start distance, stagger and curvature per dot. Seeded so the
        // choreography is identical on every launch; the red marker is last.
        val flights = remember(w, h) {
            val rnd = Random(11)
            LOGO.mapIndexed { i, dot ->
                val angle = i * 2.399963f + (rnd.nextFloat() - 0.5f) * 0.4f
                val distance = max(w, h) * (1.05f + rnd.nextFloat() * 0.45f)
                val delay = if (dot.red) 290f else 30f + rnd.nextFloat() * 230f
                Flight(
                    angle = angle,
                    distance = distance,
                    delay = delay,
                    duration = 500f + rnd.nextFloat() * 70f,
                    bow = (rnd.nextFloat() * 2f - 1f) * distance * 0.07f,
                )
            }
        }

        // The faint dot lattice the app's graphs are drawn on, as one point batch.
        val lattice = remember(w, h) {
            val pitch = 42f
            val cols = (w / pitch).toInt() + 2
            val rows = (h / pitch).toInt() + 2
            buildList {
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        add(Offset(c * pitch, r * pitch))
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawSplash(
                    clock = clock.value,
                    flights = flights,
                    lattice = lattice,
                    word = word,
                    w = w,
                    h = h,
                    cx = cx,
                    cy = cy,
                    unit = unit,
                )
            }
        }
    }
}

private fun DrawScope.drawSplash(
    clock: Float,
    flights: List<Flight>,
    lattice: List<Offset>,
    word: androidx.compose.ui.text.TextLayoutResult,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val ms = clock * TOTAL_MS
    val exit = 1f - smoothstep(EXIT_START, 1f, clock)
    if (exit <= 0f) return

    // Background first: the dissolve has to reveal the live UI, not black.
    drawRect(NothingBlack.copy(alpha = exit))

    // Lattice: the app's graph texture, appearing under the landed dots.
    val latticeAlpha = 0.045f * smoothstep(260f, 820f, ms) * exit
    if (latticeAlpha > 0f) {
        drawPoints(
            points = lattice,
            pointMode = PointMode.Points,
            color = Color.White.copy(alpha = latticeAlpha),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    LOGO.forEachIndexed { i, dot ->
        val f = flights[i]
        val t = ((ms - f.delay) / f.duration).coerceIn(0f, 1f)
        if (t <= 0f) return@forEachIndexed

        val target = Offset(cx + dot.x * unit, cy + dot.y * unit)
        val dir = Offset(cos(f.angle), sin(f.angle))
        val start = target + dir * f.distance
        val ctrl = (start + target) * 0.5f + Offset(-dir.y, dir.x) * f.bow

        val eased = OVERSHOOT.transform(t)
        val pos = quadBezier(start, ctrl, target, eased)
        val radius = (if (dot.red) 4.5f else 3.4f) * unit
        val baseColor = if (dot.red) NothingRed else Color.White

        // Motion streak: a short line from where the dot was ~90 ms ago.
        val tPrev = ((ms - f.delay - 90f) / f.duration).coerceIn(0f, 1f)
        if (t < 0.999f && tPrev < t) {
            val prev = quadBezier(start, ctrl, target, OVERSHOOT.transform(tPrev))
            drawLine(
                color = baseColor.copy(alpha = 0.22f * exit),
                start = prev,
                end = pos,
                strokeWidth = radius * 0.85f,
                cap = StrokeCap.Round,
            )
        }

        // The dot grows into place; the fade-in is quick so the entry reads.
        val grow = 0.45f + 0.55f * eased.coerceIn(0f, 1.2f)
        var alpha = (t * 5f).coerceAtMost(1f) * exit
        // One calm pulse of the marker after everything has settled.
        if (dot.red && ms > 980f) {
            val pulse = sin(((ms - 980f) / 320f * Math.PI).toFloat()).coerceAtLeast(0f)
            alpha *= 1f - 0.45f * pulse
        }
        drawCircle(color = baseColor, radius = radius * grow, center = pos, alpha = alpha)

        // Landing flash: a ring blooms from the target as the dot arrives.
        val ring = ((ms - f.delay - f.duration) / 280f).coerceIn(0f, 1f)
        if (ring > 0f && ring < 1f) {
            drawCircle(
                color = baseColor.copy(alpha = 0.32f * (1f - ring) * exit),
                radius = radius * (1.1f + 1.7f * ring),
                center = target,
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
    }

    // Wordmark, wide-tracked like the app's micro-labels.
    val wordAlpha = smoothstep(660f, 980f, ms) * exit
    if (wordAlpha > 0f) {
        val y = cy + 23.4f * unit + 52.dp.toPx()
        drawText(
            textLayoutResult = word,
            color = Color.White,
            topLeft = Offset(cx - word.size.width / 2f, y),
            alpha = wordAlpha * 0.62f,
        )
    }
}
