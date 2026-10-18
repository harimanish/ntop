package com.ntop.app.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

// ---------------------------------------------------------------------------
// User configuration for the dynamic Glyph Toy.
//
// The toy resolves a [GlyphMode] every frame from live signals, in order:
//   1. charging (if the user left it on)     -> WATTS
//   2. a PLAYING group while audio plays     -> that group's mode
//   3. a group that owns the foreground app  -> that group's mode
//   4. nothing matched                       -> defaultMode (SMILEY)
//
// Groups are user-built combos: a name, a set of packages, the glyph to show
// and a [GlyphTrigger]. A PLAYING group wins while audio is active. Public
// APIs cannot attribute playback to a package, so that trigger is about the
// moment ("music is playing") — which for a music group is exactly the point;
// it also matches while one of its apps is in the foreground.
//
// Groups are stored as a JSON array string so they fit a plain Preferences
// DataStore. Malformed data is skipped on read rather than crashing a service
// that is mid-frame. The pre-groups per-app map (0.4.x) is migrated on first
// read into one IN_USE group per mode.
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

/** When a group takes over the matrix. */
enum class GlyphTrigger {
    /** The group owns the app currently in the foreground. */
    IN_USE,

    /** Audio is playing (any app — playback attribution is not public API), or the group owns the foreground app. */
    PLAYING;

    companion object {
        fun parse(s: String): GlyphTrigger? = entries.firstOrNull { it.name == s }
    }
}

/** A user-built glyph combo: these apps, this mode, under this trigger. */
data class GlyphGroup(
    val id: String,
    val name: String,
    val apps: Set<String> = emptySet(),
    val mode: GlyphMode = DEFAULT_GLYPH_MODE,
    val trigger: GlyphTrigger = GlyphTrigger.IN_USE,
)

/** Look knobs for WAVEFORM, shared by the toy service and the tab preview. */
data class GlyphWave(
    val mirror: Boolean = false,
    val sensitivity: Float = 1f,
    val smoothing: Float = 0.72f,
)

/** Out-of-the-box fallback mode; the user can change it in the GLYPH tab. */
val DEFAULT_GLYPH_MODE = GlyphMode.SMILEY

data class GlyphSettings(
    val username: String = "",
    val chargingEnabled: Boolean = true,
    val defaultMode: GlyphMode = DEFAULT_GLYPH_MODE,
    val groups: List<GlyphGroup> = emptyList(),
    val wave: GlyphWave = GlyphWave(),
) {
    fun usernameOr(fallback: String): String = username.trim().ifEmpty { fallback }
}

/**
 * Scene -> mode. Shared verbatim by the toy service and the GLYPH tab
 * preview, so what the page shows is what the matrix gets.
 */
fun GlyphSettings.resolveMode(
    charging: Boolean,
    foregroundPkg: String?,
    audioPlaying: Boolean,
): GlyphMode {
    if (chargingEnabled && charging) return GlyphMode.WATTS
    groups.firstOrNull { it.trigger == GlyphTrigger.PLAYING && audioPlaying }?.let {
        return it.mode
    }
    if (foregroundPkg != null) {
        groups.firstOrNull { foregroundPkg in it.apps }?.let { return it.mode }
    }
    return defaultMode
}

private val Context.glyphStore: DataStore<Preferences> by preferencesDataStore("ntop_glyph")

private val KEY_USERNAME = stringPreferencesKey("glyph_username")
private val KEY_CHARGING = booleanPreferencesKey("glyph_charging_enabled")
private val KEY_DEFAULT_MODE = stringPreferencesKey("glyph_default_mode")
private val KEY_GROUPS = stringPreferencesKey("glyph_groups")

private val KEY_WAVE_MIRROR = booleanPreferencesKey("glyph_wave_mirror")
private val KEY_WAVE_SENSITIVITY = floatPreferencesKey("glyph_wave_sensitivity")
private val KEY_WAVE_SMOOTHING = floatPreferencesKey("glyph_wave_smoothing")

/** Pre-groups per-app map ("pkg=MODE"); read once and migrated. */
private val KEY_APP_MODES = stringSetPreferencesKey("glyph_app_modes")

