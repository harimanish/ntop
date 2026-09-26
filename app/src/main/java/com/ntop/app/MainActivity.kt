package com.ntop.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ntop.app.ui.components.FluentBackground
import com.ntop.app.ui.components.GlassCard
import com.ntop.app.ui.components.GlassCardVariant
import com.ntop.app.ui.components.GlassChargePill
import com.ntop.app.ui.components.GlassGlyphDots
import com.ntop.app.ui.components.Panel
import com.ntop.app.ui.components.PanelRow
import com.ntop.app.ui.components.DotGraph
import com.ntop.app.ui.components.CoreDots
import com.ntop.app.ui.components.GlyphConfigSection
import com.ntop.app.ui.components.GlassTelemetryRow
import com.ntop.app.ui.components.Phone3Render
import com.ntop.app.ui.components.FluentBottomBar
import com.ntop.app.ui.components.GraphCard
import com.ntop.app.ui.components.CoreBars
import com.ntop.app.ui.components.SpecGridCell
import com.ntop.app.ui.components.MicroLabel
import com.ntop.app.ui.components.resolveDeviceRender
import com.ntop.app.stats.BLUR_MAX
import com.ntop.app.stats.BLUR_MIN
import com.ntop.app.stats.BAR_ALPHA_MAX
import com.ntop.app.stats.BAR_ALPHA_MIN
import com.ntop.app.stats.MonitorState
import com.ntop.app.stats.abiText
import com.ntop.app.stats.cameraCounts
import com.ntop.app.stats.chipsetName
import com.ntop.app.stats.chipsetVendor
import com.ntop.app.stats.osVersionText
import com.ntop.app.stats.screenSpec
import com.ntop.app.stats.setAccent
import com.ntop.app.stats.setBarAlpha
import com.ntop.app.stats.setBlurDp
import com.ntop.app.stats.BatteryHealth
import com.ntop.app.stats.ThemeSettings
import com.ntop.app.stats.batteryHealth
import com.ntop.app.stats.observeThemeSettings
import com.ntop.app.stats.recordSession
import com.ntop.app.stats.ProcEntry
import com.ntop.app.stats.isShizukuReady
import com.ntop.app.stats.isShizukuInstalled
import com.ntop.app.stats.hasUsageAccess
import com.ntop.app.stats.shizukuProcTable
import com.ntop.app.stats.usageRanking
import com.ntop.app.stats.quantizeWatts
import com.ntop.app.stats.readMeminfoKb
import com.ntop.app.stats.loadSampleCache
import com.ntop.app.stats.RamInfo
import com.ntop.app.stats.RamApp
import com.ntop.app.stats.saveSampleCache
import com.nothing.ketchum.Common
import rikka.shizuku.Shizuku
import com.ntop.app.ui.theme.NtopTheme
import com.ntop.app.ui.theme.AccentColors
import com.ntop.app.ui.theme.AccentNames
import com.ntop.app.ui.theme.GeistMono
import com.ntop.app.ui.theme.LucentSurfaceStrong
import com.ntop.app.ui.theme.Muted
import com.ntop.app.ui.theme.NothingBlack
import com.ntop.app.ui.theme.NothingRed
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale

// ---------------------------------------------------------------------------
// Model: parsed from the sticky ACTION_BATTERY_CHANGED broadcast (no
// permission) plus the official BatteryManager health properties:
//
// - stateOfHealth: BATTERY_PROPERTY_STATE_OF_HEALTH (value 10) — remaining
//   estimated full charge capacity relative to rated capacity, in %.
//   Public since Android 16 (was SystemApi/BATTERY_STATS before); many OEM
//   Health HALs still omit it, so null renders as "—".
// - cycleCount: EXTRA_CYCLE_COUNT (API 34+), null below 34 or when absent.
// - currentMa: signed instantaneous current in mA (+ = charging, from the
//   unprivileged BATTERY_PROPERTY_CURRENT_NOW, µA / 1000). Absurd spikes and
//   Integer.MIN_VALUE (unsupported) map to null.
// - maxCurrentMa / maxVoltageV: hidden charger-limit extras
//   "max_charging_current" (µA) / "max_charging_voltage" (µV). @hide in the
//   SDK, so literal keys are used; absent on some OEMs → null.
// - chargeTimeMs: computeChargeTimeRemaining() (ms to full, -1 → null),
// powerW is derived: voltageV × currentMa / 1000 (signed, W).
@Stable
data class BatteryInfo(
    val level: Int = -1,
    val charging: Boolean = false,
    val source: String = "—",
    val tempC: Float = Float.NaN,
    val voltageV: Float = Float.NaN,
    val health: String = "—",
    val technology: String = "—",
    val stateOfHealth: Int? = null,
    val cycleCount: Int? = null,
    // Estimated full charge capacity (mAh), derived from the unprivileged
    // CHARGE_COUNTER + CAPACITY properties. Shown when the HAL / platform
    // gates the official SOH % behind BATTERY_STATS (signature|privileged).
    val fullCapMah: Int? = null,
    val currentMa: Float? = null,
    val avgCurrentMa: Float? = null,
    val maxCurrentMa: Float? = null,
    val maxVoltageV: Float? = null,
    val chargeTimeMs: Long? = null,
) {
    // Signed live power flow in watts, null when V or I is unknown.
    val powerW: Float?
        get() = if (!voltageV.isNaN() && currentMa != null) voltageV * currentMa / 1000f else null
}

