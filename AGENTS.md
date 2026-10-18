# AGENTS.md — ntop contributor context

`ntop` — btop-style system monitor for Nothing phones. Package
`com.ntop.app`, version 0.4.1. Tabs: MON / PROC / BATT / SYS (Compose
`HorizontalPager` + `FluentBottomBar`, Nothing monochrome theme, Geist
fonts, red accent only on charging pill/dot).

## Build & verify

- Toolchain: AGP 8.7.3, Kotlin 2.1.20, Gradle 8.11.1, Java 17,
  `compileSdk/targetSdk 36`, `minSdk 33`. No system gradle — use `./gradlew`.
  The Glyph Matrix SDK AAR (`app/libs/glyph-matrix-sdk-2.0.aar`) is
  gitignored: fresh worktrees/clones must copy it in before the first build.
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
- **There is no public API for a live audio session id on API 36** — verified
  in the stubs: `AudioPlaybackConfiguration` publishes only
  `getAudioAttributes()` (`getPlayState()`/`getAudioSessionId()` are
  `@SystemApi`), the public `MediaSession` stub has no `getSessionId()`, and
  hidden-API reflection is blocked at `targetSdk 36`. The one working route
  is the **session-0 output mix** (`Visualizer(0)`), which AudioFlinger gates
  on **RECORD_AUDIO**: with only `MODIFY_AUDIO_SETTINGS` the effect fails
  (`AudioFlinger ... status: -1` -> `Java error: -3`), so the GLYPH tab
  shows a `LIVE AUDIO OFF` banner + runtime-permission ALLOW. Without the
  grant (or where the effect cannot attach) the toy falls back to the
  synthetic bars, still gated on the real play/pause signal from
  `AudioManager.activePlaybackConfigurations` (no permission needed).
- WAVEFORM look is user-configurable: `GlyphWave(mirror, sensitivity,
  smoothing)` in the Glyph DataStore, applied identically by the toy service
  and the tab preview. Mirror = bass centre / treble edges (`mirrorSpectrum`
  resamples the band in a symmetric V), sensitivity scales bars
  (`applyGain`), smoothing is the per-tick fall factor passed to
  `AudioCapture.bars(count, release, gain)`. The editor opens by tapping the
  live preview in the hero card (Glyph Beat's gesture).
- **The preview is drawn to the Glyph Museum editor's measurements** (read
  off `com.pauwma.glyphmuseum`'s 25×25 editor by pixel analysis of
  screenshots): LEDs are identical rounded squares whether lit or off — one
  `side = cell * 0.80f` and `CornerRadius(side * 0.25f)` for both, only the
  alpha changes (`v / 255`, linear — the old 0.35..1.0 ramp lied). The panel
  is a **black disc** (user's call, over the museum's grey substrate) with a
  30 % rim, and unlit LEDs are drawn at 7 % white so off cells read as dim
  squares on black — the museum card's off state. The LED grid is inset
  **half a cell per side** (`field = s * (matrix - 1f) / matrix`, grid
  centred in the box) and cells are kept when their centre is within
  `matrix / 2f - 0.5f` cells of the middle: with the old "centre inside the
  disc" rule the rim touched the outermost pixels, which the user flagged.
  `hardware = false` (group icons) draws lit cells only.
- Group glyphs need **Usage Access** (`PACKAGE_USAGE_STATS`) or
  `foregroundApp()` returns null and IN USE matching is silently ignored —
  the GLYPH tab shows a banner and a deep link to the settings screen.
- Glyph groups are **user-built combos** (name + packages + mode + trigger),
  persisted as a JSON array string in the Glyph DataStore and resolved by the
  shared `GlyphSettings.resolveMode` (toy and tab preview run the same
  function, so the preview cannot lie): charging -> WATTS; PLAYING group
  while audio plays -> its mode; group owning the foreground app -> its mode;
  else defaultMode. A **PLAYING trigger cannot know which app is making
  sound** (no public playback attribution), so it matches on audio
  system-wide plus its own apps' foreground — the honest approximation for a
  music group. **`queryEvents` needs wall-clock epoch millis, not
  `SystemClock.elapsedRealtime()`** — the elapsed-clock window landed in
  January 1970, so every `foregroundApp()` query came back empty and
  foreground matching silently fell back to the default; the lookback is
  15 min so the newest RESUMED does not age out while you stay in one app.
  0.4.x per-app maps are migrated on read into one IN USE group per mode;
  a present-but-empty groups key is an explicit empty list.
