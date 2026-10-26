# Shiv Phase 1 defect log

All diagnoses below were obtained from actual GitHub Actions build/test output.

| Defect | Observed failure | Fix | Validation |
| --- | --- | --- | --- |
| SHIV-D001 | MainActivity missing required model picker callback, Kotlin compilation failure, run 40219297196 | Wire OpenDocument launcher to PhaseOneController, commit `7dd3cfa` | Later APK compiles in run 40220199926 |
| SHIV-D002 | JVM property setter collision for speechLocale, run 40219610957 | Rename method to updateSpeechLocale, commit `b2395ab` | Later APK compiles in run 40220199926 |
| SHIV-D003 | Obsolete model-local capability status test, 17/18 JVM tests passed, run 40219861853 | Align assertion with real LiteRT runtime while keeping missing-integration tests, commit `748fc9f` | Unit suite and lint pass in run 40220199926 |
| SHIV-D004 | No Android security test for SQLCipher or denylisted inputs | Added Android 35 instrumentation suite, commit `7dd3cfa` | 4/4 tests passed in run 40220199919 |
| SHIV-D005 | Actual activity launch and recreation unverified | Added lifecycle instrumentation test, commit `713112f` | Passed 5/5 tests, run 40220610385 |

| SHIV-D006 | WorkManager signal queue handled 30 records then stopped, leaving pending encrypted signals | Continue scheduling remaining records; fail terminal attempts; add 37-signal regression | 14/14 on API26 and API35 in [40259210606](https://github.com/Navee569/Project-Shiv/actions/runs/40259210606) |
| SHIV-D007 | Detected commitments only produced ASK traces, with no owner-approval action | Reviewable event+reminder transaction, stable IDs, redacted audit log | API26/API35 event approval, reminder, idempotence and audit test passed in 40259210606 |
| SHIV-D008 | Corrected or deleted Room memory could remain in encrypted compatibility notes | Same SQLCipher transaction updates facts and vault index | API26/API35 note correction/deletion test passed in 40259210606 |
| SHIV-D009 | Clearing chat left extra records in Room and durable owner-input signal queue | Transactional erase of chat, vault history, owner signals and orphan traces | API26/API35 privacy-deletion test passed in 40259210606 |
| SHIV-D010 | Multi-GB model SHA-256 verification might block Compose UI | Rehash model on I/O dispatcher; verify recorded digest and size | APK/JVM/lint passed 40259210693; simulated file-integrity test passed API26/API35 |
| SHIV-D011 | Minimum API26 Android runtime and wrong-key/rollback/concurrency untested | Add two-version emulator matrix and additional SQLCipher tests | 14/14 each API26/API35, 40259210606 |

## Remaining high-value gaps, not falsely cleared

- Galaxy S23-specific offline model compatibility, GPU/NPU, memory/thermal, battery, Kannada/Telugu STT and TTS.
- Migration crash/restart tests with **real pre-upgrade encrypted preferences** and Keystore-loss recovery.
- Durable queue concurrency, active cancellation, retry-after-process-death, stress and overflow.
- Real microphone, notification access and Accessibility denial/secure-window coverage.
- API26 and API35 emulator checks pass; intervening Android versions and actual Samsung Galaxy S23 remain unverified.
- Authenticated integration E2E, cloud mock-server prompt leakage tests, permission-revocation behavior.
- Usability and accessibility audits of all Compose screens, device rotation beyond the smoke scenario.

Blocking critical defects must be resolved before Phase 1 M11 acceptance or release signing.