fun Intent.toBatteryInfo(): BatteryInfo {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
    val pct = if (level >= 0) (level * 100 / scale) else -1
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val cycles = if (Build.VERSION.SDK_INT >= 34) {
        getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
    } else {
        null
    }
    // Hidden charger-limit extras (see model comment): µA / µV → mA / V.
    val maxMa = getIntExtra("max_charging_current", -1).takeIf { it > 0 }?.div(1000f)
    val maxV = getIntExtra("max_charging_voltage", -1).takeIf { it > 0 }?.div(1_000_000f)
    return BatteryInfo(
        level = pct,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL,
        source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "WIRELESS"
            else -> if (status == BatteryManager.BATTERY_STATUS_FULL) "FULL" else "BATTERY"
        },
        tempC = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1).takeIf { it >= 0 }
            ?.div(10f) ?: Float.NaN,
        voltageV = getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
            ?.div(1000f) ?: Float.NaN,
        health = when (getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            else -> "UNKNOWN"
        },
        technology = getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—",
        cycleCount = cycles,
        maxCurrentMa = maxMa,
        maxVoltageV = maxV,
    )
}

// Official state-of-health % via BatteryManager. The constant
// BATTERY_PROPERTY_STATE_OF_HEALTH (= 10) is still hidden in the SDK 36
// android.jar (public from Android 16 behind FLAG_STATE_OF_HEALTH_PUBLIC,
// AOSP bug 288842045), so the documented literal is used. Returns null when
// the device HAL does not publish it (common) instead of showing 0 or -1.
private const val BATTERY_PROPERTY_STATE_OF_HEALTH = 10

private fun Context.readStateOfHealth(): Int? = try {
    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    bm.getIntProperty(BATTERY_PROPERTY_STATE_OF_HEALTH).takeIf { it in 1..100 }
} catch (_: SecurityException) {
    // Expected on builds (like this Nothing OS) where the SOH flag is off:
    // fall back to the charge-counter capacity estimate below.
    null
} catch (_: Exception) {
    null
}

// Unprivileged estimate: full_mAh ≈ remaining_uAh / (level% / 100) / 1000.
// Tracks real capacity fade over time; compare against the rated ~mAh spec.
private fun Context.readFullCapacityMah(): Int? = try {
    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (counter > 0 && cap in 5..100) (counter / (cap / 100f) / 1000f).toInt().takeIf { it > 0 } else null
} catch (_: Exception) {
    null
}

private fun Context.enrichWithHealthProps(base: BatteryInfo): BatteryInfo =
    base.copy(
        stateOfHealth = readStateOfHealth(),
        fullCapMah = readFullCapacityMah(),
        currentMa = readCurrentMa(average = false),
        avgCurrentMa = readCurrentMa(average = true),
        chargeTimeMs = readChargeTimeMs(),
    )

// Signed instantaneous/average current in mA. getLongProperty-backed props
// collapse "unsupported" to 0 after the int cast, so exact 0 reads as null
// rather than fake precision; magnitudes above ±15 A are fuel-gauge garbage.
private fun Context.readCurrentMa(average: Boolean): Float? = try {
    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val raw = bm.getIntProperty(
        if (average) BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE
        else BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,
    )
    if (raw != 0 && raw != Int.MIN_VALUE && kotlin.math.abs(raw) <= 15_000_000) raw / 1000f else null
} catch (_: Exception) {
    null
}

private fun Context.readChargeTimeMs(): Long? = try {
    if (Build.VERSION.SDK_INT < 28) {
        null
    } else {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.computeChargeTimeRemaining().takeIf { it >= 0 }
    }
} catch (_: Exception) {
    null
}

// ---------------------------------------------------------------------------
// RAM: totals from /proc/meminfo (no permission) + per-app PSS via
// ActivityManager.getProcessMemoryInfo (own processes, no permission).
// Swap = SwapTotal - SwapFree; cached ≈ Cached + Buffers + SReclaimable.
// Per-app list covers live own processes; other apps need PACKAGE_USAGE_STATS
// or root, so the list is scoped to this app's processes.
// ---------------------------------------------------------------------------

