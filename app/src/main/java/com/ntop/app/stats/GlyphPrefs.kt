package com.ntop.app.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// ---------------------------------------------------------------------------
// User configuration for the dynamic Glyph Toy.
//
// The toy picks a [GlyphMode] per refresh from its inputs, in order:
//   1. charging (if the user left it on)  -> WATTS
//   2. foreground app the user configured -> the mode mapped to that package
//   3. nothing configured                 -> USERNAME, the always-on default
//
// The app map is stored as a StringSet of "pkg=MODE" so it fits a plain
// Preferences DataStore. Malformed entries are skipped on read rather than
// crashing a service that is mid-frame.
// ---------------------------------------------------------------------------

enum class GlyphMode {
    SMILEY,
    USERNAME,
    WATTS,
    WAVEFORM;

    companion object {
        fun parse(s: String): GlyphMode? = entries.firstOrNull { it.name == s }
    }
}

/** Shown when the user has not mapped the foreground app to anything. */
val DEFAULT_GLYPH_MODE = GlyphMode.SMILEY

data class GlyphSettings(
    val username: String = "",
    val chargingEnabled: Boolean = true,
    val appModes: Map<String, GlyphMode> = emptyMap(),
) {
    /** Mode the user assigned to [pkg], or null when it is not configured. */
    fun modeFor(pkg: String?): GlyphMode? = pkg?.let { appModes[it] }

    fun usernameOr(fallback: String): String = username.trim().ifEmpty { fallback }
}

private val Context.glyphStore: DataStore<Preferences> by preferencesDataStore("ntop_glyph")

private val KEY_USERNAME = stringPreferencesKey("glyph_username")
private val KEY_CHARGING = booleanPreferencesKey("glyph_charging_enabled")
private val KEY_APP_MODES = stringSetPreferencesKey("glyph_app_modes")

fun glyphSettings(context: Context): Flow<GlyphSettings> = context.glyphStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { p ->
        val modes = p[KEY_APP_MODES].orEmpty().mapNotNull { entry ->
            val sep = entry.indexOf('=')
            if (sep <= 0) return@mapNotNull null
            val pkg = entry.substring(0, sep)
            val mode = GlyphMode.parse(entry.substring(sep + 1)) ?: return@mapNotNull null
            pkg to mode
        }.toMap()
        GlyphSettings(
            username = p[KEY_USERNAME].orEmpty(),
            chargingEnabled = p[KEY_CHARGING] ?: true,
            appModes = modes,
        )
    }

suspend fun setGlyphUsername(context: Context, value: String) {
    context.glyphStore.edit { it[KEY_USERNAME] = value.trim() }
}

suspend fun setGlyphCharging(context: Context, enabled: Boolean) {
    context.glyphStore.edit { it[KEY_CHARGING] = enabled }
}

/** Assigns [mode] to [pkg]; pass null to clear that app's mapping. */
suspend fun setGlyphAppMode(context: Context, pkg: String, mode: GlyphMode?) {
    context.glyphStore.edit { prefs ->
        val existing = prefs[KEY_APP_MODES].orEmpty().toMutableSet()
        // Drop any prior entry for this package first, otherwise both survive
        // in the set and whichever the reader hits first wins.
        existing.removeAll { it.startsWith("$pkg=") }
        if (mode != null) existing += "$pkg=${mode.name}"
        prefs[KEY_APP_MODES] = existing
    }
}
