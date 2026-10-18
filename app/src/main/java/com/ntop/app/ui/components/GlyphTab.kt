package com.ntop.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ntop.app.BatteryInfo
import com.ntop.app.stats.AudioCapture
import com.ntop.app.stats.GlyphGroup
import com.ntop.app.stats.GlyphMode
import com.ntop.app.stats.GlyphPreviewKit
import com.ntop.app.stats.GlyphSettings
import com.ntop.app.stats.GlyphTrigger
import com.ntop.app.stats.GlyphWave
import com.ntop.app.stats.MonitorState
import com.ntop.app.stats.audioPlaying
import com.ntop.app.stats.foregroundApp
import com.ntop.app.stats.glyphMatrixLength
import com.ntop.app.stats.glyphSettings
import com.ntop.app.stats.hasUsageAccess
import com.ntop.app.stats.quantizeWatts
import com.ntop.app.stats.resolveMode
import com.ntop.app.stats.setGlyphCharging
import com.ntop.app.stats.setGlyphDefaultMode
import com.ntop.app.stats.setGlyphGroups
import com.ntop.app.stats.setGlyphUsername
import com.ntop.app.stats.setGlyphWave
import com.ntop.app.ui.theme.LucentBorder
import com.ntop.app.ui.theme.LucentSurface
import com.ntop.app.ui.theme.LucentSurfaceStrong
import com.ntop.app.ui.theme.Muted
import com.ntop.app.ui.theme.NothingRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

// ---------------------------------------------------------------------------
// GLYPH tab — the toy's own page.
//
// Layout follows the Glyph Museum's detail screen (device render hero with a
// live matrix on it, mode pills, group cards). The live preview is not an
// illustration: GlyphPreviewKit builds the exact frame the service would push,
// through the same resolveMode the toy runs, and the eye button pauses/plays
// the animation. Red stays reserved for charging; the accent is the user's
// colour everywhere else.
// ---------------------------------------------------------------------------

private const val APP_LIMIT = 64

/** Pills for the always-on default mode, in display order. */
private val DEFAULT_PILLS =
    listOf(GlyphMode.SMILEY, GlyphMode.USERNAME, GlyphMode.WATTS, GlyphMode.WAVEFORM)

/** User-facing name: the mode enum says WATTS, the UI says POWER. */
private fun modeLabel(mode: GlyphMode?): String = when (mode) {
    null -> "OFF"
    GlyphMode.WATTS -> "POWER"
    else -> mode.name
}

private fun triggerLabel(trigger: GlyphTrigger): String = when (trigger) {
    GlyphTrigger.IN_USE -> "IN USE"
    GlyphTrigger.PLAYING -> "PLAYING"
}

/** A fresh, unsaved group for the editor. */
private fun newGroup() = GlyphGroup(id = UUID.randomUUID().toString(), name = "")

private data class AppRow(val pkg: String, val label: String)