private fun Context.readRamInfo(mem: Map<String, Long> = readMeminfoKb()): RamInfo = try {
    fun mb(key: String): Long = mem[key]?.let { it / 1024 } ?: -1
    val total = mb("MemTotal")
    val avail = mb("MemAvailable")
    val swapTotal = mb("SwapTotal")
    val swapFree = mb("SwapFree")
    val cached = listOf("Cached", "Buffers", "SReclaimable")
        .mapNotNull { mem[it] }.sum().let { if (mem.isEmpty()) -1 else it / 1024 }
    val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    // Per-app list is filled async (see RamContent); keep this call instant so
    // first draw never waits on the /proc walk.
    RamInfo(
        totalMb = total,
        availMb = avail,
        swapTotalMb = swapTotal,
        swapFreeMb = swapFree,
        cachedMb = cached,
        thresholdMb = if (memInfo.threshold > 0) (memInfo.threshold / (1024 * 1024)) else -1,
        lowMemory = memInfo.lowMemory,
        apps = emptyList(),
    )
} catch (_: Exception) {
    RamInfo()
}


// Per-app list: Android 10+ hides other apps' /proc entries from an app
// context, so only own processes are readable. Use ActivityManager PSS for
// those (no permission, instant) instead of a blocked /proc walk.
private fun Context.readRamApps(): List<RamApp> = try {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    am.runningAppProcesses.orEmpty().mapNotNull { procInfo ->
        val pssKb = try {
            am.getProcessMemoryInfo(intArrayOf(procInfo.pid)).firstOrNull()?.totalPss ?: 0
        } catch (_: Exception) {
            0
        }
        if (pssKb > 0) {
            RamApp(procInfo.processName.substringAfterLast(':').substringAfterLast('.').take(24), (pssKb / 1024).toLong())
        } else {
            null
        }
    }.sortedByDescending { it.pssMb }.take(12)
} catch (_: Exception) {
    emptyList()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NtopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    BatteryScreen()
                }
            }
        }
    }
}

@Composable
fun BatteryScreen() {
    val context = LocalContext.current
    val filter = remember { IntentFilter(Intent.ACTION_BATTERY_CHANGED) }
    var info by remember {
        val sticky = ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        mutableStateOf(
            (sticky?.toBatteryInfo() ?: BatteryInfo()).let { context.enrichWithHealthProps(it) },
        )
    }
    var ram by remember { mutableStateOf(context.readRamInfo()) }
    val mon = remember { MonitorState() }
    var health by remember { mutableStateOf<BatteryHealth?>(null) }
    var loopN by remember { mutableIntStateOf(0) }
    val ioScope = rememberCoroutineScope()

    // Cold start: hydrate from the last known sample so no page opens empty.
    // Runs off the main thread and lands within a few ms; the 1s live loop
    // below then takes over.
    var procRows by remember { mutableStateOf<List<ProcEntry>>(emptyList()) }
    LaunchedEffect(context) {
        val cached = loadSampleCache(context) ?: return@LaunchedEffect
        cached.applyTo(mon)
        cached.ram?.let { ram = it }
        procRows = cached.proc
        loopN++
    }

    // Initial health resolve (DataStore + map, no waiting for loop).
    LaunchedEffect(context) {
        health = batteryHealth(context)
    }

    // 1s refresh: broadcasts only fire on actual battery change, so poll the
    // sticky intent + fuel-gauge properties for live temp/voltage/current.
    // Also feeds the ntop monitor hub (histories + charge-session learner).
    // Sampling runs on Dispatchers.Default: this loop does file I/O
    // (/proc/meminfo, per-core cpufreq reads) and several BatteryManager binder
    // calls every second, and it was all running on the main thread. Only the
    // state writes below come back to the UI dispatcher.
    LaunchedEffect(context) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val current = withContext(Dispatchers.Default) {
                ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
            val base = current?.toBatteryInfo() ?: info
            val enriched = withContext(Dispatchers.Default) { context.enrichWithHealthProps(base) }
            info = enriched
            val kb = withContext(Dispatchers.Default) { readMeminfoKb() }
            ram = withContext(Dispatchers.Default) { context.readRamInfo(kb) }
            val session = mon.sample(
                context,
                charging = info.charging,
                levelPct = info.level,
                tempC = info.tempC,
                powerW = info.powerW,
                voltageV = info.voltageV,
                currentMa = info.currentMa,
                memKb = kb,
            )
            if (session != null) {
                ioScope.launch {
                    recordSession(context, session)
                    health = batteryHealth(context)
                }
            }
            loopN++
            if (loopN % 10 == 0) {
                ioScope.launch { health = batteryHealth(context) }
            }
            // Persist periodically so the next cold start paints from cache.
            // Every 10s is plenty: the cache only needs to be roughly recent.
            if (loopN % 10 == 0) {
                ioScope.launch { saveSampleCache(context, mon, ram, procRows) }
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                info = ctx.enrichWithHealthProps(intent.toBatteryInfo())
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    AppShell(info = info, ram = ram, mon = mon, health = health, tick = loopN, cachedProc = procRows)
}

@Composable
private fun AppShell(
    info: BatteryInfo,
    ram: RamInfo,
    mon: MonitorState,
    health: BatteryHealth?,
    tick: Int,
    cachedProc: List<ProcEntry> = emptyList(),
) {
    val routes = listOf("mon", "proc", "batt", "sys")
    val pagerState = rememberPagerState(pageCount = { routes.size })
    val scope = rememberCoroutineScope()
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val route = routes.getOrElse(currentPage) { "mon" }
    val hazeState = rememberHazeState()
    val context = LocalContext.current
    val theme by observeThemeSettings(context).collectAsState(
        initial = ThemeSettings(),
    )
    val accent = AccentColors.getOrElse(theme.accentIdx) { AccentColors[0] }

    // Overlay shell: pages fill the screen down to the gesture edge so scroll
    // content visibly passes behind the floating frosted bar.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        FluentBackground(level = info.level.coerceIn(0, 100))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .hazeSource(state = hazeState),
                pageSpacing = 16.dp,
            ) { page ->
                when (routes[page]) {
                    "mon" -> MonContent(mon, tick, accent)
                    "proc" -> ProcContent(ram, tick, cachedProc)
                    "sys" -> SysContent(info, mon, theme, health)
                    else -> BattContent(info, health, mon, tick)
                }
            }
        }

        // Floating Lucent footer: frosted tab bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            FluentBottomBar(
                currentRoute = route,
                hazeState = hazeState,
                barAlpha = theme.barAlphaPct / 100f,
                blurDp = theme.blurDp,
                accent = accent,
                onSelect = { dest ->
                    val page = routes.indexOf(dest).takeIf { it >= 0 } ?: 0
                    if (page != pagerState.currentPage) {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                },
            )
        }
    }
}

