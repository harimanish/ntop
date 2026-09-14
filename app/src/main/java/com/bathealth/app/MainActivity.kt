package com.bathealth.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bathealth.app.ui.components.FluentBackground
import com.bathealth.app.ui.components.GlassCard
import com.bathealth.app.ui.components.GlassCardVariant
import com.bathealth.app.ui.components.GlassChargePill
import com.bathealth.app.ui.components.GlassGlyphDots
import com.bathealth.app.ui.components.GlassHeader
import com.bathealth.app.ui.components.GlassTelemetryRow
import com.bathealth.app.ui.components.Phone3Render
import com.bathealth.app.ui.components.FluentBottomBar
import com.bathealth.app.ui.components.MicroLabel
import com.bathealth.app.ui.theme.BatHealthTheme
import com.bathealth.app.ui.theme.GeistMono
import com.bathealth.app.ui.theme.Muted
import com.bathealth.app.ui.theme.NothingBlack
import com.bathealth.app.ui.theme.NothingRed
import java.text.SimpleDateFormat
import java.util.Date
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatHealthTheme {
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
    var updatedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                info = ctx.enrichWithHealthProps(intent.toBatteryInfo())
                updatedAt = System.currentTimeMillis()
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    AppShell(info = info, updatedAt = updatedAt)
}

