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


## Independent QA evidence — 2026-10-22 — tested SHA 1f53acc

The independent report confirmed exact-SHA push run [39708040012](https://github.com/Navee569/Project-Shiv/actions/runs/39708040012) and PR run [39708044974](https://github.com/Navee569/Project-Shiv/actions/runs/39708044974) both passed build, unit tests, lint, and artifact upload. JUnit XML: 6 passed, 0 failed/errors/skipped. Lint: 0 errors, 27 warnings with categories unchanged. Artifact ZIP digests and APK metadata/checksums are recorded in STATUS.md.

These results apply only to SHA `1f53acc34887245c94b30fd9b4ae20032476ce56`. The next commit adds the missing normal success-first test, so CI must be rerun on that new SHA. Do not treat old evidence as current for new HEAD.

| ID | Requirement | Status after QA report |
|---|---|---|
| M0-01 | Gradle wrapper starts with supported JDK | PASS on SHA 1f53acc; re-run required for new HEAD |
| M0-02 | Android app module is recognised | PASS on SHA 1f53acc; re-run required for new HEAD |
| M0-03 | Debug APK builds | PASS on SHA 1f53acc; re-run required for new HEAD |
| M0-04 | Unit-test task executes | PASS: 6/6 on SHA 1f53acc; re-run required for new HEAD |
| M0-05 | Debug lint executes | PASS: 0 errors, 27 warnings on SHA 1f53acc; re-run required for new HEAD |
| M0-06 | Launcher activity resolves in packaged APK and launches | NOT VERIFIED |
| M0-07 | Success-first shared completion gate runs effects exactly once | Added to test suite; CI pending for new HEAD |
| M0-08 | Failure-first late success has no success side effects | PASS on SHA 1f53acc helper/shared-gate level; re-run required for new HEAD |
| M0-09 | Completion callback exception does not repeat cleanup/effects | PASS on SHA 1f53acc helper level; re-run required for new HEAD |
| DEV-IDE-01 | Android Studio sync/run configuration | NOT VERIFIED |
| DEV-01 | Physical device/emulator installs and cold-starts | BLOCKED / NOT VERIFIED |
| CAM-01 | Permission denial, preview, capture, ML Kit result and real ImageProxy lifetime | BLOCKED / NOT VERIFIED |

M0 remains NOT ACCEPTED. Passing CI and helper-level tests do not satisfy unperformed Android Studio, binary manifest, or device/runtime verification.


## Independent M0 retest evidence — 2026-10-22 — tested SHA `597be93da4b45e7a8caf780fb9628d85e307cc56`

The independent QA report confirms the exact branch head and records these results:

- Push run [39709266980](https://github.com/Navee569/Project-Shiv/actions/runs/39709266980): SUCCESS on exact commit `597be93da4b45e7a8caf780fb9628d85e307cc56`.
- PR run [39709271615](https://github.com/Navee569/Project-Shiv/actions/runs/39709271615): SUCCESS; head SHA matches, but checkout is synthetic merge SHA `8f742c27ce1890a492af9f6b89b0fead38d80f38`. This is merge-result validation, distinct from the exact-commit push run.
- Toolchain: Temurin/OpenJDK 17.0.20.1+1, Gradle 8.9, Android platform 35, Build Tools 35.0.0.
- Gradle project discovery, debug APK assembly, unit tests, lint, and artifact upload succeeded in both runs.
- Actual JUnit XML suite `app.shiv.PhotoCaptureCompletionTest`: 7/7 passed in both artifacts, 0 failures/errors/skips.
- Lint: 0 errors, 27 warnings (GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, MissingApplicationIcon 1); `UnsafeOptInUsageError` absent.
- Push artifact ID 12396373994; GitHub/independent ZIP SHA-256 match `99a1d2d94914d437acada936a11c2bc8deacca021faaed4e52a0608011eae41f`; APK SHA-256 `53ca69ad77fae89ecfbb24c8ea42ee22d6faf174b30609ba5f9a01a28475af53`.
- PR artifact ID 12396114520; GitHub/independent ZIP SHA-256 match `46b37713e571c446f5144f8f6d3070abc8969ed9d3dde374308d401c02f1bf7d`; APK SHA-256 `43270a96ecd6e4cb1e0459385b1869e937dd1637a681ce1f6f9a361cea59550f`.
- Both APK metadata files identify `app.shiv`, debug, versionCode 1, versionName 0.1.0, minSdk 26. APK hashes differ; no byte-identical reproducibility claim. Metadata is not binary-manifest evidence.

| ID | Requirement | Status on SHA 597be93 |
|---|---|---|
| M0-01 | Branch head/expected SHA | PASS |
| M0-02 | Push exact-commit workflow | PASS |
| M0-03 | PR merge-result workflow | PASS, synthetic merge distinction recorded |
| M0-04 | Gradle project discovery / :app | PASS |
| M0-05 | Debug APK assembly | PASS |
| M0-06 | Unit-test task and XML | PASS: 7/7 |
| M0-07 | Debug lint | PASS: 0 errors, 27 warnings |
| M0-08 | Success-first shared gate exactly once | PASS at helper/unit-test level |
| M0-09 | Failure-first late success rejected | PASS at helper/unit-test level |
| M0-10 | Completion exception / cleanup / error policy | PASS at helper/unit-test level |
| M0-11 | Artifact ZIP digest provenance | PASS |
| M0-12 | Packaged APK binary manifest / launcher | NOT VERIFIED |
| M0-13 | Android Studio sync/run configuration | NOT VERIFIED |
| DEV-01 | Device/emulator install and cold launch | BLOCKED / NOT VERIFIED |
| CAM-01 | Permission denial, preview, capture, real ML Kit result | BLOCKED / NOT VERIFIED |
| CAM-02 | Actual asynchronous ImageProxy lifetime and closure | NOT VERIFIED |
| M0-ACCEPT | M0 acceptance | NOT ACCEPTED |

The documentation records the evidence available for SHA `597be93...`; future commits require their own exact-SHA CI evidence. M0 remains NOT ACCEPTED until the binary-manifest, IDE, and runtime/device gates are completed and independently verified.


## Follow-up: automated APK manifest gate — 2026-10-22

The CI workflow now includes a post-assembly check using Android Build Tools `aapt dump badging` against `app-debug.apk`. It records the output and asserts the packaged application ID is `app.shiv` and the launchable activity is `app.shiv.MainActivity`; the report is included in the workflow artifact.

Status: **PENDING exact-SHA CI verification**. Do not mark the binary-manifest criterion PASS until the new workflow succeeds and its actual output is inspected. Even if this check passes, Android Studio run-configuration resolution, device/emulator installation and cold launch, permission denial, preview/capture, real ML Kit output, and actual asynchronous ImageProxy lifetime remain NOT VERIFIED/BLOCKED. M0 remains NOT ACCEPTED.


## Independent M0 retest — exact source SHA 8c860e197b0280c299ca890e1a0872bcf4f4fc0c — 2026-10-22

Evidence from the independent report:

- Exact-source push run [39712010450](https://github.com/Navee569/Project-Shiv/actions/runs/39712010450): SUCCESS.
- PR merge-result run [39712016130](https://github.com/Navee569/Project-Shiv/actions/runs/39712016130): SUCCESS on synthetic merge SHA `e21426dc26247226087a5273a9ef58dde75ed1ac`; source PR head remained `8c860e197b0280c299ca890e1a0872bcf4f4fc0c`.
- Build, project discovery, unit-test task, lint and artifact upload passed. JUnit XML: 7/7 passed. Lint: 0 errors, 27 warnings.
- Binary APK manifest gate: PASS; package `app.shiv`, versionCode 1, versionName `0.1.0`, launcher `app.shiv.MainActivity`.
- Push artifact ID `12397658751`, APK SHA-256 `4193e608c111026aeaa8727a8d7ee0a6a8dce86d0016ed98753beead5ad0e203`.
- PR artifact ID `12397003355`, APK SHA-256 `2d9bf52a3611a165aa2f6b9fbf530c441fead244f8e66fbe82c60f2d3e3b8f45`. The APK hashes differ; reproducibility is not asserted.

| Requirement | Result on SHA 8c860e1 |
|---|---|
| Gradle project discovery / app module | PASS |
| Debug APK assembly | PASS |
| Unit tests | PASS: 7/7 |
| Lint | PASS: 0 errors, 27 warnings |
| Binary APK package/launcher inspection | PASS |
| Artifact digest provenance | PASS |
| Android Studio sync and run configuration | NOT VERIFIED |
| Physical device install and cold launch | NOT VERIFIED |
| Camera permission denial, preview/capture, real ML Kit output | NOT VERIFIED |
| Real asynchronous ImageProxy lifetime/closure | NOT VERIFIED |
| M0 acceptance | NOT ACCEPTED |

The workflow change in this commit adds a clean-install emulator cold-launch smoke test and archives launch/activity/logcat evidence. Its result is pending on the new commit; do not infer PASS before inspecting that exact-SHA run. Emulator launch does not prove physical-camera or real camera callback correctness.


## Consolidated remediation — 2026-10-22 — pending exact-SHA CI

Source changes in the next revision explicitly enable both CameraX preview and image capture, request runtime camera permission before use, and add a vector launcher icon referenced by the manifest. CI now also statically validates the Android Studio run configuration XML and adds an emulator clean-install/cold-launch smoke test.

| Requirement | Status before next revision | Required new evidence |
|---|---|---|
| Launcher icon metadata | FAIL / lint warning | Exact-SHA lint plus packaged manifest inspection |
| Camera preview use case enabled | Source defect corrected; runtime NOT VERIFIED | Emulator/device preview evidence |
| Runtime camera permission | Source path added; denial UX NOT VERIFIED | Permission grant/deny UI test evidence |
| Android Studio run config | Static check added; IDE sync/run NOT VERIFIED | Open/sync/run using Android Studio |
| Emulator clean install and cold launch | CI smoke test added | Exact-SHA workflow logs and artifact |
| Physical-device camera and ML Kit | NOT VERIFIED | Actual device capture, ML Kit callback/result and logs |
| Real asynchronous ImageProxy closure | NOT VERIFIED | Instrumented callback/lifecycle test on device/emulator |
| M0 acceptance | NOT ACCEPTED | All mandatory criteria independently verified |