- The toy re-queries `UsageStatsManager.queryEvents` and the battery
  broadcast on the render tick; at a 90 ms marquee cadence that is ~11
  system-service IPCs a second and it stutters. They are cached
  (`SIGNAL_TTL_MS`), and the foreground query is skipped entirely when no
  group lists any app. Audio state is cached the same way for resolution;
  the waveform animation still reads it per frame as before.
- **The 1 s sampler ran on the main thread and that, not rendering, was the
  cost.** `LaunchedEffect` uses the UI dispatcher, so every tick was doing
  `/proc/meminfo` (twice), 8 per-core `cpufreq` reads, `StatFs`, `TrafficStats`
  and 4 `BatteryManager.getIntProperty` binder calls on the main thread.
  Moving it to `Dispatchers.Default` recovered ~5 ms/frame on its own.
- **Never put `Regex("...")` inside a per-line lambda** — `readMeminfoKb` was
  compiling a fresh `Pattern` for every line of `/proc/meminfo`, twice a tick.
  Index-based parsing is ~free. Note the values are preceded by runs of spaces
  (`MemTotal:       15190456 kB`), so a naive `substringBefore(' ')` silently
  yields nothing and the UI shows `—`.
- **Nothing's own display face is a *system* font**, not a bundled asset:
  `/system/fonts/Ndot-55.otf`, family `"Ndot 55"`, PostScript `Ndot55All` — the
  same face the Glyph Matrix SDK uses. Resolve it with
  `Typeface.create("Ndot 55", NORMAL)` and fall back to Geist Mono off-Nothing,
  so there is no licence question and ntop stays installable anywhere.
- `HistoryBuffer.values()` must return a cached snapshot, not `buf.toList()`:
  every graph on a page calls it, and that was a boxed `List<Float>` allocated
  ~7x per recomposition.
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
- A scrolling marquee must wrap with `offsetPx.mod(stripWidth)` and draw the
  strip twice — a plain rising offset scrolls the text off the matrix and
  then blanks it.
- The hero photo is **drawn, not composed**: Coil's `SubcomposeAsyncImage`
  with `requiredSize` and `AsyncImagePainter` both failed (invisible draw /
  compile); what works is `context.imageLoader.execute(ImageRequest)` ->
  `(drawable as BitmapDrawable).bitmap`, drawn on a `Canvas` with
  `translate(-cropX, -cropY)` + `scale(canvasPx / bmp.width, pivot =
  Offset.Zero)`. Phone (3) disc geometry, measured off the 2000 px CDN
  render: centre at 62.35 % / 15.15 % of canvas width, disc d 11.75 %. One
  visual, not two: the render is scaled to a 906 dp canvas (the 544 dp
  baseline x 5/3, crop -408/-47, viewport 240x400 dp) and the **full
  preview — panel, LEDs and rim — is drawn directly on the photo's disc**
  (box = disc, no clip: the preview *is* the disc). Tapping the image opens
  the wave editor.

## Open items (next session)

- **None blocking.** The wave editor, the museum-matched LED preview
  (equal-size lit/unlit squares, black panel + rim, preview drawn on the
  hero render) and the RECORD_AUDIO
  banner flow are all device-verified (in-app ALLOW -> `granted=true`,
  AUDIO row back to `PLAYING · LIVE`); test residue (wav, "lolili" group,
  defaults, charging toggle, screen timeout) has been cleaned from the
  demo phone.
- **Physical Glyph matrix check** (only the user can do it): selecting a toy
  needs the physical Glyph button. Confirm the WAVEFORM look and mirror mode
  on the real LEDs.
- The editor was left at BASS · CENTRE / 2.96x / 59% smoothing on the demo
  device — reset in the editor if defaults are wanted.

## Conventions

- Monochrome white/gray UI (`Theme.kt` tokens); no new red.
- Numbers: Geist Mono; labels: uppercase micro-labels.
- Missing data renders as `—`, never 0 or fake precision.
- mgmt: `TodoWrite` per multi-step task; compress context aggressively.
