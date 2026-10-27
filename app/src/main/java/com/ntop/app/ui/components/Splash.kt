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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntop.app.stats.SplashIntro
import com.ntop.app.ui.theme.GeistMono
import com.ntop.app.ui.theme.NothingBlack
import com.ntop.app.ui.theme.NothingRed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Launch animations. Three intros, chosen by the user's LAUNCH ANIMATION
// setting in SYS > APPEARANCE (BLACKHOLE is the default):
//
//   BLACKHOLE - a void ignites at the centre, the dots spiral into it one by
//               one and are swallowed, then the singularity bursts the mark
//               back out along the slots.
//   SOLAR     - a red sun ignites at the centre, the dots burst out of it into
//               three counter-clockwise orbits, swirl, then collapse into the
//               mark while the sun flies to its corner last.
//   FLY_IN    - the icon's dots fly in from every direction and assemble the
//               mark.
//
// All three share the same finish: the app's dot lattice fades in, the
// wide-tracked wordmark follows, the red marker pulses once, and the frame
// dissolves into the UI that was loading behind it.
//
// The mark is the adaptive-icon foreground itself - a dot-matrix "n" made of
// thirteen white dots plus the red marker - so the splash is the icon, not an
// illustration of it. One master clock drives everything: fourteen coroutines
// would be fourteen sources of drift, and the exit fade has to be frame-locked
// to the last landing.
// ---------------------------------------------------------------------------

/** Per-intro beat sheet, in milliseconds from launch. */
private data class Timing(
    val total: Float,
    val exitAt: Float,
    val latticeIn: Float,
    val latticeOut: Float,
    val wordIn: Float,
    val wordOut: Float,
    val pulseAt: Float,
    val pulseOut: Float,
)

private val FLY_IN = Timing(
    total = 1750f,
    exitAt = 1470f,
    latticeIn = 260f,
    latticeOut = 820f,
    wordIn = 660f,
    wordOut = 980f,
    pulseAt = 980f,
    pulseOut = 1300f,
)

private val SOLAR = Timing(
    total = 2450f,
    exitAt = 2270f,
    latticeIn = 1450f,
    latticeOut = 1900f,
    wordIn = 1950f,
    wordOut = 2150f,
    pulseAt = 2060f,
    pulseOut = 2290f,
)

private val BLACKHOLE = Timing(
    total = 2450f,
    exitAt = 2270f,
    latticeIn = 1450f,
    latticeOut = 1900f,
    wordIn = 1950f,
    wordOut = 2150f,
    pulseAt = 2060f,
    pulseOut = 2290f,
)

// BLACKHOLE beats, in milliseconds. The last dot crosses the horizon around
// 1230, the ring implodes to a point by 1370, and the burst starts at 1330.
private const val BH_FIRST_FALL = 110f
private const val BH_FALL_STAGGER = 36f
private const val BH_IMPLODE_AT = 1180f
private const val BH_IMPLODE_DUR = 190f
private const val BH_BURST_AT = 1330f

/** Horizon radius in icon units (the mark's dots start well outside it). */
private const val BH_HORIZON_UNITS = 7.6f

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

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t
    return 1f - u * u * u
}

private fun easeInCubic(t: Float): Float = t * t * t

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) * (-2f * t + 2f) / 2f

/** Wraps an angle into (-PI, PI]. */
private fun wrapAngle(a: Float): Float = a - (2f * PI.toFloat()) * round(a / (2f * PI.toFloat()))

private fun quadBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
    val u = 1f - t
    return p0 * (u * u) + p1 * (2f * u * t) + p2 * (t * t)
}

/** FLY_IN: where a dot starts and how it travels to its slot. */
private data class Flight(
    val angle: Float,
    val distance: Float,
    val delay: Float,
    val duration: Float,
    val bow: Float,
)

/** SOLAR: a dot's orbit lane, spin and the alignment path into its slot. */
private data class Orbit(
    val radius: Float,
    val omega: Float,
    val base: Float,
    val burstAt: Float,
    val slotR: Float,
    val slotAngle: Float,
    val alignDelta: Float,
)