/**
 * Installed, launchable apps. No cap here — the display list is capped after
 * filtering, so a search can still find apps past the first 64 alphabetically.
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
}

@Composable
fun GlyphTabContent(info: BatteryInfo, mon: MonitorState, tick: Int, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val matrix = remember { glyphMatrixLength() }

    if (matrix <= 0) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            MicroLabel("GLYPH MATRIX")
            Spacer(Modifier.height(8.dp))
            GlassCardVariant {
                MicroLabel("NO GLYPH MATRIX ON THIS DEVICE", color = Muted)
            }
            Spacer(Modifier.height(150.dp))
        }
        return
    }

    val kit = remember(matrix) { GlyphPreviewKit(context, matrix) }
    val settings by remember { glyphSettings(context) }.collectAsState(initial = GlyphSettings())
    val apps = remember { launchableApps(context) }
    var live by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<GlyphGroup?>(null) }
    var fg by remember { mutableStateOf<String?>(null) }
    var waveEdit by remember { mutableStateOf(false) }
    var waveMirror by remember(settings.wave) { mutableStateOf(settings.wave.mirror) }
    var sensDraft by remember(settings.wave) { mutableStateOf(settings.wave.sensitivity) }
    var smoothDraft by remember(settings.wave) { mutableStateOf(settings.wave.smoothing) }
    val commitWave: () -> Unit = {
        scope.launch { setGlyphWave(context, GlyphWave(waveMirror, sensDraft, smoothDraft)) }
    }
    var audioGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        audioGranted = granted
        // Clear the retry cooldown so the visualizer can come up on the next
        // frame instead of waiting out the backoff.
        if (granted) AudioCapture.stop()
    }

    // The foreground query is only meaningful (and only paid for) when a
    // group lists apps; the service caches it on the same schedule.
    LaunchedEffect(tick) {
        if (settings.groups.any { it.apps.isNotEmpty() } && tick % 2 == 0) {
            fg = foregroundApp(context)
        }
    }

    val playing = remember(tick) { audioPlaying(context) }

    // The exact resolution the service runs, so the preview cannot lie.
    val resolved: GlyphMode = settings.resolveMode(
        charging = info.charging,
        foregroundPkg = fg,
        audioPlaying = playing,
    )
    val ma = mon.stableMa ?: info.avgCurrentMa
    val watts = if (!info.voltageV.isNaN() && ma != null) {
        quantizeWatts(abs(info.voltageV * ma / 1000f))
    } else {
        null
    }
    // The toy draws watts only while charging; otherwise the frame is battery %.
    val glyphWatts = if (info.charging) watts else null
    val username = settings.username

    val deviceName = remember {
        (resolveDeviceRender()?.name ?: Build.MODEL).uppercase(Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        MicroLabel("ALWAYS-ON GLYPH")
        Spacer(Modifier.height(8.dp))

        // Hero: official render, zoomed into the glyph circle so the live
        // frame sits exactly over the hardware disc (geometry measured off
        // the CDN image; unknown devices get the plate without a photo).
        GlassCardVariant {
            Row(verticalAlignment = Alignment.Top) {
                HeroDevice(
                    kit = kit,
                    mode = resolved,
                    username = username,
                    pct = info.level,
                    watts = glyphWatts,
                    playing = playing,
                    live = live,
                    matrix = matrix,
                    wave = settings.wave,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MicroLabel(deviceName)
                            Spacer(Modifier.height(2.dp))
                            MicroLabel("${matrix}×${matrix} MATRIX", color = Muted)
                        }
                        LiveToggle(live = live, accent = accent) { live = !live }
                    }
                    Spacer(Modifier.height(12.dp))
                    LiveMatrixPreview(
                        kit = kit,
                        mode = resolved,
                        username = username,
                        pct = info.level,
                        watts = glyphWatts,
                        playing = playing,
                        live = live,
                        matrix = matrix,
                        hardware = true,
                        wave = settings.wave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { waveEdit = !waveEdit },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("MATRIX NOW", color = Muted)
                Text(
                    text = modeLabel(resolved) + if (live) "" else " · PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }

            // Waveform look editor: the same knobs Glyph Beat exposes when
            // its visualizer is tapped; shared with the toy via DataStore.
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { waveEdit = !waveEdit }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("WAVEFORM LOOK", color = Muted)
                MicroLabel(if (waveEdit) "HIDE" else "EDIT", color = accent)
            }
            if (waveEdit) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModePill(
                        label = "LEFT · RIGHT",
                        selected = !waveMirror,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    ) {
                        waveMirror = false
                        commitWave()
                    }
                    ModePill(
                        label = "BASS · CENTRE",
                        selected = waveMirror,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    ) {
                        waveMirror = true
                        commitWave()
                    }
                }
                Spacer(Modifier.height(4.dp))
                MicroLabel(
                    "SENSITIVITY · ${"%.2f".format(Locale.US, sensDraft)}×",
                    color = Muted,
                )
                Slider(
                    value = sensDraft,
                    onValueChange = { sensDraft = it },
                    onValueChangeFinished = { commitWave() },
                    valueRange = 0.5f..3f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                )
                Spacer(Modifier.height(2.dp))
                MicroLabel("SMOOTHING · ${(smoothDraft * 100).toInt()}%", color = Muted)
                Slider(
                    value = smoothDraft,
                    onValueChange = { smoothDraft = it },
                    onValueChangeFinished = { commitWave() },
                    valueRange = 0.5f..0.95f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                )
            }
        }

        // AudioFlinger gates the session-0 visualizer on RECORD_AUDIO, so
        // without the grant the waveform quietly falls back to the synthetic
        // shape (the AUDIO row shows PLAYING instead of PLAYING · LIVE).
        if (!audioGranted) {
            Spacer(Modifier.height(8.dp))
            GlassCardVariant {
                MicroLabel("LIVE AUDIO OFF", color = NothingRed)
                Spacer(Modifier.height(4.dp))
                MicroLabel("GRANT AUDIO ACCESS FOR THE REAL WAVEFORM", color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) {
                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    MicroLabel("ALLOW", color = accent)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        MicroLabel("DEFAULT MODE")
        Spacer(Modifier.height(4.dp))
        MicroLabel("USED WHEN NO GROUP MATCHES", color = Muted)
        Spacer(Modifier.height(8.dp))
        GlassCardVariant {
            DEFAULT_PILLS.chunked(2).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { mode ->
                        ModePill(
                            label = modeLabel(mode),
                            selected = settings.defaultMode == mode,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                        ) {
                            scope.launch { setGlyphDefaultMode(context, mode) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
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
                    MicroLabel("POWER WHILE CHARGING", color = Muted)
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
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicroLabel("GLYPH GROUPS")
            MicroLabel(
                if (settings.groups.size == 1) "1 GROUP" else "${settings.groups.size} GROUPS",
                color = accent,
            )
        }
        Spacer(Modifier.height(4.dp))
        MicroLabel("A MATCHING GROUP OVERRIDES THE DEFAULT", color = Muted)
        Spacer(Modifier.height(8.dp))

        // Without Usage Access the toy cannot see which app is in the
        // foreground, so IN USE groups are silently ignored (PLAYING groups
        // still match on audio alone).
        if (!hasUsageAccess(context)) {
            GlassCardVariant {
                MicroLabel("USAGE ACCESS OFF", color = NothingRed)
                Spacer(Modifier.height(4.dp))
                MicroLabel("IN USE GROUPS NEED IT TO MATCH", color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: Exception) {
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    MicroLabel("OPEN SETTINGS", color = accent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val draftGroup = editing
        if (draftGroup == null) {
            GlassCardVariant {
                if (settings.groups.isEmpty()) {
                    MicroLabel("NO GROUPS YET", color = Muted)
                    Spacer(Modifier.height(4.dp))
                    MicroLabel("TRY MUSIC: PLAYERS · WAVEFORM · PLAYING", color = Muted)
                } else {
                    settings.groups.forEach { group ->
                        GroupRow(
                            kit = kit,
                            matrix = matrix,
                            group = group,
                            username = username,
                            accent = accent,
                        ) {
                            query = ""
                            editing = group
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) {
                        query = ""
                        editing = newGroup()
                    }
                    .background(LucentSurfaceStrong)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                MicroLabel("+ NEW GROUP", color = accent)
            }
        } else {
            GroupEditor(
                draft = draftGroup,
                onDraft = { editing = it },
                apps = apps,
                query = query,
                onQuery = { query = it },
                accent = accent,
                existing = settings.groups.any { it.id == draftGroup.id },
                onSave = {
                    val saved = draftGroup.copy(
                        name = draftGroup.name.trim().ifEmpty { modeLabel(draftGroup.mode) },
                    )
                    val updated = if (settings.groups.any { it.id == saved.id }) {
                        settings.groups.map { if (it.id == saved.id) saved else it }
                    } else {
                        settings.groups + saved
                    }
                    scope.launch { setGlyphGroups(context, updated) }
                    editing = null
                },
                onDelete = if (settings.groups.any { it.id == draftGroup.id }) {
                    {
                        scope.launch {
                            setGlyphGroups(
                                context,
                                settings.groups.filterNot { it.id == draftGroup.id },
                            )
                        }
                        editing = null
                    }
                } else {
                    null
                },
                onCancel = { editing = null },
            )
        }

        Spacer(Modifier.height(24.dp))
        MicroLabel("TOY")
        Spacer(Modifier.height(8.dp))
        GlassCardVariant {
            GlassTelemetryRow("TOY", "NTOP · AOD")
            GlassTelemetryRow("MATRIX", "${matrix}×${matrix}")
            GlassTelemetryRow(
                "AUDIO",
                when {
                    !playing -> "IDLE"
                    AudioCapture.isLive() -> "PLAYING · LIVE"
                    else -> "PLAYING"
                },
                last = true,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button) {
                    try {
                        context.startActivity(
                            Intent().apply {
                                component = android.content.ComponentName(
                                    "com.nothing.thirdparty",
                                    "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
                                )
                            },
                        )
                    } catch (_: Exception) {
                    }
                }
                .background(LucentSurfaceStrong)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            MicroLabel("ADD TO ALWAYS-ON GLYPH")
        }
        Spacer(Modifier.height(150.dp))
    }
}

// ---------------------------------------------------------------------------
// Live preview
// ---------------------------------------------------------------------------

/** Everything one rendered frame depends on, read fresh on every step. */
private data class PreviewInput(
    val mode: GlyphMode?,
    val username: String,
    val pct: Int,
    val watts: Float?,
    val playing: Boolean,
    val wave: GlyphWave = GlyphWave(),
)

