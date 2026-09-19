package com.ntop.app.stats

import android.os.Debug
import java.io.File

// Runs in Shizuku's user-service process as shell (UID 2000). Hidden-API
// restrictions don't apply here, so IActivityManager is reached via
// reflection (it is @hidden and invisible to the app process, which is also
// why SystemServiceHelper.getTransactionCode() is null there). system_server
// does the actual PSS reads, so ALL processes resolve — unlike /proc
// smaps_rollup, which SELinux hides from shell for other UIDs.
class ProcUserService : IProcService.Stub() {
    override fun snapshot(): String {
        return try {
            val pids = File("/proc").listFiles { f ->
                f.isDirectory && f.name.all { c -> c.isDigit() }
            }?.mapNotNull { it.name.toIntOrNull() } ?: return ""
            if (pids.isEmpty()) return ""
            val smClass = Class.forName("android.os.ServiceManager")
            val binder = smClass.getMethod("getService", String::class.java)
                .invoke(null, "activity") as android.os.IBinder
            val iamClass = Class.forName("android.app.IActivityManager")
            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val am = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                .invoke(null, binder)
            @Suppress("UNCHECKED_CAST")
            val infos = iamClass.getMethod("getProcessMemoryInfo", IntArray::class.java)
                .invoke(am, pids.toIntArray()) as Array<Debug.MemoryInfo>
            val sb = StringBuilder()
            infos.forEachIndexed { i, mi ->
                val pid = pids[i]
                if (pid <= 0) return@forEachIndexed
                val pss = mi.totalPss
                if (pss <= 0) return@forEachIndexed
                val cmd = try {
                    File("/proc/$pid/cmdline").readBytes().toString(Charsets.UTF_8)
                        .trimEnd('\u0000').take(120)
                } catch (_: Exception) {
                    ""
                }
                sb.append(pid).append('|').append(pss).append('|')
                    .append(cmd.ifEmpty { "PID $pid" }).append('\n')
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}
