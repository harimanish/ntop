package com.ntop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ntop.app.R

// ---------------------------------------------------------------------------
// Nothing OS 5.0 palette: monochrome-first, one red accent.
// ---------------------------------------------------------------------------
val NothingBlack = Color(0xFF0A0A0B)
val NothingBlackSoft = Color(0xFF131316) // second stop for the Lucent depth gradient
val LucentSurface = Color(0x14FFFFFF) // ~8% white: the "Lucent" frosted layer
val LucentSurfaceStrong = Color(0x26FFFFFF) // ~15% white: bottom bar / emphasis layer
val LucentBorder = Color(0x1F595959) // hairline edge for translucent cards
val Muted = Color(0xFFA1A1A1)
val NothingRed = Color(0xFFD71920) // Nothing brand red, charging/recording dot

// ---------------------------------------------------------------------------
// Enhanced glassmorphism tokens for fluent design.
// ---------------------------------------------------------------------------
val GlassSurface = Color(0x1AFFFFFF) // 10% white: colorScheme surface layer

// ---------------------------------------------------------------------------
// User-selectable accent palette (SYS → APPEARANCE). Drives graph lines +
// fills, core bars, history dots and the selected-tab indicator. Charging
// pill/dot always stay NothingRed regardless of accent.
// ---------------------------------------------------------------------------
val AccentNames = listOf("WHITE", "GREEN", "AMBER", "BLUE", "VIOLET", "RED")
val AccentColors = listOf(
    Color.White,
    Color(0xFF4CAF50),
    Color(0xFFFFB300),
    Color(0xFF40C4FF),
    Color(0xFFB388FF),
    NothingRed,
)

// ---------------------------------------------------------------------------
// Geist typeface roles (Nothing OS 5.0 system font, Vercel OFL).
//
// Geist Sans  -> UI labels, headings, body (clarity + tight spacing).
// Geist Mono  -> numerals / telemetry (tabular, Micrographics-inspired).
//
// Bundled from Google Fonts ("Geist" v5 + "Geist Mono" v6, OFL-1.1).
// See OFL-Geist.txt / OFL-GeistMono.txt at repo root.
// res/font/geist_sans.xml maps 400/500/600, geist_mono.xml maps 400/500.
// ---------------------------------------------------------------------------
val GeistSans: FontFamily = FontFamily(Font(R.font.geist_sans))
val GeistMono: FontFamily = FontFamily(Font(R.font.geist_mono))

// ---------------------------------------------------------------------------
// NDot — Nothing's own dot-matrix display face, for hero numerals and section
// headers. It is a *system* font on Nothing devices (/system/fonts/Ndot-55.otf,
// family "Ndot 55", PostScript name "Ndot55All" — the same face the Glyph Matrix
// SDK renders its text with), so there is no asset to bundle and no licence
// question. Falls back to Geist Mono on devices that do not ship it, which keeps
// ntop installable anywhere while still looking native on a Nothing phone.
// ---------------------------------------------------------------------------
private fun systemTypeface(vararg candidates: String): android.graphics.Typeface? {
    for (name in candidates) {
        val tf = runCatching { android.graphics.Typeface.create(name, android.graphics.Typeface.NORMAL) }
            .getOrNull()
        if (tf != null) return tf
    }
    return null
}

val NDotDisplay: FontFamily = systemTypeface("Ndot 55", "Ndot55All", "NDot55All")
    ?.let { FontFamily(it) }
    ?: GeistMono

private val NtopTypography = Typography(
    // Hero percentage: Geist Mono, tabular numerals, tight tracking like
    // Nothing's Micrographics clock face.
    displayLarge = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 96.sp,
        lineHeight = 96.sp,
        letterSpacing = (-2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    // Mono uppercase micro-labels: section headers, telemetry captions.
    labelSmall = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val NtopColors = darkColorScheme(
    background = NothingBlack,
    surface = GlassSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color.White,
    secondary = Muted,
    tertiary = NothingRed,
)

@Composable
fun NtopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NtopColors,
        typography = NtopTypography,
        content = content,
    )
}
