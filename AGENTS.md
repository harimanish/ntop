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

## Conventions

- Monochrome white/gray UI (`Theme.kt` tokens); no new red.
- Numbers: Geist Mono; labels: uppercase micro-labels.
- Missing data renders as `—`, never 0 or fake precision.
- mgmt: `TodoWrite` per multi-step task; compress context aggressively.
