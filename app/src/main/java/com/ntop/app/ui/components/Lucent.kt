package com.ntop.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ntop.app.ui.theme.GeistMono
import com.ntop.app.ui.theme.LucentBorder
import com.ntop.app.ui.theme.LucentSurface
import com.ntop.app.ui.theme.LucentSurfaceStrong
import com.ntop.app.ui.theme.Muted
import com.ntop.app.ui.theme.NothingBlack
import com.ntop.app.ui.theme.NothingBlackSoft
import com.ntop.app.ui.theme.NothingRed
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

@Composable
fun MicroLabel(text: String, color: Color = Color.White) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun FluentBackground(level: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack),
    )
}

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NothingBlackSoft)
            .border(1.dp, LucentBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
fun GlassCardVariant(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NothingBlackSoft)
            .border(1.dp, LucentBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
fun FluentBottomBar(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color(0x990A0A0B),
                        blurRadius = 24.dp,
                        tints = emptyList(),
                    ),
                )
                .border(1.dp, LucentBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FluentTab(
                icon = Icons.Filled.ShowChart,
                contentDescription = "Monitor",
                selected = currentRoute == "mon",
                onClick = { onSelect("mon") },
            )
            FluentTab(
                icon = Icons.Filled.Apps,
                contentDescription = "Processes",
                selected = currentRoute == "proc",
                onClick = { onSelect("proc") },
            )
            FluentTab(
                icon = Icons.Filled.BatteryFull,
                contentDescription = "Battery",
                selected = currentRoute == "batt",
                onClick = { onSelect("batt") },
            )
            FluentTab(
                icon = Icons.Filled.MonitorHeart,
                contentDescription = "System",
                selected = currentRoute == "sys",
                onClick = { onSelect("sys") },
            )
        }
    }
}

@Composable
private fun FluentTab(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .background(if (selected) LucentSurface else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun GlassHeader() {
    val pulseAlpha = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        pulseAlpha.animateTo(
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }
    GlassCardVariant {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
                    .drawBehind {
                        drawCircle(
                            color = NothingRed.copy(alpha = pulseAlpha.value),
                            radius = size.width * 1.5f,
                        )
                    },
            )
            Spacer(Modifier.width(10.dp))
            MicroLabel("NTOP")
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
}

@Composable
fun GlassChargePill(charging: Boolean, source: String) {
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
fun GlassGlyphDots(level: Int, cells: Int = 20) {
    val animatedLevel by animateIntAsState(
        targetValue = level,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "level",
    )
    val lit = (animatedLevel * cells / 100f).toInt().coerceIn(0, cells)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val gap = size.width / cells
        val r = 5.dp.toPx().coerceAtMost(gap / 2 - 2.dp.toPx())
        for (i in 0 until cells) {
            val cx = gap * i + gap / 2
            val isLit = i < lit
            drawCircle(
                color = if (isLit) Color.White else Color.White.copy(alpha = 0.14f),
                radius = r,
                center = Offset(cx, size.height / 2),
            )
        }
    }
}

@Composable
fun GlassTelemetryRow(label: String, value: String, last: Boolean = false) {
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