/** BLACKHOLE: one dot's death spiral into the horizon and its ejection back out. */
private data class Infall(
    val startR: Float,
    val startAngle: Float,
    val spin: Float,
    val fallAt: Float,
    val fallDur: Float,
    val slotR: Float,
    val slotAngle: Float,
    val ejectAt: Float,
    val ejectDur: Float,
) {
    val swallowedAt: Float get() = fallAt + fallDur
    val landedAt: Float get() = ejectAt + ejectDur
}

/**
 * Full-screen launch splash over the app shell. [intro] is the user's choice
 * from SYS > APPEARANCE; [onFinished] fires once the dissolve completes and
 * the caller drops this composable.
 */
@Composable
fun NtopSplash(intro: SplashIntro, onFinished: () -> Unit) {
    val timing = when (intro) {
        SplashIntro.FLY_IN -> FLY_IN
        SplashIntro.SOLAR -> SOLAR
        SplashIntro.BLACKHOLE -> BLACKHOLE
    }

    val clock = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        // Start on the first *drawn* frame: a cold start can spend hundreds of
        // milliseconds in the launch-window transition, and the intro must not
        // play behind it.
        withFrameNanos { }
        clock.animateTo(1f, tween(timing.total.toInt(), easing = LinearEasing))
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

        // Three counter-clockwise lanes of 5/4/4 white dots. Each dot's base
        // bearing is its own; the swirl unwinds as it leaves the core, the
        // orbit keeps turning, and a per-dot delta nudges the angle onto its
        // slot ray before the collapse starts.
        val orbits = remember(w, h, unit) {
            val rnd = Random(23)
            val m = min(w, h)
            val bandRadius = floatArrayOf(0.17f * m, 0.26f * m, 0.35f * m)
            val spinScale = 2f * PI.toFloat() / 1000f
            val bandOmega = floatArrayOf(-1.5f * spinScale, -1.25f * spinScale, -1.0f * spinScale)
            var white = -1
            LOGO.mapIndexed { i, dot ->
                if (dot.red) {
                    // The sun owns its own path; keep the list index-aligned.
                    Orbit(0f, 0f, 0f, 0f, 0f, 0f, 0f)
                } else {
                    white++
                    val band = when {
                        white <= 4 -> 0
                        white <= 8 -> 1
                        else -> 2
                    }
                    val base = white * 2.399963f + (rnd.nextFloat() - 0.5f) * 0.5f
                    val burstAt = 90f + i * 16f
                    val angleAt1400 = base + bandOmega[band] * (1400f - burstAt - 420f)
                    val slotR = hypot(dot.x * unit, dot.y * unit)
                    val slotAngle = atan2(dot.y * unit, dot.x * unit)
                    Orbit(
                        radius = bandRadius[band],
                        omega = bandOmega[band],
                        base = base,
                        burstAt = burstAt,
                        slotR = slotR,
                        slotAngle = slotAngle,
                        alignDelta = wrapAngle(slotAngle - angleAt1400),
                    )
                }
            }
        }

        // BLACKHOLE: each dot gets a start bearing, an accelerating spin, a
        // fall schedule and an ejection schedule. The red marker falls in last
        // and is thrown back out last, onto its corner.
        val infalls = remember(w, h, unit) {
            val rnd = Random(37)
            val m = min(w, h)
            var white = -1
            LOGO.map { dot ->
                val slotR = hypot(dot.x * unit, dot.y * unit)
                val slotAngle = atan2(dot.y * unit, dot.x * unit)
                if (dot.red) {
                    Infall(
                        startR = m * 0.41f,
                        startAngle = 2.1f,
                        spin = 5.2f,
                        fallAt = BH_FIRST_FALL + 13f * BH_FALL_STAGGER,
                        fallDur = 620f,
                        slotR = slotR,
                        slotAngle = slotAngle,
                        ejectAt = BH_BURST_AT + 120f,
                        ejectDur = 400f,
                    )
                } else {
                    white++
                    Infall(
                        startR = m * (0.33f + rnd.nextFloat() * 0.11f),
                        startAngle = white * 2.399963f + (rnd.nextFloat() - 0.5f) * 0.5f,
                        spin = 4.0f + rnd.nextFloat() * 2.2f,
                        fallAt = BH_FIRST_FALL + white * BH_FALL_STAGGER,
                        fallDur = 600f + rnd.nextFloat() * 90f,
                        slotR = slotR,
                        slotAngle = slotAngle,
                        ejectAt = BH_BURST_AT + white * 8f,
                        ejectDur = 390f + rnd.nextFloat() * 40f,
                    )
                }
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
                    intro = intro,
                    timing = timing,
                    clock = clock.value,
                    flights = flights,
                    orbits = orbits,
                    infalls = infalls,
                    lattice = lattice,
                    word = word,
                    cx = cx,
                    cy = cy,
                    unit = unit,
                )
            }
        }
    }
}

