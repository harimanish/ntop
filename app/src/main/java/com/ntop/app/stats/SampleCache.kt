package com.ntop.app.stats

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------------------------------------------------------------------------
// Cold-start snapshot cache.
//
// On launch every page used to render empty: the graphs had no history and the
// PROC table was blank until live sampling filled them, so the app visibly
// "warmed up" over a second or two. This persists the last known sample and
// hydrates it immediately, so pages paint fully populated from cached values
// and the 1 s live loop takes over afterwards.
//
// Plain JSON in one file (org.json ships with Android, no new dependency),
// written off the main thread. A corrupt or partial file is treated as "no
// cache" rather than an error — the live path always works regardless.
// ---------------------------------------------------------------------------

private const val TAG = "ntop.Cache"
private const val FILE = "last_sample.json"
private const val VERSION = 1

class SampleCache(
    val mem: MemSnapshot,
    val storage: StorageSnapshot,
    val net: NetRate,
    val stableMa: Float?,
    val ram: RamInfo?,
    val cores: List<CpuCore>,
    val proc: List<ProcEntry>,
    val histories: Map<String, List<Float>>,
) {
    /** Seeds the monitor hub so graphs paint populated before the first live sample. */
    fun applyTo(mon: MonitorState) {
        mon.mem = mem
        mon.storage = storage
        mon.net = net
        if (cores.isNotEmpty()) mon.cores = cores
        if (stableMa != null) mon.restoreStableMa(stableMa)
        histories["mem"]?.let { mon.memHist.restore(it) }
        histories["swap"]?.let { mon.swapHist.restore(it) }
        histories["rx"]?.let { mon.rxHist.restore(it) }
        histories["tx"]?.let { mon.txHist.restore(it) }
        histories["cpu"]?.let { mon.cpuHist.restore(it) }
        histories["temp"]?.let { mon.tempHist.restore(it) }
        histories["power"]?.let { mon.powerHist.restore(it) }
    }
}

/** Replaces the buffer contents, used to hydrate graphs before the first sample. */
fun HistoryBuffer.restore(values: List<Float>) {
    values.forEach { push(it) }
}

private fun cacheFile(context: Context) = File(context.filesDir, FILE)

suspend fun loadSampleCache(context: Context): SampleCache? = withContext(Dispatchers.IO) {
    try {
        val f = cacheFile(context)
        if (!f.exists()) return@withContext null
        val r = JSONObject(f.readText())
        if (r.optInt("v") != VERSION) return@withContext null

        val procArr = r.optJSONArray("proc")
        val ramArr = r.optJSONArray("ramApps")

        SampleCache(
            mem = MemSnapshot(
                totalMb = r.optLong("memTotal"),
                availMb = r.optLong("memAvail"),
                swapTotalMb = r.optLong("swapTotal"),
                swapFreeMb = r.optLong("swapFree"),
                cachedMb = r.optLong("memCached"),
                thresholdMb = r.optLong("memThreshold"),
                lowMemory = r.optBoolean("lowMemory"),
            ),
            storage = StorageSnapshot(
                totalMb = r.optLong("storageTotal"),
                freeMb = r.optLong("storageFree"),
            ),
            net = NetRate(r.optDouble("rxBps", 0.0).toFloat(), r.optDouble("txBps", 0.0).toFloat()),
            stableMa = if (r.has("stableMa")) r.optDouble("stableMa", 0.0).toFloat() else null,
            cores = r.optJSONArray("cores")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    CpuCore(
                        index = o.optInt("i"),
                        curFreqKhz = o.optLong("cur"),
                        maxFreqKhz = o.optLong("max"),
                    )
                }
            } ?: emptyList(),
            ram = ramArr?.let { arr ->
                RamInfo(
                    totalMb = r.optLong("ramTotal"),
                    availMb = r.optLong("ramAvail"),
                    swapTotalMb = r.optLong("ramSwapTotal"),
                    swapFreeMb = r.optLong("ramSwapFree"),
                    cachedMb = r.optLong("ramCached"),
                    thresholdMb = r.optLong("ramThreshold"),
                    lowMemory = r.optBoolean("ramLow"),
                    apps = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        RamApp(o.optString("name"), o.optLong("pss"))
                    },
                )
            },
            proc = procArr?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    ProcEntry(
                        label = o.optString("label"),
                        pssMb = o.optLong("pss"),
                        pid = o.optInt("pid", -1),
                        foregroundMin = o.optLong("fg", -1L),
                    )
                }
            } ?: emptyList(),
            histories = r.optJSONObject("histories")?.let { obj ->
                obj.keys().asSequence().associateWith { key ->
                    val a = obj.optJSONArray(key) ?: return@associateWith emptyList()
                    (0 until a.length()).map { a.optDouble(it, 0.0).toFloat() }
                }
            } ?: emptyMap(),
        )
    } catch (e: Exception) {
        Log.w(TAG, "cache read failed; starting cold", e)
        null
    }
}