/**
 * Runs the preview loop and draws it. The loop lives here, not in
 * GlyphTabContent, so a 10 fps animation only recomposes this one node and
 * never the 64 per-app rows around it.
 */
@Composable
private fun LiveMatrixPreview(
    kit: GlyphPreviewKit,
    mode: GlyphMode?,
    username: String,
    pct: Int,
    watts: Float?,
    playing: Boolean,
    live: Boolean,
    matrix: Int,
    hardware: Boolean,
    wave: GlyphWave = GlyphWave(),
    modifier: Modifier = Modifier,
) {
    var data by remember(kit) { mutableStateOf(IntArray(matrix * matrix)) }
    val latest by rememberUpdatedState(PreviewInput(mode, username, pct, watts, playing, wave))

    LaunchedEffect(kit, mode, live, wave) {
        if (!live) {
            // One frame so a paused preview still shows the current mode.
            val inp = latest
            data = kit.frame(inp.mode, 0, inp.username, inp.pct, inp.watts, inp.playing, inp.wave)
            return@LaunchedEffect
        }
        var step = 0
        while (isActive) {
            val inp = latest
            data = kit.frame(inp.mode, step, inp.username, inp.pct, inp.watts, inp.playing, inp.wave)
            step++
            // Meter modes only change when the 1 s sample does; animations
            // follow the toy's awake cadence (90-100 ms).
            delay(if (inp.mode == GlyphMode.WATTS) 1000L else 90L)
        }
    }

    MatrixPreview(data = data, matrix = matrix, hardware = hardware, modifier = modifier)
}

