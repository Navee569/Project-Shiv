# Project Shiv Test Matrix

Statuses: NOT RUN, PASS, FAIL, BLOCKED. A source declaration or successful compile is not a behavioural PASS.

| ID | Requirement | Test type | Current status | Required evidence |
|---|---|---|---|---|
| M0-01 | Gradle wrapper starts with supported JDK | Build/CI | PASS (SHA a942af9; re-run required for follow-up SHA) | Exact-SHA workflow logs: JDK 17, Gradle 8.9 |
| M0-02 | Android app module is recognised | Build/CI | PASS (SHA a942af9; re-run required for follow-up SHA) | Exact-SHA workflow Gradle project discovery and app tasks |
| M0-03 | Debug APK builds | Build/CI | PASS (SHA a942af9; re-run required for follow-up SHA) | Exact-SHA push/PR runs 39654587925 and 39654596592; APK artifact inspected |
| M0-04 | Unit-test task executes | Build/CI | PASS (SHA a942af9; re-run required for follow-up SHA) | JUnit XML: 5 executed, 5 passed, 0 failed/errors/skipped |
| M0-05 | Debug lint executes | Build/CI | PASS (SHA a942af9; re-run required for follow-up SHA) | Lint XML/TXT: 0 errors, 27 warnings; UnsafeOptInUsageError absent |
| M0-06 | Launcher activity resolves | Manifest/device | NOT VERIFIED | Binary APK manifest/launcher decode and launch smoke test still required |
| DB-01 | Encrypted database creates and reopens | Instrumentation | NOT RUN | Test output and encryption verification |
| DB-02 | Incorrect/lost key fails safely | Instrumentation | NOT RUN | Regression test evidence |
| DB-03 | Memory/event writes persist across restart | Instrumentation | NOT RUN | Persistence test evidence |
| AI-01 | Compatible local model loads | Device integration | NOT RUN | Model/runtime/version details |
| AI-02 | Real inference succeeds in airplane mode | Device integration | NOT RUN | Device, model, network state, output and timing |
| AI-03 | Invalid structured output is rejected | Unit | NOT RUN | Deterministic test report |
| ACT-01 | Action tiers and confirmation rules are enforced | Unit/integration | NOT RUN | Allow/deny matrix and test report |
| PRIV-01 | No hidden cloud AI fallback | Security/device | NOT RUN | Dependency/network inspection and offline test |
| DEV-01 | App cold-starts on Samsung Galaxy S23 | Device | BLOCKED | Device run, Android version, logcat |
| DEV-02 | Process restart preserves required state | Device | BLOCKED | Device test evidence |

Update each status only when the corresponding evidence exists. Add cases as milestones introduce behaviour.

## M0 verification evidence update — 2026-10-22

The PASS statuses above refer specifically to source SHA `a942af9613f7561d671de5aba9b5cfdbd717d522` and exact-SHA push/PR workflows 39654587925 / 39654596592. A follow-up source revision changes the production callback gate and expands unit tests; re-run and inspect all CI checks against that new SHA before treating these statuses as current for HEAD.

The tested SHA's five tests all passed. Lint had 0 errors and 27 warnings (GradleDependency 15; ObsoleteSdkInt 6; Recycle 2; InlinedApi 1; OldTargetApi 1; DataExtractionRules 1; MissingApplicationIcon 1). Artifact provenance and APK metadata/checksums are recorded in STATUS.md's dated retest section.

Still NOT VERIFIED/BLOCKED: binary launcher resolution, Android Studio run configuration, physical device/emulator installation and launch, permission denial, camera preview/capture, ML Kit description, and real ImageProxy lifetime. The previous late-callback test had a test-quality gap; the follow-up revision adds a shared production success gate, but its tests and CI must pass before claiming the gap closed. M0 remains NOT ACCEPTED.