private fun floats(v: List<Float>) = JSONArray().also { arr -> v.forEach { arr.put(it.toDouble()) } }

suspend fun saveSampleCache(
    context: Context,
    mon: MonitorState,
    ram: RamInfo?,
    proc: List<ProcEntry>,
) = withContext(Dispatchers.IO) {
    try {
        val r = JSONObject()
        r.put("v", VERSION)
        r.put("memTotal", mon.mem.totalMb)
        r.put("memAvail", mon.mem.availMb)
        r.put("swapTotal", mon.mem.swapTotalMb)
        r.put("swapFree", mon.mem.swapFreeMb)
        r.put("memCached", mon.mem.cachedMb)
        r.put("memThreshold", mon.mem.thresholdMb)
        r.put("lowMemory", mon.mem.lowMemory)
        r.put("storageTotal", mon.storage.totalMb)
        r.put("storageFree", mon.storage.freeMb)
        r.put(
            "cores",
            JSONArray().also { arr ->
                mon.cores.forEach { c ->
                    arr.put(
                        JSONObject()
                            .put("i", c.index)
                            .put("cur", c.curFreqKhz)
                            .put("max", c.maxFreqKhz),
                    )
                }
            },
        )
        r.put("rxBps", mon.net.rxBps.toDouble())
        r.put("txBps", mon.net.txBps.toDouble())
        mon.stableMa?.let { r.put("stableMa", it.toDouble()) }

        ram?.let { m ->
            r.put("ramTotal", m.totalMb)
            r.put("ramAvail", m.availMb)
            r.put("ramSwapTotal", m.swapTotalMb)
            r.put("ramSwapFree", m.swapFreeMb)
            r.put("ramCached", m.cachedMb)
            r.put("ramThreshold", m.thresholdMb)
            r.put("ramLow", m.lowMemory)
            r.put(
                "ramApps",
                JSONArray().also { arr ->
                    m.apps.take(12).forEach { a ->
                        arr.put(JSONObject().put("name", a.name).put("pss", a.pssMb))
                    }
                },
            )
        }

        r.put(
            "proc",
            JSONArray().also { arr ->
                proc.take(40).forEach { p ->
                    arr.put(
                        JSONObject()
                            .put("label", p.label)
                            .put("pss", p.pssMb)
                            .put("pid", p.pid)
                            .put("fg", p.foregroundMin),
                    )
                }
            },
        )

        r.put(
            "histories",
            JSONObject().apply {
                put("mem", floats(mon.memHist.values()))
                put("swap", floats(mon.swapHist.values()))
                put("rx", floats(mon.rxHist.values()))
                put("tx", floats(mon.txHist.values()))
                put("cpu", floats(mon.cpuHist.values()))
                put("temp", floats(mon.tempHist.values()))
                put("power", floats(mon.powerHist.values()))
            },
        )

        // Write-then-rename so a kill mid-write cannot leave a truncated file.
        val f = cacheFile(context)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(r.toString())
        if (!tmp.renameTo(f)) f.writeText(r.toString())
    } catch (e: Exception) {
        Log.w(TAG, "cache write failed", e)
    }
}
