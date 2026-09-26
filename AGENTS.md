# AGENTS.md — ntop contributor context

`ntop` — btop-style system monitor for Nothing phones. Package
`com.ntop.app`, version 0.2.1. Tabs: MON / PROC / BATT / SYS (Compose
`HorizontalPager` + `FluentBottomBar`, Nothing monochrome theme, Geist
fonts, red accent only on charging pill/dot).

## Build & verify

- Toolchain: AGP 8.7.3, Kotlin 2.1.20, Gradle 8.11.1, Java 17,
  `compileSdk/targetSdk 36`, `minSdk 31`. No system gradle — use `./gradlew`.
- Debug: `./gradlew :app:assembleDebug`, install
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Verify on device with screenshots:
  `adb exec-out screencap -p > /tmp/opencode/x.png` then Read it.
  If the screen is asleep: wake (`input keyevent KEYCODE_WAKEUP`),
  `wm dismiss-keyguard`, swipe up, `am start -n com.ntop.app/.MainActivity`.
  Prefer pager swipes (`input swipe 900 1200 300 1200`) over tab taps.
- Release uses R8 (`isMinifyEnabled`, `isShrinkResources`).

## Hard-won platform facts (do not re-learn)

- `/proc/stat` and `/proc/<other-pid>` are **denied** to apps (verified via
  `run-as`). `getProcessMemoryInfo` returns zeros for foreign UIDs on
  Android 10+; `getRunningAppProcesses` is own-UID-only on API 33+.
- CPU load is a **frequency proxy** (`time_in_state`, readable) — label FREQ,
  never %.
- `PowerProfile` reflection is hidden-API-blocked for `targetSdk 36`;
  design mAh comes from DataStore override → reflection attempt → hardcoded
  Nothing map (`BatteryHealth.kt`), e.g. Phone 3 5150 intl / 5500 India.
- **Compose equality freeze**: data-class samples that are tick-identical
  skip recomposition. The 1s loop counter (`tick`) is threaded
  BatteryScreen → AppShell → MonContent/BattContent/ProcContent as a
  recomposition driver. Never remove it.
- **Shizuku integration has three mandatory parts**: `api` + `provider`
  deps, `ShizukuProvider` manifest entry (without it `pingBinder()` is
  always false — diagnosed via AAR inspection + official docs), and a
  `UserService` (`ProcUserService` via `IProcService.aidl`) for privileged
  reads. Raw `transact` via `SystemServiceHelper.getTransactionCode` is
  dead (hidden-API blacklist → null code).
- Shizuku caches user services by **tag + version**: bump one of them
  (`PROC_SERVICE_TAG` / `.version()`) whenever service code changes,
  or the old process keeps serving.
- R8 keeps required: `ProcUserService`, `IProcService*` (see
  `proguard-rules.pro`).
- New tab icons: verify the glyph exists in
  `material-icons-extended` AAR (`unzip -l classes.jar | grep <Name>Kt`)
  — `Bolt`/`Memory` are extended-only and broke a `-core` swap before.
- **Glyph Matrix SDK (`glyph-matrix-sdk-2.0.aar`, local `app/libs/`)**: no
  Maven coordinate — it is a bundled AAR. `NothingKey` meta-data is *not*
  required in 2.0 (the manager never calls `getMetaData`); that was GDK 1.x.
  Toys use `setMatrixFrame`, in-app control uses `setAppMatrixFrame` — do not
  swap them.
- Glyph text layout rules, read out of the AAR bytecode — the canvas is
  auto-sized to `Common.getDeviceMatrixLength()²` (25×25 Phone 3, 13×13 4a
  Pro) and there is **no size setter** on `GlyphMatrixFrame.Builder`, so
  positions must be computed per device. `renderText` honours **only**
  position and brightness: `setScale`/`setOrientation`/`setReverse` are
  ignored for text, so size comes from choosing a character count. Glyph
  advance is `letterWidth + 1` px and every letter is 6 rows tall; in the
  bundled NDot55 font digits are 4 wide, `.` 1, `w` 5. Uppercase resolves
  via `letter_` + `toLowerCase(c)`, and `+ - . : % ! °` come from
  `sSpectialLetters`. `getLetterConfigs` returns **null for empty text** and
  that NPEs in `convertToGlyphMatrix` — never `setText("")`.
- A toy is only **bound when the Glyph carousel selects it**, so `onBind`
  never runs until then, and the Glyph interface rotates toys on its own and
  will unbind a test toy mid-run. There is **no adb keycode for the physical
  Glyph button**, so selecting a toy for testing needs the user to press it.
  The dot-matrix tile in quick settings is the **Glyph torch**, NOT the toy
  carousel — toggling it never binds a toy. To stop the carousel rotating
  away mid-test, remove the other toys in Settings → Glyph Toys.
- **`PowerManager.isInteractive` stays TRUE on Nothing's always-on display**
  (the panel is lit), so it cannot detect AOD. The only reliable AOD signal
  is the SDK's `GlyphToy.EVENT_AOD`, which the system sends once a minute
  while the toy is the always-on toy; `EVENT_CHANGE` means the user is
  interacting again and the frame goes back to full brightness.
- **There is no public API for a live audio session id on API 36**, so
  `Visualizer` cannot be used for another app's playback. Verified in the
  stubs: `AudioPlaybackConfiguration` publishes only `getAudioAttributes()`
  (`getPlayState()`/`getAudioSessionId()` are `@SystemApi`), and the public
  `MediaSession` stub has no `getSessionId()` either. Hidden-API reflection
  is blocked at `targetSdk 36`. The toy therefore draws a synthetic waveform
  gated on the real play/pause signal from
  `AudioManager.activePlaybackConfigurations` (which needs no permission).
  Real samples would need Shizuku-privileged capture or a lower targetSdk.
- Per-app glyphs need **Usage Access** (`PACKAGE_USAGE_STATS`) or
  `foregroundApp()` returns null and every assignment is silently ignored —
  the SYS tab shows a banner and a deep link to the settings screen.
- The toy re-queries `UsageStatsManager.queryEvents` and the battery
  broadcast on the render tick; at a 90 ms marquee cadence that is ~11
  system-service IPCs a second and it stutters. Both are cached
  (`SIGNAL_TTL_MS`), and the foreground query is skipped entirely when no
  app is mapped.
- **`BATTERY_PROPERTY_CURRENT_NOW` is very noisy** on the Phone 3 — back to
  back 1 s samples swing by tens of mA, which made both the in-app POWER NOW
  readout and the Glyph Toy flicker.
- **Jitter here is fixed by quantizing the output, not by tuning a deadband.**
  The readout shows watts at 0.1 W precision and the noise is far below one
  displayed digit, so the last digit flipped on nearly every tick. A deadband
  in mA cannot express that threshold (it depends on terminal voltage and on
  the format string), and pushing it up just traded jitter for lag — alpha
  0.25 read as ~4 s of lag, alpha 0.6 read as jitter. `quantizeWatts()` snaps
  to the displayed precision; `CurrentSmoother` (EMA + deadband) only has to
  track the real signal. The Glyph Toy additionally dedupes frames by content
  key and refreshes on a 5 s tick — never pulse it, an on/off blink on a
  meter reads as flicker.

## Conventions

- Monochrome white/gray UI (`Theme.kt` tokens); no new red.
- Numbers: Geist Mono; labels: uppercase micro-labels.
- Missing data renders as `—`, never 0 or fake precision.
- mgmt: `TodoWrite` per multi-step task; compress context aggressively.
