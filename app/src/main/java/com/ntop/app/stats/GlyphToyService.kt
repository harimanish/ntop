package com.ntop.app.stats

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------------------------------------------------------------------------
// ntop dynamic Glyph Toy.
//
// The toy is a regular app-process service bound by com.nothing.thirdparty
// when the user selects it in the Glyph carousel, and it stays bound for AOD.
// Which glyph is on the matrix is decided every tick by [resolveMode]:
//
//   charging (user-enabled)      -> WATTS
//   foreground app is configured -> the mode the user mapped it to
//   otherwise                    -> USERNAME, the always-on default
//
// Layout facts reverse-engineered from glyph-matrix-sdk-2.0 (see
// GlyphRenderers.kt): the canvas is auto-sized to
// Common.getDeviceMatrixLength()^2 with no size setter, and the text path
// honours only position + brightness (scale is ignored), so sizing comes from
// choosing how many characters to draw. Bitmaps go through setImageSource(),
// which does honour scale, and need to be square.
// ---------------------------------------------------------------------------

private const val TAG = "ntop.GlyphToy"

/** How long a cached charge/foreground signal stays valid. */
private const val SIGNAL_TTL_MS = 1_000L

class GlyphToyService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var gm: GlyphMatrixManager? = null
    private var deviceId: String? = null
    private var matrix = 0

    private var bound = false
    private var connected = false

    private var settings = GlyphSettings()
    private var settingsJob: Job? = null
    private var loopJob: Job? = null

    private val currentSmoother = CurrentSmoother(alpha = 0.35f, deadbandMa = 40f)
    private var lastCharging: Boolean? = null
    private var lastFrameKey: String? = null

    private var marquee: UsernameMarquee? = null
    private var marqueeFor: String? = null
    private var marqueeOffset = 0

    // Both depend on the matrix size, only known in initMatrix(), so they are
    // created there. The renderer owns a reusable bitmap — a fresh one per
    // frame would allocate 90x a second inside the waveform loop.
    private var synth: WaveformSynth? = null
    private var bars: WaveformBars? = null
    private var smiley: SmileyRenderer? = null
    private var animTick = 0
    /** True while the system is driving the dim always-on frame. */
    private var aodMode = false

    /** Signal cache; see foregroundCached/chargingCached. */
    private var fgCached: String? = null
    private var fgCachedAt = 0L
    private var chargingCached = false
    private var chargingCachedAt = 0L

    /** Frame diagnostics; see pushFrame. */
    private var lastLoggedMode: String? = null

    private val eventHandler = object : android.os.Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    // The system sends this once a minute while the toy is the
                    // AOD toy. It is the only reliable AOD signal:
                    // PowerManager.isInteractive stays TRUE on Nothing's
                    // always-on display because the panel is technically lit,
                    // so testing that alone never dims the frame.
                    GlyphToy.EVENT_AOD -> {
                        aodMode = true
                        renderOnce()
                    }

                    GlyphToy.EVENT_CHANGE -> {
                        // Long press: the user is interacting again, so this is
                        // no longer the dim lock-screen frame.
                        aodMode = false
                        currentSmoother.reset()
                        lastFrameKey = null
                        renderOnce()
                    }

                    GlyphToy.EVENT_ACTION_UP -> {
                        aodMode = false
                        renderOnce()
                    }

                    else -> Unit
                }
            } else {
                super.handleMessage(msg)
            }
        }
    }
    private val messenger = Messenger(eventHandler)

    override fun onBind(intent: Intent?): IBinder? {
        bound = true
        if (gm == null) initMatrix()
        observeSettings()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return false
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        bound = false
        connected = false
        loopJob?.cancel()
        loopJob = null
        settingsJob?.cancel()
        settingsJob = null
        currentSmoother.reset()
        lastCharging = null
        lastFrameKey = null
        marquee = null
        marqueeFor = null
        try {
            gm?.turnOff()
            gm?.unInit()
        } catch (e: Exception) {
            Log.w(TAG, "teardown: glyph service refused shutdown", e)
        }
        gm = null
    }

    private fun observeSettings() {
        if (settingsJob?.isActive == true) return
        settingsJob = scope.launch {
            glyphSettings(applicationContext).collectLatest { s ->
                settings = s
                // A different name needs a different strip bitmap.
                if (s.username != marqueeFor) {
                    marquee = null
                    marqueeFor = null
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    private fun initMatrix() {
        // Device is resolved at runtime: Phone (3) is 25x25, Phone (4a) Pro
        // 13x13. Registering the wrong id yields a silently blank matrix.
        val id = when {
            Common.is23112() -> Glyph.DEVICE_23112
            Common.is25111p() -> Glyph.DEVICE_25111p
            else -> null
        }
        val len = Common.getDeviceMatrixLength()
        if (id == null || len <= 0) {
            Log.w(TAG, "no Glyph Matrix on this device; toy will idle")
            return
        }
        deviceId = id
        matrix = len
        bars = WaveformBars(len)
        synth = WaveformSynth(if (len >= 25) 11 else 7)
        smiley = SmileyRenderer(len)

        val m = GlyphMatrixManager.getInstance(applicationContext)
        gm = m
        try {
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    val ok = try {
                        m.register(deviceId!!)
                    } catch (e: Exception) {
                        Log.w(TAG, "register($deviceId) failed", e)
                        false
                    }
                    if (!ok) {
                        Log.w(TAG, "register($deviceId) rejected by glyph service")
                        return
                    }
                    connected = true
                    Log.i(TAG, "registered $deviceId, matrix ${len}x$len")
                    startLoop()
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    connected = false
                    loopJob?.cancel()
                    loopJob = null
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "init failed", e)
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive && bound && connected) {
                renderOnce()
                delay(tickMs())
            }
        }
    }

    /** Cadence follows the active mode: meters are slow, animations are fast. */
    private fun tickMs(): Long {
        val aod = isAod()
        return when (resolveMode()) {
            GlyphMode.WAVEFORM -> if (aod) 220L else 70L
            GlyphMode.USERNAME -> if (aod) 160L else 90L
            GlyphMode.SMILEY -> if (aod) 200L else 100L
            GlyphMode.WATTS -> if (aod) 15_000L else 5_000L
        }
    }

    // -------------------------------------------------------------------------
    // Mode resolution
    // -------------------------------------------------------------------------

    private fun isCharging(): Boolean {
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    // Both signals are cached: the marquee ticks every ~90ms, and
    // UsageStatsManager.queryEvents is an IPC plus a cursor walk. Re-running
    // them per tick is what makes a scrolling glyph stutter.
    private fun foregroundCached(): String? {
        // Nothing is mapped, so there is nothing to match against — skip the
        // query entirely, which is the common case.
        if (settings.appModes.isEmpty()) return null
        val now = SystemClock.elapsedRealtime()
        if (now - fgCachedAt < SIGNAL_TTL_MS) return fgCached
        fgCached = foregroundApp(applicationContext)
        fgCachedAt = now
        return fgCached
    }

    private fun chargingCached(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - chargingCachedAt < SIGNAL_TTL_MS) return chargingCached
        chargingCached = isCharging()
        chargingCachedAt = now
        return chargingCached
    }

    fun resolveMode(): GlyphMode {
        if (settings.chargingEnabled && chargingCached()) return GlyphMode.WATTS
        val mapped = settings.modeFor(foregroundCached())
        if (mapped != null) return mapped
        return DEFAULT_GLYPH_MODE
    }

    private fun isInteractive(): Boolean = try {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    } catch (e: Exception) {
        true
    }

    /**
     * Always-on rendering, from either signal: the SDK's EVENT_AOD (authoritative,
     * and the only one that still works while AOD keeps the panel "interactive"),
     * or a fully non-interactive screen for the plain screen-off case.
     */
    private fun isAod(): Boolean = aodMode || !isInteractive()

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private fun renderOnce() {
        val m = gm ?: return
        if (!connected) return
        animTick++
        val brightness = if (isAod()) BRIGHT_AOD else BRIGHT_ON

        when (resolveMode()) {
            GlyphMode.SMILEY -> {
                val face = smiley ?: return
                val obj = face.render(animTick, brightness)
                m.pushFrame("S$animTick:$brightness") { addTop(obj) }
            }

            GlyphMode.USERNAME -> {
                val obj = usernameFrame() ?: return
                // A scrolling marquee changes every tick, so its key is
                // deliberately unique — it must push every frame.
                marqueeOffset += 1
                m.pushFrame("U$marqueeOffset:$brightness") { addTop(obj) }
            }

            GlyphMode.WAVEFORM -> {
                val wf = synth ?: return
                val renderer = bars ?: return
                val sample = wf.update(audioPlaying(applicationContext))
                val obj = renderer.render(sample, brightness)
                m.pushFrame("W${sample.contentHashCode()}:$brightness") { addTop(obj) }
            }

            GlyphMode.WATTS -> renderWatts(m, brightness)
        }
    }

    private fun usernameFrame(): GlyphMatrixObject? {
        val name = settings.usernameOr("ntop")
        var mm = marquee
        if (mm == null || marqueeFor != name) {
            mm = UsernameMarquee(matrix, name, Typeface.MONOSPACE)
            marquee = mm
            marqueeFor = name
            marqueeOffset = 0
        }
        return mm.renderAt(marqueeOffset)
    }



    private fun renderWatts(m: GlyphMatrixManager, brightness: Int) {
        val p = readPower() ?: return
        val wattsText = if (p.charging) p.watts?.let { formatWatts(it) } else null
        val pctText = p.pct.toString()
        m.pushFrame("P$wattsText|$pctText|$brightness") {
            if (wattsText != null) {
                val topY: Int
                val lowY: Int
                if (matrix >= 25) {
                    // 6 rows of text, 1px gap, 6 more — centred in the matrix.
                    topY = (matrix - (GLYPH_ROWS * 2 + 1)) / 2
                    lowY = topY + GLYPH_ROWS + 1
                } else {
                    // 13x13: exactly two 6-row lines, nothing left over.
                    topY = 0
                    lowY = GLYPH_ROWS + 1
                }
                addTop(text(wattsText, glyphCenterX(wattsText, matrix), topY, brightness))
                addLow(text(pctText, glyphCenterX(pctText, matrix), lowY, brightness))
            } else {
                // No charging watts to show: battery % alone, vertically centred.
                val y = (matrix - GLYPH_ROWS) / 2
                addTop(text(pctText, glyphCenterX(pctText, matrix), y, brightness))
            }
        }
    }

    private fun text(s: String, x: Int, y: Int, brightness: Int): GlyphMatrixObject =
        GlyphMatrixObject.Builder()
            .setText(s)
            .setPosition(x, y)
            .setBrightness(brightness)
            .build()

    /**
     * 25x25 has room for a decimal and the unit ("18.4W", 22px wide); 13x13 has
     * 13px in total, so the unit is dropped and the value is rounded instead.
     */
    private fun formatWatts(w: Float): String =
        if (matrix >= 25) "%.1fW".format(java.util.Locale.US, w)
        else "%.0f".format(java.util.Locale.US, w)

    // -------------------------------------------------------------------------
    // Power flow
    // -------------------------------------------------------------------------

    private data class Power(
        val pct: Int,
        val charging: Boolean,
        val watts: Float?,
    )

    private fun readPower(): Power? = try {
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val pct = if (level >= 0) level * 100 / scale else -1
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val mv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val voltageV = if (mv > 0) mv / 1000f else Float.NaN
        if (pct < 0) {
            null
        } else {
            if (charging != lastCharging) {
                currentSmoother.reset()
                lastCharging = charging
            }
            val ma = currentSmoother.update(readCurrentMa())
            Power(pct, charging, if (ma != null && !voltageV.isNaN()) abs(voltageV * ma / 1000f) else null)
        }
    } catch (e: Exception) {
        Log.w(TAG, "readPower failed", e)
        null
    }

    private fun readCurrentMa(): Float? = try {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw != 0 && raw != Int.MIN_VALUE && abs(raw) <= 15_000_000) raw / 1000f else null
    } catch (e: Exception) {
        null
    }

    /** Pushes a frame unless the content is byte-identical to the last one. */
    private fun GlyphMatrixManager.pushFrame(
        key: String,
        build: GlyphMatrixFrame.Builder.() -> Unit,
    ): Boolean {
        if (key == lastFrameKey) return false
        try {
            val builder = GlyphMatrixFrame.Builder().apply(build)
            val data = builder.build(this@GlyphToyService).render()
            // The matrix cannot be inspected from the app, so report what was
            // actually sent. An all-zero frame means the render path produced
            // nothing even though registration succeeded.
            var lit = 0
            var peak = 0
            for (v in data) {
                if (v != 0) lit++
                if (v > peak) peak = v
            }
            setMatrixFrame(data)
            lastFrameKey = key
            // The matrix cannot be inspected from the app, so report what was
            // actually sent — this is how a blank render is diagnosed without
            // seeing the hardware. Logged only when the mode changes: the
            // animated modes produce a new key every single frame.
            val mode = key.substringBefore(':')
            if (mode != lastLoggedMode) {
                lastLoggedMode = mode
                Log.i(TAG, "frame mode=$mode lit=$lit/${data.size} peak=$peak aod=$aodMode")
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "setMatrixFrame failed", e)
            return false
        }
    }
}
