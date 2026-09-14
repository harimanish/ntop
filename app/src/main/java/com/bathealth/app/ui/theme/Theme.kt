package com.bathealth.app.ui.theme

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
import com.bathealth.app.R

// ---------------------------------------------------------------------------
// Nothing OS 5.0 palette: monochrome-first, one red accent.
// ---------------------------------------------------------------------------
val NothingBlack = Color(0xFF0A0A0B)
val NothingBlackSoft = Color(0xFF131316) // second stop for the Lucent depth gradient
val LucentSurface = Color(0x14FFFFFF) // ~8% white: the "Lucent" frosted layer
val LucentSurfaceStrong = Color(0x26FFFFFF) // ~15% white: bottom bar / emphasis layer
val LucentBorder = Color(0x1F595959) // hairline edge for translucent cards
val LucentHighlight = Color(0x40FFFFFF) // inner top highlight on Lucent layers
val Muted = Color(0xFFA1A1A1)
val NothingRed = Color(0xFFD71920) // Nothing brand red, charging/recording dot
val GlowRed = Color(0x29D71920) // soft red ambience behind Lucent layers
val GlowWhite = Color(0x0AFFFFFF) // soft white ambience for depth

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

private val BatHealthTypography = Typography(
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

private val BatHealthColors = darkColorScheme(
    background = NothingBlack,
    surface = NothingBlack,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color.White,
    secondary = Muted,
    tertiary = NothingRed,
)

@Composable
fun BatHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BatHealthColors,
        typography = BatHealthTypography,
        content = content,
    )
}
