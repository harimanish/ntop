package com.ntop.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.collectAsState
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ntop.app.stats.GlyphMode
import com.ntop.app.stats.GlyphSettings
import com.ntop.app.stats.hasUsageAccess
import com.ntop.app.stats.audioPlaying
import com.ntop.app.stats.glyphSettings
import com.ntop.app.stats.setGlyphAppMode
import com.ntop.app.stats.setGlyphCharging
import com.ntop.app.stats.setGlyphUsername
import com.ntop.app.ui.theme.LucentBorder
import com.ntop.app.ui.theme.LucentSurface
import com.ntop.app.ui.theme.Muted
import com.ntop.app.ui.theme.NothingRed
import kotlinx.coroutines.launch

// No cap: the app the user actually wants (their music player) is typically
// late in the alphabet, and a truncated list hid exactly the row they needed.
// The list lives in a scrolling Column, so the cost is just composables.
private const val APP_LIMIT = 64

/** Tapping an app cycles its assignment; [GlyphMode] order is the cycle order. */
private val CYCLE: List<GlyphMode?> =
    listOf(null, GlyphMode.SMILEY, GlyphMode.USERNAME, GlyphMode.WATTS, GlyphMode.WAVEFORM)

private fun next(current: GlyphMode?): GlyphMode? {
    val i = CYCLE.indexOf(current)
    return CYCLE[(i + 1) % CYCLE.size]
}

private data class AppRow(val pkg: String, val label: String)

/**
 * Installed, launchable apps, most-recently-known first is not available
 * without extra queries, so this is alphabetical — deterministic and cheap.
 */
private fun launchableApps(context: Context): List<AppRow> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = try {
        pm.queryIntentActivities(intent, 0)
    } catch (e: Exception) {
        return emptyList()
    }
    return resolved
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            AppRow(pkg, label)
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
        .take(APP_LIMIT)
}

@Composable
fun GlyphConfigSection(accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { glyphSettings(context) }
        .collectAsState(initial = GlyphSettings())
    val apps = remember { launchableApps(context) }
    var draft by remember { mutableStateOf<String?>(null) }

    GlassCardVariant {
        MicroLabel("ALWAYS-ON GLYPH", color = Muted)
        Spacer(Modifier.height(10.dp))

        // Username: the animated default shown whenever nothing else matches.
        MicroLabel("USERNAME", color = Muted)
        Spacer(Modifier.height(6.dp))
        val shown = draft ?: settings.username
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LucentSurface)
                .border(1.dp, LucentBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = shown,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (shown.isEmpty()) {
                        MicroLabel("ntop", color = Muted)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    scope.launch {
                        setGlyphUsername(context, shown)
                        draft = null
                    }
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            MicroLabel("SAVE", color = accent)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MicroLabel("SHOW WATTS WHILE CHARGING", color = Muted)
            }
            Switch(
                checked = settings.chargingEnabled,
                onCheckedChange = { scope.launch { setGlyphCharging(context, it) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accent,
                    checkedTrackColor = accent.copy(alpha = 0.35f),
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = LucentSurface,
                    uncheckedBorderColor = LucentBorder,
                ),
            )
        }

        Spacer(Modifier.height(6.dp))
        MicroLabel("AUDIO NOW: ${if (audioPlaying(context)) "PLAYING" else "IDLE"}", color = Muted)
    }

    // Without Usage Access the toy cannot see which app is in the foreground,
    // so every per-app assignment below is silently ignored. Say so rather
    // than letting the feature look broken.
    if (!hasUsageAccess(context)) {
        GlassCardVariant {
            MicroLabel("USAGE ACCESS OFF", color = NothingRed)
            Spacer(Modifier.height(4.dp))
            MicroLabel("PER-APP GLYPHS NEED IT", color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: Exception) {
                        }
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                MicroLabel("OPEN SETTINGS", color = accent)
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    Spacer(Modifier.height(12.dp))
    MicroLabel("PER-APP GLYPH")
    Spacer(Modifier.height(4.dp))
    MicroLabel("TAP TO CYCLE: OFF · SMILEY · NAME · WATTS · WAVE", color = Muted)
    Spacer(Modifier.height(8.dp))

    GlassCardVariant {
        apps.forEachIndexed { index, app ->
            if (index > 0) {
                Spacer(Modifier.height(2.dp))
            }
            val assigned = settings.appModes[app.pkg]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        scope.launch { setGlyphAppMode(context, app.pkg, next(assigned)) }
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    MicroLabel(app.label)
                    Spacer(Modifier.height(2.dp))
                    MicroLabel(app.pkg, color = Muted)
                }
                Spacer(Modifier.width(10.dp))
                MicroLabel(assigned?.name ?: "OFF", color = if (assigned != null) accent else Muted)
            }
        }
        if (apps.isEmpty()) {
            MicroLabel("NO LAUNCHABLE APPS FOUND", color = Muted)
        }
    }
}