private fun DrawScope.drawSplash(
    intro: SplashIntro,
    timing: Timing,
    clock: Float,
    flights: List<Flight>,
    orbits: List<Orbit>,
    infalls: List<Infall>,
    lattice: List<Offset>,
    word: TextLayoutResult,
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val ms = clock * timing.total
    val exit = 1f - smoothstep(timing.exitAt, timing.total, ms)
    if (exit <= 0f) return

    // Background first: the dissolve has to reveal the live UI, not black.
    drawRect(NothingBlack.copy(alpha = exit))

    // Lattice: the app's graph texture, appearing under the settling dots.
    val latticeAlpha = 0.045f * smoothstep(timing.latticeIn, timing.latticeOut, ms) * exit
    if (latticeAlpha > 0f) {
        drawPoints(
            points = lattice,
            pointMode = PointMode.Points,
            color = Color.White.copy(alpha = latticeAlpha),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    when (intro) {
        SplashIntro.BLACKHOLE -> drawBlackhole(timing, ms, exit, infalls, cx, cy, unit)
        SplashIntro.SOLAR -> drawSolar(timing, ms, exit, orbits, cx, cy, unit)
        SplashIntro.FLY_IN -> drawFlyIn(timing, ms, exit, flights, cx, cy, unit)
    }

    // Wordmark, wide-tracked like the app's micro-labels.
    val wordAlpha = smoothstep(timing.wordIn, timing.wordOut, ms) * exit
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

/** Shared landing flash: a ring blooming from a target as its dot arrives. */
private fun DrawScope.landingRing(
    color: Color,
    target: Offset,
    radius: Float,
    ms: Float,
    landedAt: Float,
    exit: Float,
) {
    val ring = ((ms - landedAt) / 280f).coerceIn(0f, 1f)
    if (ring <= 0f || ring >= 1f) return
    drawCircle(
        color = color.copy(alpha = 0.32f * (1f - ring) * exit),
        radius = radius * (1.1f + 1.7f * ring),
        center = target,
        style = Stroke(width = 1.4.dp.toPx()),
    )
}

/** The red marker's single calm pulse during the hold. */
private fun pulseFactor(ms: Float, timing: Timing): Float {
    if (ms <= timing.pulseAt || ms >= timing.pulseOut) return 1f
    val t = (ms - timing.pulseAt) / (timing.pulseOut - timing.pulseAt)
    return 1f - 0.45f * sin((t * PI).toFloat()).coerceAtLeast(0f)
}

private fun DrawScope.drawFlyIn(
    timing: Timing,
    ms: Float,
    exit: Float,
    flights: List<Flight>,
    cx: Float,
    cy: Float,
    unit: Float,
) {
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
        if (dot.red) alpha *= pulseFactor(ms, timing)
        drawCircle(color = baseColor, radius = radius * grow, center = pos, alpha = alpha)

        landingRing(baseColor, target, radius, ms, f.delay + f.duration, exit)
    }
}

private fun DrawScope.drawSolar(
    timing: Timing,
    ms: Float,
    exit: Float,
    orbits: List<Orbit>,
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val centre = Offset(cx, cy)
    val sunRadius = 4.5f * unit
    val whiteRadius = 3.4f * unit

    // -- Ignition: a thin ring implodes, a soft bloom opens, a hot core dies.
    if (ms < 300f) {
        val ringT = (ms / 150f).coerceIn(0f, 1f)
        if (ringT < 1f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.25f * ringT * exit),
                radius = lerp(120f, 2f, easeInCubic(ringT)),
                center = centre,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        val bloomT = (ms / 260f).coerceIn(0f, 1f)
        if (bloomT < 1f) {
            drawCircle(
                color = NothingRed.copy(alpha = 0.30f * (1f - bloomT) * exit),
                radius = lerp(8f, 60f, easeOutCubic(bloomT)),
                center = centre,
            )
        }
        val coreT = (ms / 140f).coerceIn(0f, 1f)
        if (coreT < 1f) {
            drawCircle(
                color = Color.White.copy(alpha = (1f - coreT) * exit),
                radius = 2.dp.toPx(),
                center = centre,
            )
        }
    }

    // -- The sun: holds the centre through the orbit, then flies to its corner.
    val sunTarget = Offset(cx + 20f * unit, cy - 20f * unit)
    val sunAlpha = smoothstep(120f, 320f, ms) * exit * pulseFactor(ms, timing)
    val sunAt = if (ms < 1600f) {
        centre
    } else {
        val t = ((ms - 1600f) / 390f).coerceIn(0f, 1f)
        Offset(
            lerp(centre.x, sunTarget.x, OVERSHOOT.transform(t)),
            lerp(centre.y, sunTarget.y, OVERSHOOT.transform(t)),
        )
    }
    // Breathing halo while it holds the centre; fades as it departs.
    val halo = (1f - smoothstep(1600f, 1760f, ms)) * exit * sunAlpha
    if (halo > 0f) {
        val breath = 1f + 0.06f * sin(ms / 240f)
        val rings = floatArrayOf(1.6f, 2.4f, 3.4f)
        val alphas = floatArrayOf(0.10f, 0.06f, 0.03f)
        for (k in rings.indices) {
            drawCircle(
                color = NothingRed.copy(alpha = alphas[k] * halo),
                radius = sunRadius * rings[k] * breath,
                center = centre,
            )
        }
    }
    // The sun's own comet trail during the flight to the corner.
    if (ms in 1600f..1990f) {
        val prevT = ((ms - 90f - 1600f) / 390f).coerceIn(0f, 1f)
        val prev = Offset(
            lerp(centre.x, sunTarget.x, OVERSHOOT.transform(prevT)),
            lerp(centre.y, sunTarget.y, OVERSHOOT.transform(prevT)),
        )
        drawLine(
            color = NothingRed.copy(alpha = 0.22f * exit),
            start = prev,
            end = sunAt,
            strokeWidth = sunRadius * 0.85f,
            cap = StrokeCap.Round,
        )
    }

    // -- The white dots: burst, orbit, align, collapse onto the mark.
    LOGO.forEachIndexed { i, dot ->
        if (dot.red) return@forEachIndexed
        val o = orbits[i]
        if (ms < o.burstAt) return@forEachIndexed

        val pos = orbitPos(o, ms, cx, cy)
        val posPrev = orbitPos(o, ms - 80f, cx, cy)
        val burstT = ((ms - o.burstAt) / 420f).coerceIn(0f, 1f)

        if (ms < 1830f) {
            drawLine(
                color = Color.White.copy(alpha = 0.22f * exit),
                start = posPrev,
                end = pos,
                strokeWidth = whiteRadius * 0.85f,
                cap = StrokeCap.Round,
            )
        }

        val grow = 0.4f + 0.6f * easeOutCubic(burstT)
        val alpha = smoothstep(0f, 0.25f, burstT) * exit
        drawCircle(
            color = Color.White,
            radius = whiteRadius * grow,
            center = pos,
            alpha = alpha,
        )

        landingRing(
            color = Color.White,
            target = Offset(cx + dot.x * unit, cy + dot.y * unit),
            radius = whiteRadius,
            ms = ms,
            landedAt = 1830f,
            exit = exit,
        )
    }

    // -- The sun lands last, on the marker's corner.
    if (sunAlpha > 0f) {
        drawCircle(color = NothingRed, radius = sunRadius, center = sunAt, alpha = sunAlpha)
    }
    landingRing(NothingRed, sunTarget, sunRadius, ms, 1990f, exit)
}

/** Position of an orbiting dot: burst outward, spin, align, collapse. */
private fun orbitPos(o: Orbit, ms: Float, cx: Float, cy: Float): Offset {
    val burstT = ((ms - o.burstAt) / 420f).coerceIn(0f, 1f)
    val r = if (ms < 1400f) {
        lerp(6f, o.radius, easeOutCubic(burstT))
    } else {
        val t = ((ms - 1400f) / 430f).coerceIn(0f, 1f)
        lerp(o.radius, o.slotR, easeInOutCubic(t))
    }
    val angle = if (ms < 1400f) {
        // The swirl unwinds out of the burst, then the lane keeps turning.
        val spin = o.omega * (ms - o.burstAt - 420f).coerceAtLeast(0f)
        val align = o.alignDelta * smoothstep(1100f, 1400f, ms)
        o.base + 1.1f * (1f - easeOutCubic(burstT)) + spin + align
    } else {
        o.slotAngle
    }
    return Offset(cx + cos(angle) * r, cy + sin(angle) * r)
}

private fun DrawScope.drawBlackhole(
    timing: Timing,
    ms: Float,
    exit: Float,
    infalls: List<Infall>,
    cx: Float,
    cy: Float,
    unit: Float,
) {
    val centre = Offset(cx, cy)
    val horizon = BH_HORIZON_UNITS * unit
    val sunRadius = 4.5f * unit
    val whiteRadius = 3.4f * unit

    // Matter crossing the rim lights it: each swallow adds a short flare, and
    // the red marker's fall blushes the photon ring for a moment.
    var flare = 0f
    var redFlare = 0f
    LOGO.forEachIndexed { i, dot ->
        val t = (ms - infalls[i].swallowedAt) / 260f
        if (t in 0f..1f) {
            if (dot.red) redFlare = 0.20f * (1f - t) else flare += 0.08f * (1f - t)
        }
    }

    // -- Ignition: a hot flash is swallowed by the void, which then holds a
    //    thin photon ring around a patch of true black.
    if (ms < 320f) {
        val flash = (ms / 120f).coerceIn(0f, 1f)
        if (flash < 1f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.45f * (1f - flash) * exit),
                radius = lerp(4f, horizon * 1.4f, easeOutCubic(flash)),
                center = centre,
            )
        }
        val ring = (ms / 230f).coerceIn(0f, 1f)
        if (ring < 1f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.35f * (1f - ring) * exit),
                radius = lerp(horizon * 2.7f, horizon, easeInCubic(ring)),
                center = centre,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }

    // -- The hole itself. Once the last dot is in, the ring implodes to a
    //    point: that point is the singularity the mark bursts out of.
    val implode = ((ms - BH_IMPLODE_AT) / BH_IMPLODE_DUR).coerceIn(0f, 1f)
    val holeR = lerp(horizon, 1.5f, easeInCubic(implode))
    val holeAlpha = if (ms < BH_IMPLODE_AT) smoothstep(0f, 200f, ms) else 1f - implode * implode
    if (holeAlpha > 0f) {
        val glow = (0.07f + flare) * holeAlpha * exit
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.42f to Color.White.copy(alpha = glow),
                1f to Color.Transparent,
                center = centre,
                radius = holeR * 2.6f,
            ),
            radius = holeR * 2.6f,
            center = centre,
        )
        drawCircle(
            color = Color.Black.copy(alpha = exit),
            radius = holeR * 0.98f,
            center = centre,
        )
        drawCircle(
            color = Color.White.copy(
                alpha = ((0.50f + flare * 2f) * holeAlpha * exit).coerceAtMost(1f),
            ),
            radius = holeR,
            center = centre,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        if (redFlare > 0f) {
            drawCircle(
                color = NothingRed.copy(alpha = (redFlare * holeAlpha * exit).coerceAtMost(1f)),
                radius = holeR,
                center = centre,
                style = Stroke(width = 2.4.dp.toPx()),
            )
        }
    }

    // -- The burst: the point detonates and the dots ride the shockwave out.
    val burst = ((ms - BH_BURST_AT) / 300f).coerceIn(0f, 1f)
    if (ms >= BH_BURST_AT && burst < 1f) {
        val fade = 1f - burst
        drawCircle(
            color = Color.White.copy(alpha = 0.30f * fade * exit),
            radius = lerp(4f, horizon * 4f, easeOutCubic(burst)),
            center = centre,
            style = Stroke(width = 1.4.dp.toPx()),
        )
        // A hot core that dies fast: a lingering filled disc sat inside the
        // finished mark as a grey smudge.
        drawCircle(
            color = Color.White.copy(alpha = 0.45f * fade * fade * fade * exit),
            radius = lerp(3f, horizon * 0.42f, easeOutCubic(burst)),
            center = centre,
        )
    }

    // -- Each dot: spiral in, cross the rim, then (after the burst) fly back
    //    out along its slot ray and settle exactly on the mark.
    LOGO.forEachIndexed { i, dot ->
        val o = infalls[i]
        val radius = if (dot.red) sunRadius else whiteRadius
        val color = if (dot.red) NothingRed else Color.White
        val target = Offset(cx + dot.x * unit, cy + dot.y * unit)

        if (ms >= o.fallAt && ms < o.swallowedAt) {
            val pos = infallPos(o, ms, cx, cy, horizon)
            val prev = infallPos(o, ms - 80f, cx, cy, horizon)
            val rNow = hypot(pos.x - cx, pos.y - cy)
            val vis = smoothstep(o.fallAt, o.fallAt + 100f, ms) *
                smoothstep(horizon * 0.9f, horizon * 2f, rNow)
            if (vis > 0f) {
                cometTrail(color, prev, pos, radius * 0.85f, 0.20f * vis * exit)
                drawCircle(color = color, radius = radius, center = pos, alpha = vis * exit)
            }
        }

        if (ms >= o.ejectAt) {
            val t = ((ms - o.ejectAt) / o.ejectDur).coerceIn(0f, 1f)
            val pos = ejectPos(o, t, cx, cy)
            if (t < 0.999f) {
                val prev = ejectPos(
                    o,
                    ((ms - 70f - o.ejectAt) / o.ejectDur).coerceIn(0f, 1f),
                    cx,
                    cy,
                )
                cometTrail(color, prev, pos, radius * 0.75f, 0.22f * (1f - t) * (1f - t) * exit)
            }
            var alpha = exit
            if (dot.red) alpha *= pulseFactor(ms, timing)
            drawCircle(
                color = color,
                radius = radius * (0.55f + 0.45f * easeOutCubic(t)),
                center = pos,
                alpha = alpha,
            )
            // One ring, on the red marker only: thirteen rings blooming at once
            // read as bubbles around the compact mark.
            if (dot.red) landingRing(color, target, radius, ms, o.landedAt, exit)
        }
    }
}