// Clearance so scroll ends clear the floating footer, not under it.
@Composable
private fun FooterClearance() {
    Spacer(Modifier.height(150.dp))
}

// Entrance animation removed: pages render instantly, no fade/slide.
// Kept as a pass-through so call sites stay unchanged.
@Composable
private fun EntranceItem(delayMs: Int, content: @Composable () -> Unit) {
    content()
}

// ---------------------------------------------------------------------------
// HOME: live overview — hero %, charging pill, Glyph dots.
@Composable
private fun HomeContent(info: BatteryInfo) {
    val animatedLevel by animateIntAsState(
        targetValue = info.level.coerceIn(0, 100),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "level",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        EntranceItem(0) { MicroLabel("BATTERY") }
        Spacer(Modifier.height(8.dp))
        EntranceItem(60) {
            GlassCardVariant {
                Text(
                    text = if (animatedLevel >= 0) "${animatedLevel}%" else "--%",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        EntranceItem(120) { GlassChargePill(charging = info.charging, source = info.source) }

        Spacer(Modifier.height(32.dp))
        // Glyph Matrix homage: segmented dot progress, 20 cells.
        EntranceItem(180) { GlassGlyphDots(level = info.level.coerceIn(0, 100)) }

        Spacer(Modifier.height(32.dp))
        // Device card: Phone (3) vector render + Build identity.
        EntranceItem(240) {             DeviceRenderImage(level = info.level.coerceIn(0, 100)) }

        Spacer(Modifier.height(32.dp))
        // Lucent translucent card: frosted layer + hairline border.
        EntranceItem(300) {
            GlassCard {
                GlassTelemetryRow("STATUS", if (info.charging) "CHARGING" else "DISCHARGING")
                GlassTelemetryRow(
                    "TEMP",
                    if (info.tempC.isNaN()) "—" else "${info.tempC}°C",
                )
                GlassTelemetryRow(
                    "VOLTAGE",
                    if (info.voltageV.isNaN()) "—" else "${info.voltageV} V",
                    last = true,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        // Second DeviceCard at the page end: stays reachable above the
        // floating bar and shows content drifting behind the frost.
        EntranceItem(360) {             DeviceRenderImage(level = info.level.coerceIn(0, 100)) }

        FooterClearance()
    }
}

// ---------------------------------------------------------------------------
// BATT: health % = current full-charge max / design capacity, plus the
// full telemetry card. Design chain: manual override -> PowerProfile ->
// Nothing model map; current max: session-learned (median of last 5,
// >=20% spans) else instant fuel-gauge estimate.
// Power section uses hold-last smoothing (mon.stableMa) + ETA fallback.
// ---------------------------------------------------------------------------
@Composable
private fun BattContent(info: BatteryInfo, health: BatteryHealth?, mon: MonitorState, tick: Int) {
    @Suppress("UNUSED_EXPRESSION")
    tick
    val pct = health?.healthPct
    val animatedPct by animateFloatAsState(
        targetValue = pct ?: -1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "hero",
    )
    val heroText = if (pct != null) "%.1f".format(Locale.getDefault(), animatedPct.coerceAtLeast(0f)) else "—"
    val designText = health?.designMah?.let { "%,d mAh".format(Locale.getDefault(), it) } ?: "—"
    val designSrc = health?.designSource ?: "UNKNOWN"
    val currentText = health?.currentMaxMah?.let { "≈%,d mAh".format(Locale.getDefault(), it.toInt()) } ?: "—"
    val currentSrc = if ((health?.learnedSessions ?: 0) >= 2) {
        "LEARNED(${health?.learnedSessions})"
    } else {
        "INSTANT"
    }
    val wearText = health?.wearPct?.let { "%.1f%%".format(Locale.getDefault(), it) } ?: "—"
    val cyclesText = info.cycleCount?.toString() ?: "—"
    val sohText = info.stateOfHealth?.let { "$it%" }
    // Stabilized power: the gauge current is EMA-smoothed in MonitorState, then
    // quantized to the 0.1 W the hero actually shows. Quantizing is what keeps
    // the last digit still — the spring below is for the transition between
    // real steps, not a substitute for the deadband.
    val ma = mon.stableMa ?: info.avgCurrentMa
    val watts = if (!info.voltageV.isNaN() && ma != null) {
        quantizeWatts(info.voltageV * ma / 1000f)
    } else {
        null
    }
    val animatedWatts by animateFloatAsState(
        targetValue = watts ?: 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "watts",
    )
    val hero = if (watts != null) "%+.1f".format(Locale.getDefault(), animatedWatts) else "—"
    val powerCurrentText = ma?.let { "%+.0f mA".format(Locale.getDefault(), it) } ?: "—"
    val maxMaText = info.maxCurrentMa?.let { "%.0f mA".format(Locale.getDefault(), it) } ?: "—"
    val maxVText = info.maxVoltageV?.let { "%.1f V".format(Locale.getDefault(), it) } ?: "—"
    // ETA: OS estimate first; fallback from remaining capacity / live current.
    val etaText = info.chargeTimeMs?.let { formatEta(it) } ?: run {
        val capMah = health?.currentMaxMah ?: health?.designMah?.toFloat()
        val chargeMa = ma?.takeIf { it > 0 }
        if (info.charging && capMah != null && chargeMa != null && info.level in 0..99) {
            val remainMah = capMah * (100 - info.level) / 100f
            "≈" + formatEta((remainMah / chargeMa * 3600_000).toLong())
        } else {
            "—"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        EntranceItem(0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("BATTERY HEALTH")
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (pct != null) Color(0xFF4CAF50) else Muted),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EntranceItem(60) {
            GlassCardVariant {
                Text(
                    text = buildAnnotatedString {
                        append(heroText)
                        if (pct != null) append("%")
                    },
                    style = TextStyle(
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        letterSpacing = (-1).sp,
                    ),
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Spacer(Modifier.height(32.dp))
        EntranceItem(180) {
            GlassCardVariant {
                GlassTelemetryRow("CONDITION", info.health)
                GlassTelemetryRow("CYCLES", cyclesText)
                GlassTelemetryRow("DESIGN", "$designText · $designSrc")
                GlassTelemetryRow("CURRENT MAX", "$currentText · $currentSrc")
                GlassTelemetryRow("WEAR", wearText)
                if (sohText != null) GlassTelemetryRow("SOH (HAL)", sohText)
                GlassTelemetryRow(
                    "TEMP",
                    if (info.tempC.isNaN()) "—" else "${info.tempC}°C",
                )
                GlassTelemetryRow(
                    "VOLTAGE",
                    if (info.voltageV.isNaN()) "—" else "${info.voltageV} V",
                )
                GlassTelemetryRow("CELL", info.technology, last = true)
            }
        }

        Spacer(Modifier.height(32.dp))
        EntranceItem(0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("POWER NOW")
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NothingRed),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EntranceItem(60) {
            GlassCardVariant {
                Text(
                    text = buildAnnotatedString {
                        append(hero)
                        if (watts != null) {
                            withStyle(
                                style = SpanStyle(
                                    fontSize = 24.sp,
                                    letterSpacing = 0.sp,
                                    color = Muted,
                                ),
                            ) {
                                append(" W")
                            }
                        }
                    },
                    style = TextStyle(
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        letterSpacing = (-1).sp,
                    ),
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EntranceItem(160) { GlassChargePill(charging = info.charging, source = info.source) }

        Spacer(Modifier.height(32.dp))
        EntranceItem(200) {
            GlassCardVariant {
                GlassTelemetryRow("CURRENT", powerCurrentText)
                GlassTelemetryRow(
                    "VOLTAGE",
                    if (info.voltageV.isNaN()) "—" else "${info.voltageV} V",
                    last = true,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        EntranceItem(220) {
            GlassCardVariant {
                GlassTelemetryRow("MAX CURRENT", maxMaText)
                GlassTelemetryRow("MAX VOLTAGE", maxVText)
                GlassTelemetryRow("TIME TO FULL", etaText)
                GlassTelemetryRow("SOURCE", info.source, last = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!info.charging) {
            MicroLabel("PLUG IN TO MEASURE CHARGE RATE", color = Muted)
        }
        FooterClearance()
    }
}

private fun formatEta(ms: Long): String {
    val mins = (ms / 60_000).toInt().coerceAtLeast(0)
    return if (mins < 60) "$mins MIN" else "${mins / 60}H ${mins % 60}M"
}

private fun formatMb(mb: Long): String = if (mb >= 0) "%,d".format(Locale.getDefault(), mb) else "—"

private fun formatBps(bps: Float): String = when {
    bps < 0 -> "—"
    bps < 1024 -> "%.0f B/s".format(Locale.getDefault(), bps)
    bps < 1024 * 1024 -> "%.1f KB/s".format(Locale.getDefault(), bps / 1024)
    else -> "%.1f MB/s".format(Locale.getDefault(), bps / (1024 * 1024))
}

private fun formatGb(mb: Long): String =
    if (mb >= 0) "%.1f GB".format(Locale.getDefault(), mb / 1024f) else "—"

// ---------------------------------------------------------------------------
// MON: btop-style overview — CPU freq + MEM/SWAP/NET history graphs.
// CPU load is a frequency proxy (cur/max per core): /proc/stat is denied
// to apps, so it is honestly labeled FREQ, not %.
// ---------------------------------------------------------------------------
@Composable
private fun MonContent(mon: MonitorState, tick: Int, accent: Color) {
    // tick is read (via the key below staying stable) to force refresh each
    // second: data-class samples are often equal tick-to-tick, which Compose
    // would otherwise treat as unchanged and skip.
    @Suppress("UNUSED_EXPRESSION")
    tick
    val cores = mon.cores
    val avgMhz = if (cores.isNotEmpty()) cores.map { it.curFreqKhz }.average() / 1000 else 0.0
    val maxMhz = cores.maxOfOrNull { it.maxFreqKhz } ?: 0L
    val mem = mon.mem
    val rxMax = mon.rxHist.maxOrZero().coerceAtLeast(1f)
    val txMax = mon.txHist.maxOrZero().coerceAtLeast(1f)

    // Phase 2: one framed compartment per subject with hairline-divided rows,
    // instead of a section header plus a card per stat. Same information, less
    // chrome, and noticeably less scrolling.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))

        Panel("CPU") {
            PanelRow("AVG FREQ", "%.0f MHz".format(Locale.getDefault(), avgMhz), showDivider = false)
            Spacer(Modifier.height(8.dp))
            DotGraph(
                values = mon.cpuHist.values(),
                max = 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                dotColor = accent,
            )
            Spacer(Modifier.height(14.dp))
            CoreDots(
                cores = cores.map { it.loadProxy },
                accent = accent,
            )
        }

        Spacer(Modifier.height(16.dp))

        Panel("MEMORY") {
            PanelRow(
                "RAM",
                if (mem.totalMb > 0) "${formatGb(mem.usedMb)} / ${formatGb(mem.totalMb)}" else "—",
                showDivider = false,
            )
            Spacer(Modifier.height(8.dp))
            DotGraph(
                values = mon.memHist.values(),
                max = 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                dotColor = accent,
            )
            Spacer(Modifier.height(14.dp))
            PanelRow(
                "SWAP",
                if (mem.swapTotalMb > 0) "${formatMb(mem.swapUsedMb)} MB" else "—",
            )
            Spacer(Modifier.height(8.dp))
            DotGraph(
                values = mon.swapHist.values(),
                max = 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                dotColor = accent,
            )
        }

        Spacer(Modifier.height(16.dp))

        Panel("NETWORK") {
            PanelRow("DOWN", formatBps(mon.net.rxBps), showDivider = false)
            Spacer(Modifier.height(8.dp))
            DotGraph(
                values = mon.rxHist.values(),
                max = rxMax,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                dotColor = accent,
            )
            Spacer(Modifier.height(14.dp))
            PanelRow("UP", formatBps(mon.net.txBps))
            Spacer(Modifier.height(8.dp))
            DotGraph(
                values = mon.txHist.values(),
                max = txMax,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                dotColor = accent,
            )
        }

        FooterClearance()
    }
}

// ---------------------------------------------------------------------------
// PROC: per-process table in three tiers — (1) Shizuku: full system PSS
// table, true btop; (2) UsageStats: top apps by foreground time (no RAM);
// (3) fallback: this app's own processes, the only attribution the OS
// grants a normal app. Sorted desc, refreshed every 3s.
// ---------------------------------------------------------------------------
@Composable
private fun ProcContent(ram: RamInfo, tick: Int, cachedRows: List<ProcEntry> = emptyList()) {
    val context = LocalContext.current
    var shizukuReady by remember { mutableStateOf(false) }
    var shizukuPresent by remember { mutableStateOf(false) }
    var usageOk by remember { mutableStateOf(false) }
    // Seeded from the cold-start cache so the table is populated before the
    // first Shizuku/usage query lands.
    var rows by remember { mutableStateOf(cachedRows) }

    DisposableEffect(context) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuReady = grantResult == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    // One-shot auto-request: pop the system grant dialog as soon as the
    // Shizuku binder is up but ntop isn't authorized yet (once per process
    // lifetime; the manual GRANT button below stays as fallback).
    var autoRequested by remember { mutableStateOf(false) }
    LaunchedEffect(shizukuPresent, shizukuReady) {
        if (shizukuPresent && !shizukuReady && !autoRequested) {
            autoRequested = true
            try {
                Shizuku.requestPermission(1001)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(tick) {
        if (tick % 3 != 0) return@LaunchedEffect
        shizukuReady = isShizukuReady()
        shizukuPresent = isShizukuInstalled(context)
        usageOk = hasUsageAccess(context)
        rows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when {
                shizukuReady -> shizukuProcTable(context)
                usageOk -> usageRanking(context)
                else -> context.readRamApps().map { ProcEntry(it.name, it.pssMb) }
            }
        }
    }

    val header = when {
        shizukuReady -> "PROCESSES · ALL APPS (PSS)"
        usageOk -> "TOP APPS · FOREGROUND 24H"
        else -> "PROCESSES · THIS APP (PSS)"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        MicroLabel(header)
        Spacer(Modifier.height(8.dp))
        GlassCard {
            if (rows.isEmpty()) {
                GlassTelemetryRow("PROCESSES", "—", last = true)
            } else {
                rows.forEachIndexed { index, app ->
                    val value = if (app.pssMb >= 0) {
                        if (app.pssMb > 0) "${formatMb(app.pssMb)} MB" else "<1 MB"
                    } else {
                        "${app.foregroundMin} MIN"
                    }
                    GlassTelemetryRow(
                        app.label.uppercase(Locale.getDefault()),
                        value,
                        last = index == rows.lastIndex,
                    )
                }
            }
        }
        if (!shizukuReady) {
            Spacer(Modifier.height(16.dp))
            MicroLabel("FULL TABLE NEEDS SHIZUKU")
            Spacer(Modifier.height(8.dp))
            GlassCardVariant {
                Column {
                    GlassTelemetryRow(
                        "SHIZUKU",
                        if (shizukuPresent) "TAP TO REQUEST" else "NOT RUNNING",
                    )
                    if (shizukuPresent) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(role = Role.Button) {
                                    try {
                                        Shizuku.requestPermission(1001)
                                    } catch (_: Exception) {
                                    }
                                }
                                .background(LucentSurfaceStrong)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MicroLabel("GRANT VIA SHIZUKU MANAGER")
                        }
                    }
                }
            }
        }
        if (!usageOk && !shizukuReady) {
            Spacer(Modifier.height(16.dp))
            MicroLabel("APP RANKING NEEDS USAGE ACCESS")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                ),
                            )
                        } catch (_: Exception) {
                        }
                    }
                    .background(LucentSurfaceStrong)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                MicroLabel("OPEN USAGE ACCESS SETTINGS")
            }
        }
        FooterClearance()
    }
}

// ---------------------------------------------------------------------------
// SYS: device card + storage. RAM totals moved to the MON graphs.
// ---------------------------------------------------------------------------
@Composable
private fun SysContent(info: BatteryInfo, mon: MonitorState, theme: ThemeSettings, health: BatteryHealth?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = mon.storage
    val render = remember { resolveDeviceRender() }
    val accent = AccentColors.getOrElse(theme.accentIdx) { AccentColors[0] }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        // About-Phone hero: OS + name cards beside the device render.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassCardVariant {
                    MicroLabel("OS", color = Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = osVersionText(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
                GlassCardVariant {
                    MicroLabel(
                        render?.name?.uppercase(Locale.getDefault()) ?: "DEVICE",
                        color = Muted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = render?.let { "Nothing ${it.name}" }
                            ?: "${Build.MANUFACTURER} ${Build.MODEL}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    MicroLabel("${Build.MODEL} · ${abiText()}", color = Muted)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                GlassCardVariant {
                    DeviceRenderImage(
                        level = info.level.coerceIn(0, 100),
                        modifier = Modifier.height(230.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Spec grid: only values public APIs can actually provide.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpecGridCell(
                icon = Icons.Filled.DeveloperBoard,
                title = "Processor",
                subtitle = "${chipsetVendor()}\n${chipsetName()}",
            )
            SpecGridCell(
                icon = Icons.Filled.PhotoCamera,
                title = "Camera",
                subtitle = cameraCounts(context),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpecGridCell(
                icon = Icons.Filled.Memory,
                title = "RAM",
                subtitle = formatGb(mon.mem.totalMb),
            )
            SpecGridCell(
                icon = Icons.Filled.Storage,
                title = "Storage",
                subtitle = formatGb(storage.totalMb),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpecGridCell(
                icon = Icons.Filled.BatteryStd,
                title = "Battery",
                subtitle = health?.designMah?.let { "%,d mAh".format(Locale.getDefault(), it) } ?: "—",
            )
            SpecGridCell(
                icon = Icons.Filled.Smartphone,
                title = "Screen",
                subtitle = screenSpec(context),
            )
        }
        Spacer(Modifier.height(32.dp))
        MicroLabel("APPEARANCE")
        Spacer(Modifier.height(8.dp))
        GlassCardVariant {
            MicroLabel("BAR TRANSPARENCY · ${theme.barAlphaPct}%", color = Muted)
            Slider(
                value = theme.barAlphaPct.toFloat(),
                onValueChange = { scope.launch { setBarAlpha(context, it.toInt()) } },
                valueRange = BAR_ALPHA_MIN.toFloat()..BAR_ALPHA_MAX.toFloat(),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            )
            Spacer(Modifier.height(4.dp))
            MicroLabel("BLUR · ${theme.blurDp} DP", color = Muted)
            Slider(
                value = theme.blurDp.toFloat(),
                onValueChange = { scope.launch { setBlurDp(context, it.toInt()) } },
                valueRange = BLUR_MIN.toFloat()..BLUR_MAX.toFloat(),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            )
            Spacer(Modifier.height(4.dp))
            MicroLabel("ACCENT", color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColors.forEachIndexed { i, c ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                2.dp,
                                if (i == theme.accentIdx) Color.White else Color.Transparent,
                                CircleShape,
                            )
                            .clickable(role = Role.Button) {
                                scope.launch { setAccent(context, i) }
                            },
                    )
                    if (i < AccentColors.size - 1) Spacer(Modifier.width(0.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            MicroLabel(AccentNames.getOrElse(theme.accentIdx) { "WHITE" }, color = Muted)
        }
        if (isGlyphMatrixSupported()) {
            Spacer(Modifier.height(32.dp))
            MicroLabel("GLYPH MATRIX")
            Spacer(Modifier.height(8.dp))
            GlassCardVariant {
                GlassTelemetryRow("TOY", "NTOP BATTERY · AOD")
                GlassTelemetryRow(
                    "MATRIX",
                    "${glyphMatrixLength()}×${glyphMatrixLength()}",
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
                                android.content.Intent().apply {
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
            Spacer(Modifier.height(24.dp))
            GlyphConfigSection(accent = accent)
        }
        FooterClearance()
    }
}

// ---------------------------------------------------------------------------
// Glyph Matrix gating: only matrix devices (Phone 3: 25×25) show the toy
// section. Guarded — the SDK touches system services on old builds.
// ---------------------------------------------------------------------------
private fun glyphMatrixLength(): Int = try {
    Common.getDeviceMatrixLength()
} catch (_: Exception) {
    0
}

private fun isGlyphMatrixSupported(): Boolean = glyphMatrixLength() > 0

// ---------------------------------------------------------------------------
// Device render: official store image for the detected Nothing/CMF model
// (see resolveDeviceRender) with the hand-drawn Phone3Render vector as the
// loading/error/offline fallback.
// ---------------------------------------------------------------------------
@Composable
private fun DeviceRenderImage(level: Int, modifier: Modifier = Modifier) {
    val render = remember { resolveDeviceRender() }
    val context = LocalContext.current
    val fallback = Modifier
        .height(150.dp)
        .aspectRatio(0.52f)
    if (render != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(render.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Nothing ${render.name}",
            modifier = modifier,
            contentScale = ContentScale.Fit,
            loading = { Phone3Render(level = level, modifier = fallback) },
            error = { Phone3Render(level = level, modifier = fallback) },
        )
    } else {
        Phone3Render(level = level, modifier = fallback)
    }
}

private fun sampleInfo() = BatteryInfo(
    level = 72,
    charging = true,
    source = "USB",
    tempC = 31.4f,
    voltageV = 4.12f,
    health = "GOOD",
    technology = "Li-ion",
    stateOfHealth = 94,
    cycleCount = 312,
    fullCapMah = 4735,
)

private fun sampleHealth() = BatteryHealth(
    designMah = 5150,
    designSource = "MODEL MAP",
    currentMaxMah = 5036f,
    learnedMah = 5036f,
    learnedSessions = 3,
    healthPct = 97.8f,
)

private fun sampleRam() = RamInfo(
    totalMb = 12288,
    availMb = 4096,
    swapTotalMb = 4096,
    swapFreeMb = 3072,
    cachedMb = 2560,
    thresholdMb = 1024,
    lowMemory = false,
    apps = listOf(
        RamApp("system", 812),
        RamApp("app", 356),
        RamApp("surfaceflinger", 188),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun HomePreview() {
    NtopTheme {
        Surface(color = NothingBlack) {
            HomeContent(info = sampleInfo())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun BattPreview() {
    NtopTheme {
        Surface(color = NothingBlack) {
            BattContent(
                info = sampleInfo().copy(
                    currentMa = 1480f,
                    maxCurrentMa = 1500f,
                    maxVoltageV = 5.0f,
                    chargeTimeMs = 54 * 60_000L,
                ),
                health = sampleHealth(),
                mon = MonitorState(),
                tick = 0,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun ProcPreview() {
    NtopTheme {
        Surface(color = NothingBlack) {
            ProcContent(ram = sampleRam(), tick = 0)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun FluentBottomBarPreview() {
    val hazeState = rememberHazeState()
    NtopTheme {
        Surface(color = NothingBlack) {
            FluentBottomBar(currentRoute = "mon", hazeState = hazeState, onSelect = {})
        }
    }
}