@Composable
private fun AppShell(info: BatteryInfo, updatedAt: Long) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"

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
            GlassHeader()

            Spacer(Modifier.height(16.dp))

            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.weight(1f),
            ) {
                composable("home") { HomeContent(info) }
                composable("charge") { ChargeContent(info) }
                composable("health") { HealthContent(info) }
            }
        }

        // Floating Lucent footer: updated stamp + frosted tab bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            MicroLabel(
                "UPDATED " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updatedAt)),
                color = Muted,
            )
            Spacer(Modifier.height(12.dp))
            FluentBottomBar(
                currentRoute = route,
                onSelect = { dest ->
                    if (dest != route) {
                        nav.navigate(dest) {
                            popUpTo("home")
                            launchSingleTop = true
                        }
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
        MicroLabel("BATTERY")
        Spacer(Modifier.height(8.dp))
        GlassCardVariant {
            Text(
                text = if (animatedLevel >= 0) "${animatedLevel}%" else "--%",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        GlassChargePill(charging = info.charging, source = info.source)

        Spacer(Modifier.height(32.dp))
        // Glyph Matrix homage: segmented dot progress, 20 cells.
        GlassGlyphDots(level = info.level.coerceIn(0, 100))

        Spacer(Modifier.height(32.dp))
        // Device card: Phone (3) vector render + Build identity.
        DeviceCard(level = info.level.coerceIn(0, 100))

        Spacer(Modifier.height(32.dp))
        // Lucent translucent card: frosted layer + hairline border.
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

        Spacer(Modifier.height(32.dp))
        // Second DeviceCard at the page end: stays reachable above the
        // floating bar and shows content drifting behind the frost.
        DeviceCard(level = info.level.coerceIn(0, 100))

        FooterClearance()
    }
}

// ---------------------------------------------------------------------------
// HEALTH: official state-of-health %, cycle count + full telemetry.
// ---------------------------------------------------------------------------
@Composable
private fun HealthContent(info: BatteryInfo) {
    val hasSoh = info.stateOfHealth != null
    val heroValue = if (hasSoh) info.stateOfHealth else info.fullCapMah ?: -1
    val animatedHero by animateIntAsState(
        targetValue = heroValue,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "hero",
    )
    val heroText = if (hasSoh) "${animatedHero}%" else if (info.fullCapMah != null) "≈$animatedHero" else "—"
    val heroSuffix = if (hasSoh) "" else if (info.fullCapMah != null) " mAh" else ""
    val caption = if (hasSoh) "EST FULL CHARGE VS RATED" else "EST FULL CHARGE FROM COUNTER"
    val cyclesText = info.cycleCount?.toString() ?: "—"
    val fullCapText = info.fullCapMah?.let { "≈$it mAh" } ?: "—"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel(if (hasSoh) "STATE OF HEALTH" else "FULL CHARGE CAPACITY")
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (hasSoh) Color(0xFF4CAF50) else Muted),
            )
        }
        Spacer(Modifier.height(8.dp))
        GlassCardVariant {
            Text(
                text = buildAnnotatedString {
                    append(heroText)
                    if (heroSuffix.isNotEmpty()) {
                        withStyle(
                            style = SpanStyle(
                                fontSize = 24.sp,
                                letterSpacing = 0.sp,
                                color = Muted,
                            ),
                        ) {
                            append(heroSuffix)
                        }
                    } else {
                        append("%")
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
        Spacer(Modifier.height(8.dp))
        MicroLabel(caption, color = Muted)

        Spacer(Modifier.height(32.dp))
        GlassCardVariant {
            GlassTelemetryRow("CONDITION", info.health)
            GlassTelemetryRow("CYCLES", cyclesText)
            GlassTelemetryRow("FULL CAP", fullCapText)
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

        Spacer(Modifier.height(16.dp))
        MicroLabel(
            if (hasSoh) "OFFICIAL SOH FROM BATTERYMANAGER" else "SOH GATED — NEEDS BATTERY_STATS PERMISSION",
            color = Muted,
        )
        FooterClearance()
    }
}

// ---------------------------------------------------------------------------
// CHARGE: live electrical stats — watts hero, volts/amps, charger limits.
// Current re-reads every 2s while this tab is visible (scoped poll) plus
// system broadcast updates from the shared BatteryScreen state.
// ---------------------------------------------------------------------------
@Composable
private fun ChargeContent(info: BatteryInfo) {
    val context = LocalContext.current
    var liveMa by remember(info.currentMa) { mutableStateOf(info.currentMa) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            context.readCurrentMa(average = false)?.let { liveMa = it }
        }
    }

    val ma = liveMa ?: info.avgCurrentMa
    val watts = if (!info.voltageV.isNaN() && ma != null) info.voltageV * ma / 1000f else null
    val animatedWatts by animateFloatAsState(
        targetValue = watts ?: 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "watts",
    )
    val hero = if (watts != null) "%+.1f".format(Locale.getDefault(), animatedWatts) else "—"
    val currentText = ma?.let { "%+.0f mA".format(Locale.getDefault(), it) } ?: "—"
    val maxMaText = info.maxCurrentMa?.let { "%.0f mA".format(Locale.getDefault(), it) } ?: "—"
    val maxVText = info.maxVoltageV?.let { "%.1f V".format(Locale.getDefault(), it) } ?: "—"
    val etaText = info.chargeTimeMs?.let { formatEta(it) } ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
        MicroLabel("+ IN / − OUT OF BATTERY", color = Muted)
        Spacer(Modifier.height(12.dp))
        GlassChargePill(charging = info.charging, source = info.source)

        Spacer(Modifier.height(32.dp))
        GlassCardVariant {
            GlassTelemetryRow("CURRENT", currentText)
            GlassTelemetryRow(
                "VOLTAGE",
                if (info.voltageV.isNaN()) "—" else "${info.voltageV} V",
            )
            GlassTelemetryRow("MAX CURRENT", maxMaText)
            GlassTelemetryRow("MAX VOLTAGE", maxVText)
            GlassTelemetryRow("TIME TO FULL", etaText)
            GlassTelemetryRow("SOURCE", info.source)
            GlassTelemetryRow(
                "TEMP",
                if (info.tempC.isNaN()) "—" else "${info.tempC}°C",
                last = true,
            )
        }

        Spacer(Modifier.height(16.dp))
        MicroLabel(
            if (info.charging) "LIVE FROM FUEL GAUGE — 2S REFRESH" else "PLUG IN TO MEASURE CHARGE RATE",
            color = Muted,
        )
        FooterClearance()
    }
}

private fun formatEta(ms: Long): String {
    val mins = (ms / 60_000).toInt().coerceAtLeast(0)
    return if (mins < 60) "$mins MIN" else "${mins / 60}H ${mins % 60}M"
}

// ---------------------------------------------------------------------------
// Device card: Phone (3) Canvas render + zero-permission Build identity.
// ---------------------------------------------------------------------------
@Composable
private fun DeviceCard(level: Int) {
    val chipset = if (Build.VERSION.SDK_INT >= 31) {
        Build.SOC_MODEL ?: Build.HARDWARE
    } else {
        Build.HARDWARE
    }
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Phone3Render(
                level = level,
                modifier = Modifier
                    .height(150.dp)
                    .aspectRatio(0.52f),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                MicroLabel("DEVICE", color = Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${Build.MANUFACTURER} ${Build.MODEL}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                GlassTelemetryRow("MODEL", Build.MODEL)
                GlassTelemetryRow("CHIPSET", chipset ?: "—")
                GlassTelemetryRow(
                    "ANDROID",
                    "${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}",
                    last = true,
                )
            }
        }
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

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun HomePreview() {
    BatHealthTheme {
        Surface(color = NothingBlack) {
            HomeContent(info = sampleInfo())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun ChargePreview() {
    BatHealthTheme {
        Surface(color = NothingBlack) {
            ChargeContent(
                info = sampleInfo().copy(
                    currentMa = 1480f,
                    maxCurrentMa = 1500f,
                    maxVoltageV = 5.0f,
                    chargeTimeMs = 54 * 60_000L,
                ),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun HealthPreview() {
    BatHealthTheme {
        Surface(color = NothingBlack) {
            HealthContent(info = sampleInfo())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun GlassHeaderPreview() {
    BatHealthTheme {
        Surface(color = NothingBlack) {
            GlassHeader()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun FluentBottomBarPreview() {
    BatHealthTheme {
        Surface(color = NothingBlack) {
            FluentBottomBar(currentRoute = "home", onSelect = {})
        }
    }
}
