package com.ntop.app.stats

import android.content.Context
import android.os.BatteryManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// ---------------------------------------------------------------------------
// ntop battery health: health % = current full-charge max / design capacity.
//
// Design capacity source chain (no public SDK API exists):
//  1. Manual override in DataStore (user-correctable).
//  2. PowerProfile reflection (com.android.internal.os.PowerProfile,
//     hidden/greylisted — works on old devices, throws on targetSdk 34+).
//  3. Hardcoded Nothing/CMF map below (model numbers + codenames).
//
// Current max source chain:
//  1. Session-learned estimate: charge-counter deltas integrated across
//     charging sessions spanning >= 20% (AccuBattery method), median of
//     the last 5 mature sessions.
//  2. Instant estimate: CHARGE_COUNTER / (CAPACITY / 100).
// ---------------------------------------------------------------------------

private val Context.ntopStore by preferencesDataStore(name = "ntop_battery")

private val KEY_DESIGN_OVERRIDE = intPreferencesKey("design_override_mah")
private val KEY_ESTIMATES = stringPreferencesKey("session_estimates_mah")

const val MIN_SESSION_SPAN_PCT = 20
const val HEALTH_WINDOW = 5
const val ESTIMATE_HISTORY = 20

// --- Design capacity map (mAh). Phone 3: 5150 intl / 5500 India. ---------
private const val P3_INTL = 5150
private const val P3_INDIA = 5500

private fun designFromMap(): Int? {
    val keys = sequenceOf(
        android.os.Build.MODEL, android.os.Build.DEVICE,
        android.os.Build.PRODUCT, android.os.Build.HARDWARE,
    ).map { it.lowercase().trim() }.joinToString(" | ")
    fun has(vararg needles: String) = needles.any { it in keys }
    val india = try {
        Locale.getDefault().country.equals("IN", ignoreCase = true)
    } catch (_: Exception) {
        false
    }
    return when {
        has("a069p", "froggerpro") -> null // 4a Pro: confirm at launch
        has("a009p", "supercontra") -> null // 4b: confirm at launch
        has("a069", "frogger") -> null // 4a: confirm at launch
        has("a001t", "galaxian") -> 5000 // 3a Lite
        has("a059p") -> 5000 // 3a Pro
        has("a059", "asteroids") -> 5000 // 3a
        has("a024", "metroid", "arbok") -> if (india) P3_INDIA else P3_INTL
        has("a001", "galaga") -> 5000 // CMF Phone 2 Pro
        has("a015", "tetris") -> 5000 // CMF Phone 1
        has("a142p", "pacmanpro") -> 5000 // 2a Plus
        has("a142", "pacman") -> 5000 // 2a
        has("a065", "ain065", "pong") -> 4700 // Phone 2
        has("a063", "spacewar") -> 4500 // Phone 1
        else -> null
    }
}

/** Best-effort PowerProfile reflection; null when hidden-API blocked. */
fun powerProfileCapacityMah(context: Context): Int? = try {
    val cls = Class.forName("com.android.internal.os.PowerProfile")
    val profile = cls.getConstructor(Context::class.java).newInstance(context)
    val v = cls.getMethod("getBatteryCapacity").invoke(profile) as? Double
    v?.toInt()?.takeIf { it in 1000..20000 }
} catch (_: Exception) {
    null
}

/** Resolved design capacity + where it came from. Override wins. */
suspend fun designCapacityMah(context: Context): Pair<Int?, String> {
    val prefs = context.ntopStore.data.first()
    prefs[KEY_DESIGN_OVERRIDE]?.takeIf { it in 1000..20000 }?.let { return it to "MANUAL" }
    powerProfileCapacityMah(context)?.let { return it to "POWER PROFILE" }
    designFromMap()?.let { return it to "MODEL MAP" }
    return null to "UNKNOWN"
}

fun observeDesignOverride(context: Context): Flow<Int?> =
    context.ntopStore.data.map { it[KEY_DESIGN_OVERRIDE] }

suspend fun setDesignOverride(context: Context, mah: Int?) {
    context.ntopStore.edit { prefs ->
        if (mah == null || mah !in 1000..20000) prefs.remove(KEY_DESIGN_OVERRIDE)
        else prefs[KEY_DESIGN_OVERRIDE] = mah
    }
}

