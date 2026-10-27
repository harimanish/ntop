package com.ntop.app.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// ---------------------------------------------------------------------------
// Launch intro preference.
//
// Three intros, chosen in SYS > APPEARANCE > LAUNCH ANIMATION and read once at
// cold start, before the splash composes. BLACKHOLE is the default.
//
// Kept in its own DataStore: the glyph store does a lot of decoding work and
// the splash must not wait for any of it.
// ---------------------------------------------------------------------------

/** Which launch intro plays on a cold start. */
enum class SplashIntro {
    /** The dots spiral into a black hole, which then bursts the mark back out. */
    BLACKHOLE,

    /** A red sun ignites, the dots orbit it, then collapse into the mark. */
    SOLAR,

    /** The original: the icon's dots fly in from every direction. */
    FLY_IN;

    companion object {
        fun parse(s: String): SplashIntro? = entries.firstOrNull { it.name == s }
    }
}

private val Context.launchStore: DataStore<Preferences> by preferencesDataStore("ntop_launch")

private val KEY_SPLASH_INTRO = stringPreferencesKey("splash_intro")

fun splashIntro(context: Context): Flow<SplashIntro> = context.launchStore.data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { p -> SplashIntro.parse(p[KEY_SPLASH_INTRO].orEmpty()) ?: SplashIntro.BLACKHOLE }

suspend fun setSplashIntro(context: Context, intro: SplashIntro) {
    context.launchStore.edit { it[KEY_SPLASH_INTRO] = intro.name }
}

/**
 * One-shot read for the splash itself: it needs the answer before the first
 * frame, and the DataStore's first emission is a few milliseconds away.
 */
suspend fun readSplashIntro(context: Context): SplashIntro = splashIntro(context).first()