/**
 * A tapered motion streak - a point at the tail, full width at the dot - so a
 * trail reads as a comet tail instead of a stick with a round cap.
 */
private fun DrawScope.cometTrail(color: Color, from: Offset, to: Offset, width: Float, alpha: Float) {
    if (alpha <= 0f) return
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx, dy)
    if (len < 1f) return
    val nx = -dy / len * width * 0.5f
    val ny = dx / len * width * 0.5f
    val path = Path().apply {
        moveTo(from.x, from.y)
        lineTo(to.x + nx, to.y + ny)
        lineTo(to.x - nx, to.y - ny)
        close()
    }
    drawPath(path = path, color = color, alpha = alpha)
}

/** Infall position: the radius plunges on a cubic, the angle winds up with it. */
private fun infallPos(o: Infall, ms: Float, cx: Float, cy: Float, horizon: Float): Offset {
    val t = ((ms - o.fallAt) / o.fallDur).coerceIn(0f, 1f)
    val r = lerp(o.startR, horizon, easeInCubic(t))
    val a = o.startAngle + o.spin * (t * t)
    return Offset(cx + cos(a) * r, cy + sin(a) * r)
}

/** Ejection position: straight out along the slot ray, overshooting once. */
private fun ejectPos(o: Infall, t: Float, cx: Float, cy: Float): Offset {
    val r = o.slotR * OVERSHOOT.transform(t)
    return Offset(cx + cos(o.slotAngle) * r, cy + sin(o.slotAngle) * r)
}
