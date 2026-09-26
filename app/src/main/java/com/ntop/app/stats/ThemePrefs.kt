package com.ntop.app.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ---------------------------------------------------------------------------
// ntop Lucent theme prefs: bottom-bar tint opacity, blur radius and accent
// index. Same DataStore pattern as the battery store.
// ---------------------------------------------------------------------------

private val Context.themeStore by preferencesDataStore(name = "ntop_theme")

private val KEY_BAR_ALPHA = intPreferencesKey("bar_alpha_pct")
private val KEY_BLUR_DP = intPreferencesKey("blur_dp")
private val KEY_ACCENT = intPreferencesKey("accent_idx")

const val BAR_ALPHA_MIN = 50
const val BAR_ALPHA_MAX = 95
const val BAR_ALPHA_DEFAULT = 80
const val BLUR_MIN = 0
const val BLUR_MAX = 32
const val BLUR_DEFAULT = 24
const val ACCENT_DEFAULT = 0
const val ACCENT_COUNT = 6

data class ThemeSettings(
    val barAlphaPct: Int = BAR_ALPHA_DEFAULT,
    val blurDp: Int = BLUR_DEFAULT,
    val accentIdx: Int = ACCENT_DEFAULT,
)

fun observeThemeSettings(context: Context): Flow<ThemeSettings> =
    context.themeStore.data.map { p ->
        ThemeSettings(
            barAlphaPct = p[KEY_BAR_ALPHA]?.coerceIn(BAR_ALPHA_MIN, BAR_ALPHA_MAX)
                ?: BAR_ALPHA_DEFAULT,
            blurDp = p[KEY_BLUR_DP]?.coerceIn(BLUR_MIN, BLUR_MAX) ?: BLUR_DEFAULT,
            accentIdx = p[KEY_ACCENT]?.coerceIn(0, ACCENT_COUNT - 1) ?: ACCENT_DEFAULT,
        )
    }

suspend fun setBarAlpha(context: Context, pct: Int) {
    context.themeStore.edit { it[KEY_BAR_ALPHA] = pct.coerceIn(BAR_ALPHA_MIN, BAR_ALPHA_MAX) }
}

suspend fun setBlurDp(context: Context, dp: Int) {
    context.themeStore.edit { it[KEY_BLUR_DP] = dp.coerceIn(BLUR_MIN, BLUR_MAX) }
}

suspend fun setAccent(context: Context, idx: Int) {
    context.themeStore.edit { it[KEY_ACCENT] = idx.coerceIn(0, ACCENT_COUNT - 1) }
}