/**
 * Phone (3) disc geometry, measured off the 1000x1000 CDN render.
 */
private const val P3_DISC_CX = 0.6235f
private const val P3_DISC_CY = 0.1515f
private const val P3_DISC_D = 0.1175f

/**
 * Zoomed crop of the official render with the live frame drawn exactly over
 * the hardware disc. The crop is centred on the upper-right corner of the
 * phone so the disc reads clearly. The lit cell field spans the whole
 * preview box, so the box is the disc itself, clipped to the circle — the
 * photo's own disc shows through around the LEDs.
 */
@Composable
private fun HeroDevice(
    kit: GlyphPreviewKit,
    mode: GlyphMode?,
    username: String,
    pct: Int,
    watts: Float?,
    playing: Boolean,
    live: Boolean,
    matrix: Int,
    wave: GlyphWave = GlyphWave(),
) {
    val url = remember {
        resolveDeviceRender()?.imageUrl?.replace("width=1000", "width=2000")
    }
    // Only the Phone (3) plate has measured disc geometry.
    if (url == null || matrix != 25) {
        LiveMatrixPreview(
            kit = kit, mode = mode, username = username, pct = pct, watts = watts,
            playing = playing, live = live, matrix = matrix, hardware = false,
            wave = wave,
            modifier = Modifier.size(144.dp),
        )
        return
    }
    val canvas = 544.dp
    val cropX = 245.dp
    val cropY = 28.dp
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var renderFailed by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        bitmap = null
        renderFailed = false
        val result = runCatching {
            context.imageLoader.execute(ImageRequest.Builder(context).data(url).build())
        }.getOrNull() as? SuccessResult
        if (result == null) {
            renderFailed = true
        } else {
            bitmap = (result.drawable as? BitmapDrawable)?.bitmap
        }
    }
    val loaded = bitmap != null
    Box(
        modifier = Modifier
            .width(144.dp)
            .height(240.dp)
            .clipToBounds(),
    ) {
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MicroLabel(
                    if (renderFailed) "RENDER UNAVAILABLE" else "LOADING RENDER",
                    color = Muted,
                )
            }
        }
        // The bitmap is drawn with an explicit transform inside a canvas that
        // IS the 144x240 viewport, so the photo can never drift or be clipped
        // away by parent constraints the way a sized+offset layout child was.
        Canvas(Modifier.fillMaxSize()) {
            val bmp = bitmap ?: return@Canvas
            if (bmp.width <= 0) return@Canvas
            val scaleFactor = canvas.toPx() / bmp.width
            translate(left = -cropX.toPx(), top = -cropY.toPx()) {
                scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                    drawImage(bmp.asImageBitmap())
                }
            }
        }
        val disc = canvas * P3_DISC_D
        val box = disc
        Box(
            modifier = Modifier
                .size(disc)
                .offset(
                    x = canvas * P3_DISC_CX - cropX - disc / 2,
                    y = canvas * P3_DISC_CY - cropY - disc / 2,
                )
                .clip(CircleShape),
        ) {
            LiveMatrixPreview(
                kit = kit, mode = mode, username = username, pct = pct, watts = watts,
                playing = playing, live = live, matrix = matrix, hardware = false,
                wave = wave,
                modifier = Modifier
                    .size(box)
                    .offset(x = -(box - disc) / 2, y = -(box - disc) / 2),
            )
        }
    }
}

