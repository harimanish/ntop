package com.ntop.app.stats

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats

// ---------------------------------------------------------------------------
// ntop data layer: btop-style samplers over sources a normal app can read.
// Verified on Nothing Phone 3 (app UID): /proc/meminfo, /proc/cpuinfo and
// per-core time_in_state are readable; /proc/stat and /proc/<other-pid> are
// denied, so there is no true global CPU %. CPU load is therefore reported
// as a frequency proxy (cur/max per core), honestly labeled FREQ.
// All reads are small sysfs/proc files and safe on the 1s refresh loop.
// Every function is total: exceptions yield empty/default values.
// ---------------------------------------------------------------------------

const val HISTORY_CAP = 60

/** Fixed-size ring buffer for btop-style history graphs (60 samples @1s). */
class HistoryBuffer(val cap: Int = HISTORY_CAP) {
    private val buf = ArrayDeque<Float>(cap)
    fun push(v: Float) {
        if (buf.size >= cap) buf.removeFirst()
        buf.addLast(if (v.isFinite()) v else 0f)
    }
    fun values(): List<Float> = buf.toList()
    fun latest(): Float = buf.lastOrNull() ?: 0f
}

// ---------------------------------------------------------------------------
// CPU: per-core frequencies + time_in_state distribution.
// ---------------------------------------------------------------------------

data class CpuCore(
    val index: Int,
    val curFreqKhz: Long,
    val maxFreqKhz: Long,
) {
    /** Frequency proxy for load (0..1). NOT a true utilization %. */
    val loadProxy: Float
        get() = if (maxFreqKhz > 0) (curFreqKhz / maxFreqKhz.toFloat()).coerceIn(0f, 1f) else 0f
}

fun cpuCoreCount(): Int = try {
    java.io.File("/sys/devices/system/cpu")
        .listFiles { f -> f.isDirectory && f.name.matches(Regex("cpu[0-9]+")) }
        ?.size?.takeIf { it > 0 }
        ?: Runtime.getRuntime().availableProcessors()
} catch (_: Exception) {
    Runtime.getRuntime().availableProcessors()
}

private fun readKhz(path: String): Long = try {
    java.io.File(path).readText().trim().toLongOrNull()?.takeIf { it > 0 } ?: 0L
} catch (_: Exception) {
    0L
}

fun sampleCpuFreqs(): List<CpuCore> {
    val n = cpuCoreCount()
    return (0 until n).map { i ->
        val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
        val cur = readKhz("$base/scaling_cur_freq")
        val max = readKhz("$base/cpuinfo_max_freq").takeIf { it > 0 }
            ?: readKhz("$base/scaling_max_freq")
        CpuCore(i, cur, max)
    }
}

/** freqKhz -> jiffies for one core; null when the kernel hides the file. */
fun readTimeInState(core: Int): Map<Long, Long> = try {
    java.io.File("/sys/devices/system/cpu/cpu$core/cpufreq/stats/time_in_state")
        .readLines()
        .mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size == 2) {
                val f = p[0].toLongOrNull()
                val t = p[1].toLongOrNull()
                if (f != null && t != null) f to t else null
            } else {
                null
            }
        }.toMap()
} catch (_: Exception) {
    emptyMap()
}

// ---------------------------------------------------------------------------
// Memory: global totals from /proc/meminfo + pressure state.
// ---------------------------------------------------------------------------

data class MemSnapshot(
    val totalMb: Long = -1,
    val availMb: Long = -1,
    val swapTotalMb: Long = -1,
    val swapFreeMb: Long = -1,
    val cachedMb: Long = -1,
    val thresholdMb: Long = -1,
    val lowMemory: Boolean = false,
) {
    val usedMb: Long get() = if (totalMb >= 0 && availMb >= 0) totalMb - availMb else -1
    val swapUsedMb: Long get() = if (swapTotalMb >= 0 && swapFreeMb >= 0) swapTotalMb - swapFreeMb else -1
    val usedPct: Float get() = if (totalMb > 0 && usedMb >= 0) usedMb / totalMb.toFloat() else -1f
}

private fun readMeminfoKb(): Map<String, Long> = try {
    java.io.File("/proc/meminfo").readLines().mapNotNull { line ->
        val parts = line.split(Regex("\\s+"), limit = 3)
        if (parts.size >= 2) {
            parts[0].trimEnd(':') to (parts[1].toLongOrNull() ?: return@mapNotNull null)
        } else {
            null
        }
    }.toMap()
} catch (_: Exception) {
    emptyMap()
}

fun sampleMem(context: Context): MemSnapshot = try {
    val mem = readMeminfoKb()
    fun mb(key: String): Long = mem[key]?.let { it / 1024 } ?: -1
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    MemSnapshot(
        totalMb = mb("MemTotal"),
        availMb = mb("MemAvailable"),
        swapTotalMb = mb("SwapTotal"),
        swapFreeMb = mb("SwapFree"),
        cachedMb = listOf("Cached", "Buffers", "SReclaimable")
            .mapNotNull { mem[it] }.sum().let { if (mem.isEmpty()) -1 else it / 1024 },
        thresholdMb = if (mi.threshold > 0) mi.threshold / (1024 * 1024) else -1,
        lowMemory = mi.lowMemory,
    )
} catch (_: Exception) {
    MemSnapshot()
}

// ---------------------------------------------------------------------------
// Network: device totals; caller diffs consecutive samples over elapsed ms.
// ---------------------------------------------------------------------------

data class NetTotals(val rxBytes: Long, val txBytes: Long)

fun sampleNet(): NetTotals = NetTotals(
    rxBytes = try { TrafficStats.getTotalRxBytes().takeIf { it >= 0 } ?: 0L } catch (_: Exception) { 0L },
    txBytes = try { TrafficStats.getTotalTxBytes().takeIf { it >= 0 } ?: 0L } catch (_: Exception) { 0L },
)

data class NetRate(val rxBps: Float, val txBps: Float)

fun NetTotals.rateSince(prev: NetTotals, elapsedMs: Long): NetRate {
    if (elapsedMs <= 0) return NetRate(0f, 0f)
    val s = elapsedMs / 1000f
    return NetRate(
        rxBps = ((rxBytes - prev.rxBytes).coerceAtLeast(0) / s).toFloat(),
        txBps = ((txBytes - prev.txBytes).coerceAtLeast(0) / s).toFloat(),
    )
}

// ---------------------------------------------------------------------------
// Storage: internal data partition via StatFs.
// ---------------------------------------------------------------------------

data class StorageSnapshot(val totalMb: Long = -1, val freeMb: Long = -1) {
    val usedMb: Long get() = if (totalMb >= 0 && freeMb >= 0) totalMb - freeMb else -1
    val usedPct: Float get() = if (totalMb > 0 && usedMb >= 0) usedMb / totalMb.toFloat() else -1f
}

fun sampleStorage(): StorageSnapshot = try {
    val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
    val block = stat.blockSizeLong
    StorageSnapshot(
        totalMb = stat.blockCountLong * block / (1024 * 1024),
        freeMb = stat.availableBlocksLong * block / (1024 * 1024),
    )
} catch (_: Exception) {
    StorageSnapshot()
}
