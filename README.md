# ntop — btop for Nothing phones

A btop-style system monitor for Nothing (and CMF) phones. Four tabs —
live CPU/memory/network graphs, a per-process table, battery power +
health, and device info — in Nothing's monochrome dot-matrix aesthetic.

## Tabs

| Tab | What it shows |
| --- | --- |
| **MON** | CPU frequency proxy graph + per-core bars, RAM/SWAP history, network up/down |
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

## Build

Requires Java 17 and the Android SDK (compileSdk 36):

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release (R8-optimized, ~2.6 MB):

```sh
./gradlew :app:assembleRelease
```

## License

Personal project. Geist / Geist Mono fonts are OFL-1.1
(`OFL-Geist.txt`, `OFL-GeistMono.txt`). Device renders load from the
Nothing store CDN at runtime.
