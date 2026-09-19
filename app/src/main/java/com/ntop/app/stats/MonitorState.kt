package com.ntop.app.stats

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock

// ---------------------------------------------------------------------------
// ntop monitor hub: owns history buffers + latest snapshot, fed once per
// second from the UI refresh loop. Plain object mutated in place; the loop's
// existing state writes (info/ram) already drive recomposition each tick.
// ---------------------------------------------------------------------------

/** Hold-last window for the fuel-gauge current readout. */
const val STABLE_MA_HOLD_MS = 15_000L

class MonitorState {
    val memHist = HistoryBuffer()
    val swapHist = HistoryBuffer()
    val rxHist = HistoryBuffer()
    val txHist = HistoryBuffer()
    val cpuHist = HistoryBuffer()
    val tempHist = HistoryBuffer()
    val powerHist = HistoryBuffer()

    var mem: MemSnapshot = MemSnapshot()
    var cores: List<CpuCore> = emptyList()
    var net: NetRate = NetRate(0f, 0f)
    var storage: StorageSnapshot = StorageSnapshot()
    /**
     * Last non-null fuel-gauge current, held up to [STABLE_MA_HOLD_MS] so the
     * power readout doesn't flicker to empty when the gauge reports 0/NaN
     * for a tick or two (common at rest and on slow USB charging).
     */
    var stableMa: Float? = null
    private var stableMaTs: Long = 0

    val tracker = SessionTracker()

    private var prevNet: NetTotals = NetTotals(0, 0)
    private var prevMs: Long = SystemClock.elapsedRealtime()
    private var prevInit = false
    private var wasCharging = false

    private fun Context.counterMah(): Float? = try {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 }?.div(1000f)
    } catch (_: Exception) {
        null
    }

    /**
     * Sample everything. Returns a mature [ChargeSession] when one closes
     * (caller persists it via [recordSession]); null otherwise.
     */
    fun sample(
        context: Context,
        charging: Boolean,
        levelPct: Int,
        tempC: Float,
        powerW: Float?,
        voltageV: Float,
        currentMa: Float?,
    ): ChargeSession? {
        val now = SystemClock.elapsedRealtime()

        if (currentMa != null) {
            stableMa = currentMa
            stableMaTs = now
        } else if (now - stableMaTs > STABLE_MA_HOLD_MS) {
            stableMa = null
        }

        mem = sampleMem(context)
        storage = sampleStorage()
        if (mem.usedPct >= 0) memHist.push(mem.usedPct * 100f)
        val swapPct = if (mem.swapTotalMb > 0 && mem.swapUsedMb >= 0) {
            mem.swapUsedMb / mem.swapTotalMb.toFloat() * 100f
        } else {
            -1f
        }
        if (swapPct >= 0) swapHist.push(swapPct)

        cores = sampleCpuFreqs()
        if (cores.isNotEmpty()) {
            cpuHist.push(cores.map { it.loadProxy }.average().toFloat() * 100f)
        }

        val totals = sampleNet()
        if (!prevInit) {
            prevNet = totals
            prevMs = now
            prevInit = true
        } else {
            net = totals.rateSince(prevNet, now - prevMs)
            rxHist.push(net.rxBps)
            txHist.push(net.txBps)
            prevNet = totals
            prevMs = now
        }

        if (!tempC.isNaN()) tempHist.push(tempC)
        if (powerW != null) powerHist.push(kotlin.math.abs(powerW))

        // Session learner: feed while charging, close on unplug/full.
        val counter = context.counterMah()
        return if (charging) {
            wasCharging = true
            tracker.tick(true, levelPct, counter)
            null
        } else if (wasCharging) {
            wasCharging = false
            tracker.close(levelPct)
        } else {
            tracker.tick(false, levelPct, counter)
            null
        }
    }
}
