package com.bathealth.app.ui.components

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bathealth.app.ui.theme.AmbientRed
import com.bathealth.app.ui.theme.AmbientWhite
import com.bathealth.app.ui.theme.GlassBorder
import com.bathealth.app.ui.theme.GlassHighlight
import com.bathealth.app.ui.theme.GlassShadow
import com.bathealth.app.ui.theme.GlassSurface
import com.bathealth.app.ui.theme.GeistMono
import com.bathealth.app.ui.theme.GlowRed
import com.bathealth.app.ui.theme.GlowWhite
import com.bathealth.app.ui.theme.LucentSurfaceStrong
import com.bathealth.app.ui.theme.Muted
import com.bathealth.app.ui.theme.NothingBlack
import com.bathealth.app.ui.theme.NothingBlackSoft
import com.bathealth.app.ui.theme.NothingRed

@Composable
fun MicroLabel(text: String, color: Color = Color.White) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun AnimatedAmbientGlow() {
    val alpha = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(AmbientRed.copy(alpha = alpha.value), Color.Transparent),
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
                    colors = listOf(AmbientWhite.copy(alpha = alpha.value), Color.Transparent),
                    center = Offset(120f, 1900f),
                    radius = 1300f,
                ),
            ),
    )
}

@Composable
fun DynamicGradient(level: Int) {
    val redIntensity = (level / 100f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        NothingBlack,
                        NothingBlackSoft,
                        NothingBlack.copy(
                            red = redIntensity * 0.05f,
                            green = 0f,
                            blue = 0f,
                        ),
                    ),
                ),
            ),
    )
}

@Composable
fun FluentBackground(level: Int) {
    DynamicGradient(level)
    AnimatedAmbientGlow()
}

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GlassHighlight.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2, 0f),
                        radius = size.width * 0.8f,
                    ),
                )
            }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
            )
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
                            GlassHighlight,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun GlassCardVariant(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GlassHighlight.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2, 0f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
            )
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
                            GlassHighlight,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun FluentBottomBar(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LucentSurfaceStrong)
            .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FluentTab(
                label = "HOME",
                selected = currentRoute == "home",
                onClick = { onSelect("home") },
            )
            FluentTab(
                label = "CHARGE",
                selected = currentRoute == "charge",
                onClick = { onSelect("charge") },
            )
            FluentTab(
                label = "HEALTH",
                selected = currentRoute == "health",
                onClick = { onSelect("health") },
            )
        }
    }
}

@Composable
private fun FluentTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .background(if (selected) GlassSurface else Color.Transparent)
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
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
            fontWeight = FontWeight.Medium,
            color = Color.White,
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
}

@Composable
fun GlassChargePill(charging: Boolean, source: String) {
    val bg = if (charging) NothingRed else GlassSurface
    val fg = Color.White
    val glowAlpha by animateFloatAsState(
        targetValue = if (charging) 0.3f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "glow",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, GlassBorder, RoundedCornerShape(50))
            .drawBehind {
                if (charging) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NothingRed.copy(alpha = glowAlpha),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.6f,
                        ),
                    )
                }
            }
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
            if (isLit) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = r * 1.5f,
                    center = Offset(cx, size.height / 2),
                )
            }
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
                .background(GlassBorder),
        )
    }
}
