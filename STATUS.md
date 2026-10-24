# Project status

| Feature | Status | Verification | Last verified commit | Limitations | Open defects |
|---|---|---|---|---|---|
| M0 baseline and worker delivery | BUILT AND TESTED (CI) — accepted | M0 passed independent review; exact evidence in DEFECT_LOG.md | a2c632054b5d20f6c5e46256c24b1044ca1e7741 | Uncertain proactive delivery retains a reservation and may consume a daily slot; accepted for M0. | None; M0-001 through M0-004 closed. |
| M1 architecture foundation | BUILT AND TESTED (CI) — accepted | M1 passed independent review; M1-001 through M1-009 closed | a2c632054b5d20f6c5e46256c24b1044ca1e7741 | Later milestone work is separate. | None. |
| M2 Compose shell | BUILT AND TESTED (CI) — accepted | M2 passed independent review; M2-001 through M2-004 closed | a2c632054b5d20f6c5e46256c24b1044ca1e7741 | Later model, tasks, and runtime capabilities remain separate. | None. |
| M3 persistence foundation | PARTIAL — implementation delivered; independent review pending | Local assembleDebug, testDebugUnitTest (80 tests; 0 failures/errors/skips), lintDebug, and compileDebugAndroidTestKotlin passed. CI is configured to execute Android instrumentation for the real Keystore/SQLCipher open path and encrypted v5→v6 migration. | Base a2c632054b5d20f6c5e46256c24b1044ca1e7741 | Android instrumentation/device behavior is not claimed locally. | Exact-commit CI and independent M3 review pending. |

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
