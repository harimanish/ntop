package com.ntop.app.stats

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import rikka.shizuku.Shizuku

// ---------------------------------------------------------------------------
// ntop PROC tiers. Android 10+ gates foreign-UID memory info behind
// privileged context, so the full btop table needs Shizuku (adb-privileged
// binder calls from a normal app). No system property or public API exposes
// other apps' PSS/CPU to a regular app — verified on Nothing Phone 3.
// ---------------------------------------------------------------------------

data class ProcEntry(
    val label: String,
    val pssMb: Long,
    /** -1 when RAM attribution is unavailable (no Shizuku). */
    val pid: Int = -1,
    val foregroundMin: Long = -1,
)

/** True when the Shizuku binder is alive AND ntop is granted. */
fun isShizukuReady(): Boolean = try {
    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (_: Exception) {
    false
}

/** True when the Shizuku manager app is reachable (setup possible). */
fun isShizukuInstalled(context: Context): Boolean = try {
    Shizuku.pingBinder()
} catch (_: Exception) {
    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") != null
}

/**
 * Privileged process snapshot via Shizuku UserService (shell identity).
 * The raw-transact alternative is dead: hidden-API blacklist blocks
 * Stub-class reflection for our targetSdk, so getTransactionCode() is null.
 * The service reads /proc directly — plain files, no hidden APIs either
 * side. MUST be called off the main thread (bind + IPC).
 */
const val PROC_SERVICE_TAG = "ntop-proc-v4"

/** Bound privileged service; null until Shizuku connects it. */
@Volatile private var procService: IProcService? = null
private var procServiceBound = false

private val procServiceConn = object : android.content.ServiceConnection {
    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
        procService = IProcService.Stub.asInterface(service)
    }
    override fun onServiceDisconnected(name: android.content.ComponentName?) {
        procService = null
    }
}

/** Bind once per process; safe to call repeatedly. No-op unless granted. */
fun ensureProcServiceBound(context: Context) {
    if (procServiceBound || !isShizukuReady()) return
    try {
        val args = Shizuku.UserServiceArgs(
            android.content.ComponentName(context.packageName, ProcUserService::class.java.name),
        ).daemon(false).processNameSuffix("proc").version(3).tag(PROC_SERVICE_TAG)
        Shizuku.bindUserService(args, procServiceConn)
        procServiceBound = true
    } catch (_: Exception) {
    }
}

/** All running app processes (public API; memory fields gated per-UID). */
data class ProcIdentity(val pid: Int, val processName: String, val importance: Int, val uid: Int)

fun runningProcIdentities(context: Context): List<ProcIdentity> = try {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.runningAppProcesses.orEmpty().map {
        ProcIdentity(it.pid, it.processName, it.importance, it.uid)
    }
} catch (_: Exception) {
    emptyList()
}

/** Short human label: basename for native paths, last segment otherwise. */
fun shortProcLabel(processName: String): String =
    processName.substringAfterLast('/').substringAfterLast(':')
        .substringAfterLast('.').take(24)

/** Full system table (Shizuku user service). Empty until connected. */
fun shizukuProcTable(context: Context, limit: Int = 40): List<ProcEntry> {
    ensureProcServiceBound(context)
    val raw = try {
        procService?.snapshot()
    } catch (_: Exception) {
        null
    }
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 3) return@mapNotNull null
        val pid = p[0].toIntOrNull() ?: return@mapNotNull null
        val kb = p[1].toLongOrNull() ?: return@mapNotNull null
        if (kb <= 0) return@mapNotNull null
        val label = shortProcLabel(p[2])
        if (label.isBlank() || label.all { it.isDigit() }) return@mapNotNull null
        ProcEntry(label, kb / 1024L, pid)
    }.sortedByDescending { it.pssMb }.take(limit).toList()
}

// --- UsageStats fallback (no RAM, foreground-time ranking) -----------------

fun hasUsageAccess(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
    )
    mode == AppOpsManager.MODE_ALLOWED
} catch (_: Exception) {
    false
}

/** Top apps by foreground minutes in the last 24h. No RAM figures. */
fun usageRanking(context: Context, limit: Int = 20): List<ProcEntry> {
    if (!hasUsageAccess(context)) return emptyList()
    return try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 3600_000L, now)
        val pm = context.packageManager
        stats.orEmpty()
            .filter { it.totalTimeInForeground > 60_000L }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .mapNotNull { s ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                } catch (_: Exception) {
                    s.packageName.substringAfterLast('.')
                }
                ProcEntry(label.take(28), -1, foregroundMin = s.totalTimeInForeground / 60_000L)
            }
    } catch (_: Exception) {
        emptyList()
    }
}