/** Instant full-charge max from the fuel gauge (mAh). Null when gated. */
fun Context.instantFullCapMah(): Int? = try {
    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (counter > 0 && cap in 5..100) (counter / (cap / 100f) / 1000f).toInt().takeIf { it > 0 } else null
} catch (_: Exception) {
    null
}

// --- Session learner -------------------------------------------------------

/** One mature charge session: [startPct, endPct] with summed mAh in. */
data class ChargeSession(val startPct: Int, val endPct: Int, val mahIn: Float) {
    val span: Int get() = endPct - startPct
    val estimateMah: Float? get() = if (span >= MIN_SESSION_SPAN_PCT && mahIn > 0) mahIn / span * 100f else null
}

/**
 * Feed once per loop tick while charging. Integrates charge-counter deltas
 * across level rises; emits a mature [ChargeSession] once the plug pulls
 * or 100% is reached with span >= 20%.
 */
class SessionTracker {
    private var startPct = -1
    private var lastPct = -1
    private var lastCounterMah = -1f
    private var sumMah = 0f

    fun tick(charging: Boolean, levelPct: Int, counterMah: Float?): ChargeSession? {
        if (!charging || levelPct < 0) return reset(result = null)
        if (startPct < 0) {
            startPct = levelPct
            lastPct = levelPct
            lastCounterMah = counterMah ?: -1f
            return null
        }
        if (counterMah != null && lastCounterMah >= 0 && levelPct >= lastPct) {
            sumMah += (counterMah - lastCounterMah).coerceAtLeast(0f)
        }
        if (counterMah != null) lastCounterMah = counterMah
        lastPct = levelPct
        return null
    }

    /** Call on unplug (or full): closes the session, returns it if mature. */
    fun close(endPct: Int): ChargeSession? {
        val s = if (startPct >= 0 && endPct > startPct) ChargeSession(startPct, endPct, sumMah) else null
        reset(result = null)
        return if (s != null && s.span >= MIN_SESSION_SPAN_PCT && s.estimateMah != null) s else null
    }

    private fun <T> reset(result: T): T {
        startPct = -1
        lastPct = -1
        lastCounterMah = -1f
        sumMah = 0f
        return result
    }
}

private fun parseEstimates(raw: String?): List<Float> =
    raw?.split(",")?.mapNotNull { it.toFloatOrNull()?.takeIf { v -> v in 500f..20000f } } ?: emptyList()

fun observeEstimates(context: Context): Flow<List<Float>> =
    context.ntopStore.data.map { parseEstimates(it[KEY_ESTIMATES]) }

suspend fun recordSession(context: Context, session: ChargeSession) {
    val est = session.estimateMah ?: return
    context.ntopStore.edit { prefs ->
        val updated = (parseEstimates(prefs[KEY_ESTIMATES]) + est).takeLast(ESTIMATE_HISTORY)
        prefs[KEY_ESTIMATES] = updated.joinToString(",")
    }
}

suspend fun learnedFullCapMah(context: Context): Pair<Float?, Int> {
    val all = parseEstimates(context.ntopStore.data.first()[KEY_ESTIMATES])
    val window = all.takeLast(HEALTH_WINDOW)
    if (window.isEmpty()) return null to 0
    return window.sorted()[window.size / 2] to window.size
}

// --- Combined health -------------------------------------------------------

data class BatteryHealth(
    val designMah: Int?,
    val designSource: String,
    /** Preferred current-max: learned when mature, else instant. */
    val currentMaxMah: Float?,
    val learnedMah: Float?,
    val learnedSessions: Int,
    val healthPct: Float?,
) {
    val wearPct: Float? get() = healthPct?.let { (100f - it).coerceAtLeast(0f) }
}

suspend fun batteryHealth(context: Context): BatteryHealth {
    val (design, source) = designCapacityMah(context)
    val (learned, n) = learnedFullCapMah(context)
    val instant = context.instantFullCapMah()?.toFloat()
    val current = if (n >= 2 && learned != null) learned else instant
    val pct = if (design != null && design > 0 && current != null && current > 0) {
        (current / design * 100f).coerceIn(0f, 110f)
    } else {
        null
    }
    return BatteryHealth(design, source, current, learned, n, pct)
}
