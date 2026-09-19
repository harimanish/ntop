package com.ntop.app.ui.components

import android.os.Build

// ---------------------------------------------------------------------------
// Official device renders, fetched from the Nothing store collection:
// https://in.nothing.tech/collections/phones (Shopify CDN, width=1000 for
// card quality).
//
// Mapping covers model numbers (e.g. A024), device codenames (e.g. metroid)
// and marketing names (e.g. "Phone (3)"), matched against Build.MODEL,
// Build.DEVICE and Build.PRODUCT. Unknown / non-Nothing devices return null
// and the DeviceCard falls back to the hand-drawn Phone3Render vector.
// ---------------------------------------------------------------------------

private const val CDN = "https://cdn.shopify.com/s/files/1/0586/3270/0077/files"

private const val PHONE_3 = "$CDN/0000s_0011_Phone-3-white.png?v=1753757325&width=1000"
private const val PHONE_3A = "$CDN/0000s_0008_Phone-3a-black.png?v=1753757370&width=1000"
private const val PHONE_3A_PRO = "$CDN/0000s_0010_Phone-3a-Pro-black.png?v=1753757371&width=1000"
private const val PHONE_3A_LITE = "$CDN/BulbT-Black.png?v=1761642504&width=1000"
private const val PHONE_4A = "$CDN/Phone-4a-Black.png?v=1772251226&width=1000"
private const val PHONE_4A_PRO = "$CDN/Phone-4a-Pro-Black.png?v=1772251228&width=1000"
private const val PHONE_4B = "$CDN/product-thumbnail-black.webp?v=1783318425&width=1000"
private const val CMF_2_PRO = "$CDN/0000s_0058_CMF-Phone-2-Pro-black.png?v=1753757371&width=1000"
private const val CMF_1 = "$CDN/0000s_0062_CMF-Phone-1-Black.png?v=1753757365&width=1000"

/** Marketing name + render URL for a known device, null when unrecognized. */
data class DeviceRender(
    val name: String,
    val imageUrl: String,
)

fun resolveDeviceRender(): DeviceRender? {
    // Longer / more specific keys first so "a059p" wins over "a059",
    // "phone (3a) pro" wins over "phone (3a)", etc.
    val keys = sequenceOf(Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.HARDWARE)
        .map { it.lowercase().trim() }
        .joinToString(" | ")
    fun has(vararg needles: String) = needles.any { it in keys }

    return when {
        has("a069p", "froggerpro", "phone (4a) pro") ->
            DeviceRender("Phone (4a) Pro", PHONE_4A_PRO)
        has("a009p", "supercontra", "phone (4b)") ->
            DeviceRender("Phone (4b)", PHONE_4B)
        has("a069", "frogger", "phone (4a)") ->
            DeviceRender("Phone (4a)", PHONE_4A)
        has("a001t", "galaxian", "phone (3a) lite") ->
            DeviceRender("Phone (3a) Lite", PHONE_3A_LITE)
        has("a059p", "phone (3a) pro") ->
            DeviceRender("Phone (3a) Pro", PHONE_3A_PRO)
        has("a059", "asteroids", "phone (3a)") ->
            // A059P shares the "asteroids" codename; the model-number branch
            // above catches Pro first, so plain asteroids == Phone (3a).
            DeviceRender("Phone (3a)", PHONE_3A)
        has("a024", "metroid", "arbok", "phone (3)") ->
            DeviceRender("Phone (3)", PHONE_3)
        has("a001", "galaga", "cmf phone 2 pro") ->
            DeviceRender("CMF Phone 2 Pro", CMF_2_PRO)
        has("a142p", "pacmanpro", "phone (2a) plus", "phone (2a)+") ->
            // No official render on the current collection page; vector fallback.
            null
        has("a142", "pacman", "phone (2a)") -> null
        has("a065", "ain065", "pong", "phone (2)") -> null
        has("a063", "spacewar", "phone (1)") -> null
        has("a015", "tetris", "cmf phone 1") ->
            DeviceRender("CMF Phone 1", CMF_1)
        else -> null
    }
}