/**
 * The matrix as hardware shows it, drawn in the Glyph Museum editor's style:
 * identical rounded-square LEDs whether lit or not — brightness is the only
 * thing that changes — sitting on a faint disc substrate with a thin rim.
 * With [hardware] off, only lit cells are drawn, for overlaying on the
 * device render where the photo's own disc shows through.
 *
 * LEDs are kept when their centre falls inside the disc (half of the box),
 * which is the museum's rule, so the outermost LEDs stop exactly at the rim.
 */
@Composable
fun MatrixPreview(
    data: IntArray,
    matrix: Int,
    modifier: Modifier = Modifier,
    hardware: Boolean = true,
) {
    Canvas(modifier) {
        if (matrix <= 0) return@Canvas
        val s = size.minDimension
        if (s <= 0f) return@Canvas
        val originX = (size.width - s) / 2f
        val originY = (size.height - s) / 2f
        val centre = Offset(originX + s / 2f, originY + s / 2f)
        val radius = s / 2f
        val cell = s / matrix
        // Museum numbers: LED side 0.80 of the pitch, corner radius a
        // quarter of the side, substrate ~7% white, rim ~30%.
        val side = cell * 0.80f
        val corner = CornerRadius(side * 0.25f)
        if (hardware) {
            drawCircle(color = Color.White.copy(alpha = 0.07f), radius = radius, center = centre)
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = radius,
                center = centre,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        val cells = if (data.size == matrix * matrix) data else IntArray(matrix * matrix)
        for (j in 0 until matrix) {
            for (i in 0 until matrix) {
                val fx = (i + 0.5f) / matrix * 2f - 1f
                val fy = (j + 0.5f) / matrix * 2f - 1f
                if (fx * fx + fy * fy > 1f) continue
                val v = cells[j * matrix + i]
                if (v <= 0 && !hardware) continue
                drawRoundRect(
                    color = if (v > 0) {
                        Color.White.copy(alpha = (v / 255f).coerceIn(0f, 1f))
                    } else {
                        // An unlit LED reads as a dark square in the substrate.
                        Color.Black
                    },
                    topLeft = Offset(
                        originX + i * cell + (cell - side) / 2f,
                        originY + j * cell + (cell - side) / 2f,
                    ),
                    size = Size(side, side),
                    cornerRadius = corner,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun LiveToggle(live: Boolean, accent: Color, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (live) accent.copy(alpha = 0.16f) else LucentSurface)
            .clickable(role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (live) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = if (live) "Pause live preview" else "Start live preview",
            tint = if (live) accent else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (selected) accent.copy(alpha = 0.16f) else LucentSurface)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.45f) else LucentBorder,
                shape = RoundedCornerShape(50),
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchField(query: String, accent: Color, onQuery: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LucentSurface)
            .border(1.dp, LucentBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    MicroLabel("SEARCH APPS", color = Muted)
                }
                inner()
            },
        )
    }
}

@Composable
private fun GroupRow(
    kit: GlyphPreviewKit,
    matrix: Int,
    group: GlyphGroup,
    username: String,
    accent: Color,
    onEdit: () -> Unit,
) {
    // One representative frame of the group's mode — a static render, not a
    // second animation; a moving preview per row would be paint noise.
    val mini = remember(kit, group.mode, username) {
        kit.frame(group.mode, 0, username, 72, null, false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MatrixPreview(
            data = mini,
            matrix = matrix,
            hardware = false,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MicroLabel(group.name.ifBlank { "UNTITLED" })
            Spacer(Modifier.height(2.dp))
            MicroLabel(
                (if (group.apps.size == 1) "1 APP" else "${group.apps.size} APPS") +
                    " · " + triggerLabel(group.trigger),
                color = Muted,
            )
        }
        Spacer(Modifier.width(10.dp))
        MicroLabel(modeLabel(group.mode), color = accent)
    }
}

@Composable
private fun GroupEditor(
    draft: GlyphGroup,
    onDraft: (GlyphGroup) -> Unit,
    apps: List<AppRow>,
    query: String,
    onQuery: (String) -> Unit,
    accent: Color,
    existing: Boolean,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    // Selected apps float to the top, then alphabetical; the search still
    // reaches past the cap because filtering happens first.
    val visible = remember(apps, query, draft.apps) {
        apps.asSequence()
            .filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
            .sortedWith(
                compareByDescending<AppRow> { draft.apps.contains(it.pkg) }
                    .thenBy { it.label.lowercase() },
            )
            .take(APP_LIMIT)
            .toList()
    }
    GlassCardVariant {
        MicroLabel(if (existing) "EDIT GROUP" else "NEW GROUP", color = accent)
        Spacer(Modifier.height(10.dp))

        MicroLabel("NAME", color = Muted)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LucentSurface)
                .border(1.dp, LucentBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = draft.name,
                onValueChange = { onDraft(draft.copy(name = it)) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (draft.name.isEmpty()) {
                        MicroLabel("MUSIC", color = Muted)
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        MicroLabel("SHOW", color = Muted)
        Spacer(Modifier.height(6.dp))
        DEFAULT_PILLS.chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    ModePill(
                        label = modeLabel(mode),
                        selected = draft.mode == mode,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    ) {
                        onDraft(draft.copy(mode = mode))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        MicroLabel("WHEN", color = Muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModePill(
                label = "IN USE",
                selected = draft.trigger == GlyphTrigger.IN_USE,
                accent = accent,
                modifier = Modifier.weight(1f),
            ) {
                onDraft(draft.copy(trigger = GlyphTrigger.IN_USE))
            }
            ModePill(
                label = "PLAYING",
                selected = draft.trigger == GlyphTrigger.PLAYING,
                accent = accent,
                modifier = Modifier.weight(1f),
            ) {
                onDraft(draft.copy(trigger = GlyphTrigger.PLAYING))
            }
        }
        Spacer(Modifier.height(6.dp))
        MicroLabel(
            if (draft.trigger == GlyphTrigger.PLAYING) {
                "WHILE AUDIO PLAYS · OR A GROUP APP IS OPEN"
            } else {
                "WHILE A GROUP APP IS OPEN"
            },
            color = Muted,
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicroLabel("APPS")
            MicroLabel("${draft.apps.size} SELECTED", color = accent)
        }
        Spacer(Modifier.height(6.dp))
        SearchField(query = query, accent = accent, onQuery = onQuery)
        Spacer(Modifier.height(6.dp))
        visible.forEach { app ->
            AppCheckRow(
                label = app.label,
                pkg = app.pkg,
                selected = draft.apps.contains(app.pkg),
                accent = accent,
            ) {
                val next = if (draft.apps.contains(app.pkg)) {
                    draft.apps - app.pkg
                } else {
                    draft.apps + app.pkg
                }
                onDraft(draft.copy(apps = next))
            }
        }
        if (visible.isEmpty()) {
            MicroLabel("NO MATCHING APPS", color = Muted)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onSave)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                MicroLabel("SAVE GROUP", color = accent)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
            ) {
                MicroLabel("CANCEL", color = Muted)
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClick = onDelete)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                ) {
                    MicroLabel("DELETE", color = NothingRed)
                }
            }
        }
    }
}

@Composable
private fun AppCheckRow(
    label: String,
    pkg: String,
    selected: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    width = 1.dp,
                    color = if (selected) accent.copy(alpha = 0.6f) else LucentBorder,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MicroLabel(label)
            Spacer(Modifier.height(2.dp))
            Text(
                text = pkg,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
