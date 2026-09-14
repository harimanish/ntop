package com.bathealth.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bathealth.app.ui.theme.GeistMono
import com.bathealth.app.ui.theme.GlowRed
import com.bathealth.app.ui.theme.GlowWhite
import com.bathealth.app.ui.theme.LucentBorder
import com.bathealth.app.ui.theme.LucentHighlight
import com.bathealth.app.ui.theme.LucentSurface
import com.bathealth.app.ui.theme.LucentSurfaceStrong
import com.bathealth.app.ui.theme.Muted
import com.bathealth.app.ui.theme.NothingBlack
import com.bathealth.app.ui.theme.NothingBlackSoft
import com.bathealth.app.ui.theme.NothingRed
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

@Composable
fun MicroLabel(text: String, color: Color = Color.White) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun DotMatrixHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(NothingRed),
        )
        Spacer(Modifier.width(10.dp))
        MicroLabel("BATHEALTH")
    }
    Spacer(Modifier.height(14.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val gap = 14.dp.toPx()
        val r = 2.dp.toPx()
        var x = r
        while (x < size.width) {
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = r,
                center = Offset(x, size.height / 2),
            )
            x += gap
        }
    }
}

// ---------------------------------------------------------------------------
// Lucent background: monochrome depth gradient + soft ambient glows.
// The glows are radial gradients (soft by construction), so the frosted
// Lucent layers above them read as frosted glass on every API level.
// ---------------------------------------------------------------------------
@Composable
fun LucentBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(NothingBlack, NothingBlackSoft, NothingBlack)),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(GlowRed, Color.Transparent),
                        center = Offset(950f, -80f),
                        radius = 1100f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(GlowWhite, Color.Transparent),
                        center = Offset(120f, 1900f),
                        radius = 1300f,
                    ),
                ),
        )
    }
}

@Composable
fun ChargePill(charging: Boolean, source: String) {
    val bg = if (charging) NothingRed else LucentSurface
    val fg = Color.White
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (charging) "● CHARGING — $source" else "○ ON BATTERY",
            fontFamily = GeistMono,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

@Composable
fun GlyphDots(level: Int, cells: Int = 20) {
    val lit = (level * cells / 100f).toInt().coerceIn(0, cells)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val gap = size.width / cells
        val r = 5.dp.toPx().coerceAtMost(gap / 2 - 2.dp.toPx())
        for (i in 0 until cells) {
            val cx = gap * i + gap / 2
            drawCircle(
                color = if (i < lit) Color.White else Color.White.copy(alpha = 0.14f),
                radius = r,
                center = Offset(cx, size.height / 2),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// LucentCard v2: frosted layer + hairline border + inner top highlight.
// ---------------------------------------------------------------------------
@Composable
fun LucentCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LucentSurface)
            .border(1.dp, LucentBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            LucentHighlight,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun TelemetryRow(label: String, value: String, last: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicroLabel(label, color = Muted)
        Text(
            text = value,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            color = Color.White,
        )
    }
    if (!last) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

// ---------------------------------------------------------------------------
// Lucent bottom bar: Nothing-style tab switcher, floating frosted glass.
// Content scrolls behind it; Haze blurs the backdrop on Android 12+ and
// draws the translucent tint as a scrim below that.
// ---------------------------------------------------------------------------
@Composable
fun LucentBottomBar(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val frosted = if (hazeState != null) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = LucentSurfaceStrong,
                    tints = emptyList(),
                    blurRadius = 28.dp,
                    noiseFactor = 0f,
                ),
            )
    } else {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LucentSurfaceStrong)
    }
    Column(
        modifier = frosted
            .border(1.dp, LucentBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucentTab(
                label = "HOME",
                selected = currentRoute == "home",
                onClick = { onSelect("home") },
            )
            LucentTab(
                label = "CHARGE",
                selected = currentRoute == "charge",
                onClick = { onSelect("charge") },
            )
            LucentTab(
                label = "HEALTH",
                selected = currentRoute == "health",
                onClick = { onSelect("health") },
            )
        }
    }
}

@Composable
private fun LucentTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .background(if (selected) LucentSurface else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) NothingRed else Color.White.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
            color = if (selected) Color.White else Muted,
        )
    }
}
