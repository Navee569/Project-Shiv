# Shiv Phase 1 defect log

All diagnoses below were obtained from actual GitHub Actions build/test output.

| Defect | Observed failure | Fix | Validation |
| --- | --- | --- | --- |
| SHIV-D001 | MainActivity missing required model picker callback, Kotlin compilation failure, run 40219297196 | Wire OpenDocument launcher to PhaseOneController, commit `7dd3cfa` | Later APK compiles in run 40220199926 |
| SHIV-D002 | JVM property setter collision for speechLocale, run 40219610957 | Rename method to updateSpeechLocale, commit `b2395ab` | Later APK compiles in run 40220199926 |
| SHIV-D003 | Obsolete model-local capability status test, 17/18 JVM tests passed, run 40219861853 | Align assertion with real LiteRT runtime while keeping missing-integration tests, commit `748fc9f` | Unit suite and lint pass in run 40220199926 |
| SHIV-D004 | No Android security test for SQLCipher or denylisted inputs | Added Android 35 instrumentation suite, commit `7dd3cfa` | 4/4 tests passed in run 40220199919 |
| SHIV-D005 | Actual activity launch and recreation unverified | Added lifecycle instrumentation test, commit `713112f` | Passed 5/5 tests, run 40220610385 |

## Remaining high-value gaps, not falsely cleared

- Galaxy S23-specific offline model compatibility, GPU/NPU, memory/thermal, battery, Kannada/Telugu STT and TTS.
- Migration crash/restart tests with **real pre-upgrade encrypted preferences** and Keystore-loss recovery.
- Durable queue concurrency, active cancellation, retry-after-process-death, stress and overflow.
- Real microphone, notification access and Accessibility denial/secure-window coverage.
- Android API 26–35 emulator/device breadth; currently API 35 only.
- Authenticated integration E2E, cloud mock-server prompt leakage tests, permission-revocation behavior.
- Usability and accessibility audits of all Compose screens, device rotation beyond the smoke scenario.

Blocking critical defects must be resolved before Phase 1 M11 acceptance or release signing.
