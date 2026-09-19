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
val LucentHighlight = Color(0x40FFFFFF) // inner top highlight on Lucent layers
val Muted = Color(0xFFA1A1A1)
val NothingRed = Color(0xFFD71920) // Nothing brand red, charging/recording dot
val GlowRed = Color(0x29D71920) // soft red ambience behind Lucent layers
val GlowWhite = Color(0x0AFFFFFF) // soft white ambience for depth

// ---------------------------------------------------------------------------
// Enhanced glassmorphism tokens for fluent design.
// ---------------------------------------------------------------------------
val GlassSurface = Color(0x1AFFFFFF) // 10% white: stronger glass surface
val GlassBorder = Color(0x33FFFFFF) // 20% white: stronger border for glass elements
val GlassHighlight = Color(0x4DFFFFFF) // 30% white: stronger top highlight
val GlassShadow = Color(0x0D000000) // 5% black: subtle shadow for depth
val AmbientRed = Color(0x1AD71920) // 10% red: soft red ambience
val AmbientWhite = Color(0x0AFFFFFF) // 4% white: depth ambience

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

// Enhanced typography for glass contexts with slightly increased tracking
// for better readability on translucent backgrounds.
val GlassTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 96.sp,
        lineHeight = 96.sp,
        letterSpacing = (-1).sp, // slightly looser tracking on glass
    ),
    titleLarge = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.2.sp, // slightly looser tracking on glass
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.2.sp, // slightly looser tracking on glass
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp, // slightly looser tracking on glass
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
