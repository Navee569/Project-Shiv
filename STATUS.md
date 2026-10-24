# Project status

| Feature | Status | Verification | Last verified commit | Limitations | Open defects |
|---|---|---|---|---|---|
| M0 baseline build | BUILT AND TESTED (CI) | Tester confirmed M0 pass; final CI run 40045079241 passed assembleDebug, testDebugUnitTest and lintDebug | 4838d058f6c6ba3c7c703aa9308ccd5ab09879e4 | None. | None. |
| M0 worker delivery fixes | BUILT AND TESTED (CI) | Tester confirmed M0 pass; runs 40044537471 and 40045079241 passed build, 16 unit tests and lint | 4838d058f6c6ba3c7c703aa9308ccd5ab09879e4 | Uncertain dispatch retains a reservation; documented in DEFECT_LOG.md and accepted for M0. | None; tester closed M0-001 through M0-004. |
| M1 architecture foundation | PARTIAL | Full local `testDebugUnitTest` passed: 21 tests, including all domain-record repository round-trips and 13 worker regressions | Not yet CI-verified | Proactive persistence is behind `ProactiveRepository`. ViewModel persistence and speech/TTS capability ports are now defined. Broader layer separation, action execution, a public inference queue contract, integration, permission and diagnostics contracts remain. | M1 incomplete; no M1 acceptance claim. |

## M0 root cause

- .run/App.run.xml referenced stale Android Studio module Shiv.app; the app configuration now targets the imported Shiv.app.main module.
- Gradle settings include :app; the app plugin, app.shiv namespace/application ID, debug variant, and exported MAIN/LAUNCHER activity are valid.
- The configured LiteRT-LM 0.18.0 artifact contains Kotlin 2.4 metadata, but the project used Kotlin 2.0.21. The Kotlin and Compose plugins were aligned to 2.4.21.
- KSP 2.3 requires a newer Android Gradle Plugin than 8.7.3. The smallest compatible toolchain update is AGP 8.10.1 with Gradle 8.11.1 and KSP 2.3.9.
- Room 2.6.1 crashed in KSP with unexpected jvm signature V; the Room runtime, KTX, and compiler now use 2.8.5.
- ProactiveWorker.kt ended with non-source binary data. The worker was restored with quiet-hours, notification permission, daily-cap, duplicate, and model-decision checks.
- Compilation also exposed stale LiteRT message handling, a Kotlin interpolation typo, a missing chat-mode notification guard, and an incorrect CameraX import; these were corrected.
- Lint exposed missing Android 13 notification permission handling, a CameraX image opt-in, and a camera hardware feature declaration; these were corrected.
- The build was run with JDK 17. The shell default is JDK 8, so CI explicitly installs JDK 17.
- No local.properties, model weights, APK/AAB files, or signing keys are tracked by Git; ignore rules now cover local model and build artifacts.
- Three deterministic proactive-policy unit tests pass locally. The initial CI attempt stopped before Gradle because the Android setup action requested the removed `tools` package. The corrected workflow uses the Android SDK already present on the hosted runner; GitHub Actions run 40042340290 passed.
