package com.ntop.app.stats

import kotlin.math.abs

/**
 * Fuel-gauge current read from
 * [android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] comes straight off
 * the PMIC and is noisy: on a Nothing Phone (3) back-to-back 1 s samples swing
 * by tens of mA, which is enough to make both the in-app POWER NOW readout and
 * the Glyph Toy visibly flicker.
 *
 * [update] runs an exponential moving average with a fixed [alpha], then holds
 * the emitted value until the average has genuinely moved further than
 * [deadbandMa]. The value handed to a UI therefore changes on a real move
 * rather than on every tick. A null sample — the gauge reporting 0 or
 * "unsupported", common at rest and on slow USB charging — holds the last
 * value instead of blanking the readout.
 *
 * One instance per consumer; it is not thread-safe and is meant to be driven
 * from a single sampler (the app's 1 s loop, or the Glyph Toy's own loop).
 */
class CurrentSmoother(
    private val alpha: Float = 0.25f,
    private val deadbandMa: Float = 25f,
) {
    private var ema: Float? = null
    private var shown: Float? = null

    /**
     * Feeds one raw sample in mA. Returns the value to display, or null if no
     * usable reading has been seen yet.
     */
    fun update(rawMa: Float?): Float? {
        val prev = ema
        ema = when {
            rawMa == null -> prev
            prev == null -> rawMa
            else -> prev + (rawMa - prev) * alpha
        }
        val next = ema ?: return null
        val cur = shown
        if (cur == null || abs(next - cur) >= deadbandMa) shown = next
        return shown
    }

    /**
     * Drops the average and the displayed value. Call when the charge state
     * flips, otherwise the average has to walk from a discharging current up
     * to (or down from) a charging one and the readout trails reality.
     */
    fun reset() {
        ema = null
        shown = null
    }
}
