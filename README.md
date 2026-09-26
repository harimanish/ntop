# ntop — btop for Nothing phones

A btop-style system monitor for Nothing (and CMF) phones. Four tabs —
live CPU/memory/network graphs, a per-process table, battery power +
health, and device info — in Nothing's monochrome dot-matrix aesthetic.
Graphs are drawn as **dot matrices** and readouts use Nothing's own
display face where the device ships it.

## Tabs

| Tab | What it shows |
| --- | --- |
| **MON** | CPU panel (frequency dot-graph + per-core dot meters), memory and network panels, hairline-divided |
| **PROC** | Per-process PSS table (via Shizuku), foreground-time ranking, or own processes |
| **BATT** | Live power (W/A/V), time-to-full, plus battery health % = current max ÷ design capacity |
| **SYS** | Device render matched to your model, chipset, ABI, storage |

## Notes & requirements

- CPU load is a **frequency proxy** (`time_in_state`): Android blocks
  `/proc/stat` for apps, so true CPU % is impossible without root. It is
  labeled FREQ, honestly.
- The full process table needs **[Shizuku](https://shizuku.rikka.app)**
  (wireless debugging, no root): install it, start it, open ntop's PROC tab
  and allow the prompt. Without Shizuku the tab falls back to usage ranking
  (needs Usage Access) or the app's own processes.
- Battery design capacity resolves from PowerProfile → a Nothing model map
  (Phone 3: 5150 mAh intl / 5500 mAh India) → manual override; the
  session learner refines current-max over charge cycles.
- **Dynamic Glyph Toy**: ntop ships a Glyph Toy that picks what to show on the
  Glyph Matrix (Phone 3 25×25, Phone 4a Pro 13×13; detected at runtime):
    - **always-on default** — an animated smiley (blinks, bobs, mouth opens
      and closes)
    - **your name** — the username marquee, if you prefer it
    - **while charging** — live charging watts over battery %
    - **per-app** — assign any installed app its own glyph from the SYS tab;
      it shows while that app is in front
  Enable it from the SYS tab → *Add to always-on Glyph*, or
  Settings → Special features → Gestures → Flip to Glyph → **Always-on Glyph
  Toy**. The toy has to be *selected* on that screen, not merely added.
  The physical Glyph button is what opens the interface — there is no adb
  keycode for it. On the lock screen the frame dims automatically; a long
  press on the Glyph button forces a fresh reading. The fuel-gauge current is
  EMA-smoothed and quantized, because raw `CURRENT_NOW` samples jitter enough
  to make the number flicker. Per-app assignment needs Usage Access — the SYS
  tab prompts for it.
  The waveform is **synthesised, not sampled**: Android's public SDK exposes
  no live audio session id on API 36, so `Visualizer` cannot capture another
  app's playback. It is driven by the real play/pause state, so it only moves
  while audio is actually playing.
- **Sideloaded installs (adb)**: Android 13+ gates sensitive access behind
  "Restricted setting — for your security, this setting is currently
  unavailable". If the Usage Access toggle is blocked, go to
  Settings → Apps → ntop → **⋮ menu → Allow restricted settings**,
  then grant it. This is an install-source policy, not a signature problem.

## Build

Requires Java 17 and the Android SDK (compileSdk 36):

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release (R8-optimized, ~2.7 MB). Signing is opt-in: create a git-ignored
`keystore.properties` in the project root, and the release build is signed
with it (without the file the build still succeeds, unsigned):

```properties
storeFile=/absolute/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

```sh
./gradlew :app:assembleRelease
```

## License

Personal project. Geist / Geist Mono fonts are OFL-1.1
(`OFL-Geist.txt`, `OFL-GeistMono.txt`). Device renders load from the
Nothing store CDN at runtime.