fun glyphSettings(context: Context): Flow<GlyphSettings> = context.glyphStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { p ->
        GlyphSettings(
            username = p[KEY_USERNAME].orEmpty(),
            chargingEnabled = p[KEY_CHARGING] ?: true,
            defaultMode = GlyphMode.parse(p[KEY_DEFAULT_MODE].orEmpty()) ?: DEFAULT_GLYPH_MODE,
            // A present-but-empty KEY_GROUPS is an explicit empty list; absence
            // means the install predates groups and the old map gets migrated.
            groups = p[KEY_GROUPS]?.let(::decodeGroups) ?: legacyGroups(p[KEY_APP_MODES].orEmpty()),
            wave = GlyphWave(
                mirror = p[KEY_WAVE_MIRROR] ?: false,
                sensitivity = p[KEY_WAVE_SENSITIVITY] ?: 1f,
                smoothing = p[KEY_WAVE_SMOOTHING] ?: 0.72f,
            ),
        )
    }

suspend fun setGlyphUsername(context: Context, value: String) {
    context.glyphStore.edit { it[KEY_USERNAME] = value.trim() }
}

suspend fun setGlyphCharging(context: Context, enabled: Boolean) {
    context.glyphStore.edit { it[KEY_CHARGING] = enabled }
}

suspend fun setGlyphDefaultMode(context: Context, mode: GlyphMode) {
    context.glyphStore.edit { it[KEY_DEFAULT_MODE] = mode.name }
}

suspend fun setGlyphGroups(context: Context, groups: List<GlyphGroup>) {
    context.glyphStore.edit { it[KEY_GROUPS] = encodeGroups(groups) }
}

suspend fun setGlyphWave(context: Context, wave: GlyphWave) {
    context.glyphStore.edit {
        it[KEY_WAVE_MIRROR] = wave.mirror
        it[KEY_WAVE_SENSITIVITY] = wave.sensitivity
        it[KEY_WAVE_SMOOTHING] = wave.smoothing
    }
}

// ---------------------------------------------------------------------------
// Persistence: groups as JSON in one string key.
// ---------------------------------------------------------------------------

private fun encodeGroups(groups: List<GlyphGroup>): String {
    val arr = JSONArray()
    groups.forEach { g ->
        arr.put(
            JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("mode", g.mode.name)
                put("trigger", g.trigger.name)
                put("apps", JSONArray(g.apps.toList()))
            },
        )
    }
    return arr.toString()
}

private fun decodeGroups(raw: String): List<GlyphGroup> = try {
    val arr = JSONArray(raw)
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mode = GlyphMode.parse(o.optString("mode")) ?: continue
            val appsArr = o.optJSONArray("apps")
            val apps = buildSet {
                for (j in 0 until (appsArr?.length() ?: 0)) {
                    appsArr?.optString(j)?.takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
            add(
                GlyphGroup(
                    id = o.optString("id").takeIf { it.isNotEmpty() } ?: "g$i",
                    name = o.optString("name"),
                    apps = apps,
                    mode = mode,
                    trigger = GlyphTrigger.parse(o.optString("trigger")) ?: GlyphTrigger.IN_USE,
                ),
            )
        }
    }
} catch (e: Exception) {
    emptyList()
}

/**
 * 0.4.x stored "pkg=MODE" pairs. Group them by mode into one IN_USE group per
 * mode so existing assignments keep working (names use the UI label).
 */
private fun legacyGroups(raw: Set<String>): List<GlyphGroup> {
    val parsed = raw.mapNotNull { entry ->
        val sep = entry.indexOf('=')
        if (sep <= 0) return@mapNotNull null
        val mode = GlyphMode.parse(entry.substring(sep + 1)) ?: return@mapNotNull null
        entry.substring(0, sep) to mode
    }
    return parsed
        .groupBy({ it.second }, { it.first })
        .map { (mode, pkgs) ->
            GlyphGroup(
                id = "legacy-${mode.name.lowercase()}",
                name = if (mode == GlyphMode.WATTS) "POWER" else mode.name,
                apps = pkgs.toSet(),
                mode = mode,
                trigger = GlyphTrigger.IN_USE,
            )
        }
}
