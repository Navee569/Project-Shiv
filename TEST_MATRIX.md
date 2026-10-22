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


## Consolidated compile and runtime remediation — 2026-10-22 — pending exact-SHA CI

The candidate had a confirmed CameraX compile blocker: `CameraController.PREVIEW` is not a supported constant and the use-case expression had the wrong type. The invalid expression has been removed and the lifecycle controller is attached to `PreviewView`. Camera permission is rechecked on resume; camera binding is conditional on permission.

| ID | Requirement | Current candidate status |
|---|---|---|
| M0-COMP-01 | CameraX API compiles | PENDING exact-SHA build |
| M0-CAM-01 | Permission denial/grant/revocation state handling | Source updated; runtime NOT VERIFIED |
| M0-CAM-02 | Preview and capture on emulator/device | NOT VERIFIED |
| M0-CAM-03 | Real ML Kit image labeling result | NOT VERIFIED |
| M0-CAM-04 | Real asynchronous ImageProxy lifetime and exactly-once closure | Helper tests exist; end-to-end integration NOT VERIFIED |
| M0-APK-01 | Binary APK package, launcher, label, icon | PENDING exact-SHA manifest report |
| M0-EMU-01 | Clean install and cold launch | PENDING exact-SHA emulator report |
| M0-IDE-01 | Android Studio sync and run configuration | NOT VERIFIED |
| M0-ACCEPT | M0 acceptance | NOT ACCEPTED |

Do not promote any pending or not-verified status to PASS without exact-SHA logs or direct test evidence.

## Independent QA finding — 2026-10-22 — candidate SHA `e1cbd88051c62c09266040938066fbbd69965847`

The PR workflow [39715598035](https://github.com/Navee569/Project-Shiv/actions/runs/39715598035) **FAILED** on synthetic merge SHA `87cd4947429d65a49aadfabc9f93a437be336389` with compiler diagnostic `PhotoCapture.kt:60:49 'val' cannot be reassigned.`

| ID | Requirement | Status on failed candidate | Evidence / next action |
|---|---|---|---|
| M0-RETEST-01 | Exact source SHA build | FAIL | PR workflow 39715598035; CameraX preview controller assignment does not compile |
| M0-RETEST-02 | APK manifest inspection | NOT RUN | Build failed before APK inspection |
| M0-RETEST-03 | Emulator install and cold launch | NOT RUN | Build failure prevented smoke test |
| M0-RETEST-04 | Unit tests | NOT RUN | Build failure prevented test task |
| M0-RETEST-05 | Lint | NOT RUN | Build failure prevented lint task |
| M0-RETEST-06 | Artifact upload | FAIL / NO ARTIFACT | Upload step found no files; no artifact uploaded |
| M0-RETEST-07 | Android Studio sync/run | NOT VERIFIED | Static XML validation is not an IDE sync/run |
| CAM-RETEST-01 | Real permission/preview/capture/ML Kit/ImageProxy path | NOT VERIFIED | Requires suitable runtime/device evidence |

The remediation candidate fixes the preview-controller assignment and strengthens CI crash/ANR evidence and APK metadata reporting. All results for the new candidate remain **PENDING** until its exact-SHA workflows finish. Historical PASS rows above apply only to their explicitly named SHAs. M0 remains **NOT ACCEPTED**.

## Consolidated QA remediation — 2026-10-22 — previous candidate SHA `cc5f07d951e7675bb2904dca08948462b5155468`

Independent QA evidence for that candidate:
- Push run [39718207920](https://github.com/Navee569/Project-Shiv/actions/runs/39718207920): FAILED at emulator boot timeout.
- PR run [39718211150](https://github.com/Navee569/Project-Shiv/actions/runs/39718211150): FAILED at emulator boot timeout on synthetic merge SHA `32e69faa251c6d7d4e74c82e3a4443bbda4a8bdb`.
- Build and APK metadata inspection passed in both runs; app install/cold-launch script did not execute.
- Unit tests and lint were SKIPPED in both runs because the emulator step failed.
- Artifact upload passed. Push artifact ID `12400717002`, digest `sha256:929a4f4dbe5aa1404aff104b854ef18f2a236f1444e622f9ffed4f841482b7ce`; PR artifact ID `12400936649`, digest `sha256:60ad72aed4efdb8b271749c8ea9a5cba74f1f319852322b6f4ed29ba2aa1fd62`.
- Push APK SHA-256 `6e5f5129486cb47544de86447b444c0968c86f8974be153e4d6548226415a153`; PR APK SHA-256 `09c96c8b589944807782e41b2dcab3351ee1069248d683582016607bdaf068f2`. Both were 136026771 bytes.
- No unit-test count or lint count is asserted for this candidate because those tasks did not run.

| ID | Requirement | Previous candidate status | Consolidated remediation status |
|---|---|---|---|
| M0-EMU-BOOT | API 35 emulator boots | FAIL | PENDING fresh exact-SHA workflow |
| M0-EMU-SMOKE | Install and cold launch | NOT RUN | PENDING; strict gate retained |
| M0-TEST | Unit tests | SKIPPED | Workflow changed to run after successful build even if emulator fails; PENDING |
| M0-LINT | Lint | SKIPPED | Workflow changed to run after successful build even if emulator fails; PENDING |
| M0-ARTIFACT | Always-run artifact upload | PASS | Must reverify new SHA |
| CAM-LABEL-01 | Labeler cleanup on synchronous setup failure | Defect identified | New pipeline and regression tests; PENDING CI |
| CAM-LABEL-02 | Async success/failure and callback exactly once | Not adequately covered | New pipeline tests; PENDING CI |
| CAM-LABEL-03 | ImageProxy ownership until ML Kit processing completes | NOT VERIFIED end-to-end | Pipeline ordering tested; real integration remains NOT VERIFIED |
| DEV-IDE-01 | Android Studio sync/run | NOT VERIFIED | NOT VERIFIED |
| CAM-DEVICE-01 | Physical camera/real ML Kit/ImageProxy lifecycle | NOT VERIFIED | NOT VERIFIED |
| M0-ACCEPT | M0 acceptance | NOT ACCEPTED | NOT ACCEPTED pending fresh evidence |

The new remediation is not accepted based on source inspection alone. Recheck actual build, JUnit XML, lint reports, emulator diagnostics/smoke output, APK metadata, and artifact provenance for the new exact SHA.

## Independent QA retest — SHA 26331bae82806ad2ed8e2041a0b2884859d28847

| Criterion | Result |
|---|---|
| APK build and manifest assertions | PASS |
| Emulator boot | FAIL — userdata required 7372.80 MB; about 5619.80 MB available |
| Unit-test compilation / JUnit execution | FAIL — callback type mismatch; no JUnit XML |
| Lint | PASS — 0 errors, 26 warnings |
| Artifact upload | PASS |
| ML Kit listener-registration / image lifetime | HIGH risk remains on tested SHA |
| Android Studio / physical device / camera | NOT VERIFIED |
| M0 | NOT ACCEPTED |

The consolidated remediation candidate adds runner disk cleanup/preflight, corrects the test callback binding, hardens task ownership and terminal result selection, preserves cleanup exceptions, and adds regression tests. All results for the new HEAD are pending fresh exact-SHA CI.

## Independent QA retest — SHA 76433e45a3fa4f607f399f96c3a0e9042400fb8d

| Criterion | Result |
|---|---|
| Debug build and APK manifest assertions | PASS |
| Emulator disk cleanup and boot | PASS |
| Emulator smoke script | FAIL — Bash-only pipefail under /usr/bin/sh |
| Install/cold launch/process/activity/crash assertions | NOT RUN |
| Unit tests | PASS: 17/17, 0 failures/errors/skips |
| Lint | PASS: 0 errors, 26 warnings |
| Artifact provenance | PASS |
| Fallback waiter safe under interruption | FAIL / HIGH RISK on tested SHA |
| Adapter registration/fallback and concurrent race tests | MISSING |
| Current-SHA documentation | STALE after workflow completion |
| Overall M0 | NOT ACCEPTED |

Follow-up candidate: shell script uses POSIX `set -eu`; fallback waits through interruption until task terminality and restores the interrupt flag. Fresh exact-SHA workflows and further adapter/concurrency tests remain mandatory.


## Independent QA retest — 2026-10-22 — candidate SHA `f26c6900c2e15cc12b4feca3c0ae9af5c1a80f37`

Independent QA verdict: **NOT ACCEPTED**. Exact-source push run [39739443060](https://github.com/Navee569/Project-Shiv/actions/runs/39739443060) and PR run [39739449813](https://github.com/Navee569/Project-Shiv/actions/runs/39739449813) both failed during emulator smoke after the POSIX shell issue was corrected. The push run encountered an ADB broken pipe and `emulator-5554 offline`; the PR run hung at `adb install` until the 20-minute step timeout. Neither run verified APK installation or cold launch. Both runs passed build, 18 unit tests, and lint (0 errors, 26 warnings). Exact artifact and APK hashes are in the independent QA report; those results apply only to this SHA.

QA also found that the listener-registration fallback blocks synchronously in the CameraX main-executor path and can invoke `onFailure` twice if the callback itself throws. The follow-up candidate changes the fallback to a worker waiter and main-thread callback dispatch, adds bounded per-command ADB timeouts and device-state checks, and adds focused fallback tests. These changes are **PENDING exact-SHA CI**. Executor-rejection paths intentionally do not signal completion, to avoid premature image release; they require review because they retain ownership without completing the user flow. M0 remains **NOT ACCEPTED**.


---

## Exact-SHA QA matrix — 2026-10-22

Source SHA `8dec101f5f399a135d9b5bfed0ae84356e8b063b`; PR #1 merge SHA `ed6edc59ea716e2bdd558b042fa102df5c38af14`.

| Gate | Push 39773119551 | PR 39773127800 |
|---|---|---|
| Build debug APK / manifest | PASS | PASS |
| KVM device permission check | PASS; hardware acceleration enabled | PASS; hardware acceleration enabled |
| Emulator unique serial selection test (zero/one/multiple) | PASS | PASS |
| Online transport and boot property | PASS; `sys.boot_completed=1` | PASS; `sys.boot_completed=1` |
| APK install | PASS; about 20 s | PASS; about 20 s |
| Clear app data | PASS | PASS |
| Force-stop and clear logcat | PASS | PASS |
| Cold launch / `Status: ok` | PASS | PASS |
| PID and expected activity | PASS | PASS |
| Crash and ANR assertions | PASS; no match | PASS; no match |
| JUnit XML | 25 tests; 0 failures/errors/skips | 25 tests; 0 failures/errors/skips |
| Debug lint HTML | 26 warnings; 0 errors | 26 warnings; 0 errors |

The evidence demonstrates the KVM fix on the present runner and exact revision. Historical ADB-offline, broken-pipe, and workflow-cancellation records remain separately classified; a cancelled run is never counted as a pass.



## Latest independent QA matrix — 2026-10-22 — source SHA `04175279087fba63423fecafdbe311fe568f17bc`

This section supersedes older results when assessing the tested SHA. The remediation commit requires fresh push and PR evidence.

| Gate | Push run 39798977912 | PR run 39798987928 |
|---|---|---|
| Branch source/head SHA | `04175279087fba63423fecafdbe311fe568f17bc` | Source head same; workflow checked out synthetic merge SHA `98543eaa915ad2314003199f6e967352afe95822` |
| Debug APK assembly | PASS | PASS |
| APK manifest / launcher gate | PASS | PASS |
| Unit-test Kotlin source compilation | FAIL at `PhotoLabelingPipelineTest.kt:394` | FAIL |
| Unit tests executed | No — compile failed | No — compile failed |
| Debug lint | PASS, 0 errors / 26 warnings | Not confirmed as a pass |
| KVM validation | PASS | FAIL despite `ls -l` displaying mode 0666 |
| Device selection / shell syntax | PASS | SKIPPED downstream of KVM failure |
| Emulator install / cold launch | PASS | SKIPPED downstream of KVM failure |
| APK artifact provenance | PASS; push APK independently hashed | Separate PR synthetic-merge artifact; never substitute for push artifact |
| Overall M0 | NOT ACCEPTED | NOT ACCEPTED |

The test-source compile error must be fixed without weakening the real-thread interrupt-restoration assertion. KVM validation must prove actual access as the workflow user and fail closed if unavailable. Re-run both event types on the new source SHA before changing any current gate to PASS.


## Independent QA retest — 2026-10-22 — source SHA `0fe5aea72f9841eb5466c878b90ac8fbb601df93`

**Verdict: NOT ACCEPTED.** PR #1 remains OPEN, DRAFT, and UNMERGED. Source/head SHA: `0fe5aea72f9841eb5466c878b90ac8fbb601df93`; base SHA: `96e5dd78eedb8f12cf1e2aee758364284de03276`; PR synthetic merge SHA: `a7a41a66cd6153cb0ae1dbddd719a0016aad285f`.

- Push run [39806547809](https://github.com/Navee569/Project-Shiv/actions/runs/39806547809): **FAIL** at cold launch. `am start -W` returned shell exit 0 but `Status: timeout`, `LaunchState: UNKNOWN (-1)`, `WaitTime: 13230`; subsequent PID/activity/crash/ANR assertions did not execute.
- PR run [39806555738](https://github.com/Navee569/Project-Shiv/actions/runs/39806555738): **PASS** on synthetic merge SHA `a7a41a66cd6153cb0ae1dbddd719a0016aad285f`; launch status was `ok` and PID/activity/crash/ANR assertions passed.
- JUnit: `PhotoCaptureCompletionTest` 13/13; `PhotoLabelingPipelineTest` 21/21; total **34 passed, 0 failed, 0 errors, 0 skipped**.
- Lint: **0 errors, 26 warnings** — GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1.
- KVM read/write open, device-selection tests, and shell syntax checks passed on both event runs.
- Push artifact ID `12437409975`, ZIP size 73,364,550 bytes, digest `sha256:d313af96b2a448138f7d6dcc77c435b4267e0e6bfab2596e48fc79a45940c071`; recorded APK hash `f51b064073ad3dcd3168dec5e76d2ee6c920e6542fb7013820d551a89185bbcd`, size 136,043,155 bytes.
- PR artifact ID `12438303047`, ZIP size 73,454,505 bytes, digest `sha256:db3007faf06d16f29ff6dd1ba6e45bec915d60eda7bf7ba209d69068f54ad7a6`; recorded APK hash `a6607cf9d3846e8e105a345425fc7bed4f46b421d559fb2d4351f0163008528f`, size 136,043,155 bytes. PR evidence identifies the synthetic merge SHA.
- **APK byte-level verification NOT VERIFIED:** QA found neither artifact contained the APK binary, so recorded hashes could not be independently recomputed.
- Lifecycle coverage remained partial: adapter/fallback race, in-flight ownership, stale generation UI effects, dispatcher rejection recovery, eventual terminality, saturation diagnostics, callback-vs-task error classification, and raw-thread failure propagation needed more proof.

The next candidate moves WorkManager scheduling off the cold-start UI thread, collects live launch diagnostics before emulator teardown without accepting `Status: timeout`, publishes a dedicated exact-run APK artifact, adds production-used capture-generation guards, and adds deterministic pipeline/adapter race and ownership tests. These changes are **PENDING fresh exact-SHA CI and independent QA**. M0 remains **NOT ACCEPTED**.

## M0 retest evidence — 2026-10-22 — source SHA `e7cfd4dbee8e1c26fa844d8bd69e38faa0b5137a`

**Verdict: NOT ACCEPTED.** PR #1 remains OPEN, DRAFT, UNMERGED. Source/head SHA: `e7cfd4dbee8e1c26fa844d8bd69e38faa0b5137a`; base SHA: `96e5dd78eedb8f12cf1e2aee758364284de03276`; PR synthetic merge SHA: `6d7dfd6209d7c424d525a278df0b571167e9695c`.

- Push run [39810149309](https://github.com/Navee569/Project-Shiv/actions/runs/39810149309): **FAILURE** at “Validate emulator smoke shell syntax”. Error: `.github/scripts/emulator-smoke.sh: line 247: unexpected EOF while looking for matching `'``. Emulator smoke did not run; cold launch is **NOT VERIFIED**.
- PR run [39810160665](https://github.com/Navee569/Project-Shiv/actions/runs/39810160665): **FAILURE** at the same shell syntax step and same error. Emulator smoke did not run; cold launch is **NOT VERIFIED**.
- JUnit on each event: `PhotoCaptureCompletionTest` 15 passed; `PhotoLabelingPipelineTest` 24 passed; aggregate **39 passed, 0 failed, 0 errors, 0 skipped**.
- Lint on each event: **0 errors, 26 warnings** — GradleDependency 15; ObsoleteSdkInt 6; Recycle 2; InlinedApi 1; OldTargetApi 1; DataExtractionRules 1.
- Debug APK build, packaged manifest inspection, actual KVM read/write-open, and zero/multiple/single emulator device-selection checks passed on both events. Shell syntax failed before the smoke execution.
- Push APK artifact ID `12439687962`, name `shiv-debug-apk-push-39810149309-attempt-1`, ZIP size 72,878,162 bytes, ZIP SHA-256 `cb4eb2a20f5d3438b4ba68c8b50566915ed59cf4bc927d9754f567704936581b`; independently verified APK size 136,043,155 bytes and SHA-256 `109c04afa992c0a28d9678a87fc0fed6955f03c139f03f0b6f5623726fdf5a51`.
- PR APK artifact ID `12440651415`, name `shiv-debug-apk-pull_request-39810160665-attempt-1`, ZIP size 72,878,184 bytes, ZIP SHA-256 `601db4f93d3031f5f1bba7367bf9425c28719c1f40db08c4466a73b503112c03`; independently verified APK size 136,043,155 bytes and SHA-256 `130ba2ccfde31e18be8fb76ff69067fedec8a442190d59c5d2aa4940609a0bea`. PR artifact was built from synthetic merge SHA `6d7dfd6209d7c424d525a278df0b571167e9695c`, not the source/head SHA.
- Both APKs: applicationId `app.shiv`, versionCode `1`, versionName `0.1.0`, minSdk `26`, launcher `app.shiv.MainActivity`, label `Shiv`, application icon present. ZIP digest and APK digest are different measurements; the two APK hashes differ because event builds used different checked-out trees.
- Lifecycle unit-test evidence passed, but Android runtime cold-launch, PID/activity, crash-buffer, and ANR assertions were **SKIPPED** due to syntax failure. Preserve all prior evidence; this section does not mark the defect closed.
- Remediation required: rewrite the malformed launch gate and duplicate displaced statements; run all three `bash -n` checks; rerun both event workflows and require actual smoke execution. M0 remains **NOT ACCEPTED**.


## Independent M0 retest evidence — 2026-10-22 — source SHA `790c72555aa8b71b7b28f296eef5e9030788c238`

This dated section records the independent tester's completed retest. It supplements and does not delete or rewrite earlier historical entries.

- Source/head SHA: `790c72555aa8b71b7b28f296eef5e9030788c238` — https://github.com/Navee569/Project-Shiv/commit/790c72555aa8b71b7b28f296eef5e9030788c238
- PR #1: https://github.com/Navee569/Project-Shiv/pull/1 — OPEN, DRAFT, UNMERGED at retest.
- PR base SHA: `96e5dd78eedb8f12cf1e2aee758364284de03276`.
- PR synthetic merge/checked-out SHA: `fcc8b773f5a8e63e35ca4a647b7aaa59fa9d2543`; this is distinct from the source/head SHA.
- Push workflow [39812687992](https://github.com/Navee569/Project-Shiv/actions/runs/39812687992): SUCCESS, source SHA `790c72555aa8b71b7b28f296eef5e9030788c238`.
- Pull-request workflow [39812695904](https://github.com/Navee569/Project-Shiv/actions/runs/39812695904): SUCCESS, source SHA `790c72555aa8b71b7b28f296eef5e9030788c238`, checked-out synthetic merge SHA `fcc8b773f5a8e63e35ca4a647b7aaa59fa9d2543`.
- Both workflows executed the emulator install/cold-launch smoke; it was not skipped. Both completed all workflow jobs successfully.

### Shell, build, tests, and lint

- Shell syntax gate passed in both workflows: `bash -n .github/scripts/emulator-smoke.sh`, `bash -n .github/scripts/emulator-device-selection.sh`, and `bash -n .github/scripts/test-emulator-device-selection.sh`.
- Gradle tasks passed in both: `./gradlew --version`, `./gradlew projects`, `./gradlew :app:assembleDebug --stacktrace`, `./gradlew :app:testDebugUnitTest --stacktrace`, and `./gradlew :app:lintDebug --stacktrace`.
- JUnit, push and PR: `PhotoCaptureCompletionTest` 15 passed; `PhotoLabelingPipelineTest` 24 passed; aggregate 39 passed, 0 failures, 0 errors, 0 skipped.
- Lint, both workflows: 0 errors, 26 warnings — GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1. Warnings were not suppressed.

### Strict emulator smoke, both events

- Emulator boot completed (`sys.boot_completed=1`); KVM `/dev/kvm` was a character device and actual read/write open by the workflow user passed. No software-emulation fallback was used.
- Device-selection checks rejected zero and multiple online devices and selected the single online serial `emulator-5554`.
- APK install and `pm clear app.shiv` returned Success; force-stop and fresh logcat clearing succeeded.
- Cold launch required both exit code zero and the exact line `Status: ok`; `Status: timeout` is not accepted. Push launch: COLD, `app.shiv/.MainActivity`, TotalTime 11304 ms, WaitTime 11597 ms, PID 2952. PR launch: COLD, same activity, TotalTime 11621 ms, WaitTime 11650 ms, PID 2697.
- Both runs confirmed numeric PID, expected activity in activity and top-activity dumps, fresh full logcat inspection without matching fatal crash/ANR patterns, and an empty crash-buffer output. Smoke summaries were generated only after all success assertions passed.
- The failure diagnostics path is implemented, but it was not exercised because both launch attempts succeeded. This is source-path review, not a simulated-failure test.

### APK artifacts and independently verified provenance

**Push run 39812687992**
- APK artifact ID `12442115561`, name `shiv-debug-apk-push-39812687992-attempt-1`; ZIP size 72,878,162 bytes; ZIP SHA-256 `576ec281284c05dfa2f74e7dda135e617b9ca8af8e5e308b70fbe5a44120922a`.
- Extracted APK size 136,043,155 bytes; independently computed APK SHA-256 `aed3bb3981b67c1c1565b05b37d04bf3f050459eee07f06fa5808631688f6f57`. Evidence JSON/hash matched.
- Report artifact ID `12442135459`, ZIP size 73,525,726 bytes, ZIP SHA-256 `29ad8035cb1978f2707e1eeb9a187ff7145f2f76d8f5f14f11e23becb71c7d80`.

**PR run 39812695904**
- APK artifact ID `12441526542`, name `shiv-debug-apk-pull_request-39812695904-attempt-1`; ZIP size 72,878,183 bytes; ZIP SHA-256 `bb8a0b15771ceb2b5bf650c24a7c303c8f20c8ca9cddf748ca394ce918296967`.
- Extracted APK size 136,043,155 bytes; independently computed APK SHA-256 `e74160833816e115153b99615e8a35f103332b582f5a077e8f395ea96261b61c`. Evidence JSON/hash matched.
- Report artifact ID `12441027673`, ZIP size 73,535,417 bytes, ZIP SHA-256 `64cf995b6a5c19018221cd0222623c75d4ac95ef071fb9fe3b5387a019e6345c`.

Both APKs' metadata/manifest: applicationId `app.shiv`, versionCode `1`, versionName `0.1.0`, minSdk `26`, launcher `app.shiv.MainActivity`, label `Shiv`, icon present. ZIP hashes and APK hashes are different measures. Push and PR APK hashes differ because the PR built the synthetic merge tree.

### Decision and scope limits

The tester confirmed the previous shell syntax and emulator smoke blockers are fixed on this candidate, but found this documentation stale at `790c72555aa8b71b7b28f296eef5e9030788c238`. This section closes that stale-evidence finding for the tested candidate and supersedes prior open shell/smoke blocker statuses only with the exact evidence above; historical failures remain preserved. M0 is still **NOT ACCEPTED pending verification of the documentation-only commit's own push and PR workflows and independent tester confirmation**. This CI result does not demonstrate physical Samsung Galaxy S23 testing, Android Studio run-configuration validation, or broader product/security milestones.

### Matrix row additions for the tested source SHA

| Test / gate | Push run 39812687992 | PR run 39812695904 | Evidence status |
|---|---|---|---|
| Three shell syntax checks | PASS | PASS | Exact source SHA `790c72555aa8b71b7b28f296eef5e9030788c238`; PR checkout was synthetic merge SHA `fcc8b773f5a8e63e35ca4a647b7aaa59fa9d2543` |
| Gradle version / project discovery | PASS | PASS | Workflow logs |
| Debug APK assembly | PASS | PASS | Run-specific APK artifacts and independent hashes above |
| Unit tests | 39 pass, 0 fail/error/skip | 39 pass, 0 fail/error/skip | JUnit XML inspected |
| Debug lint | 0 errors, 26 warnings | 0 errors, 26 warnings | Lint reports inspected |
| KVM / device selection | PASS | PASS | Actual KVM open and zero/single/multiple-device tests |
| Install / clear / force-stop | PASS | PASS | Emulator smoke logs |
| Strict cold launch | PASS | PASS | COLD launch, zero exit, exact `Status: ok` |
| PID / activity / top activity | PASS | PASS | Numeric PID and expected activity in both dumps |
| Fresh crash buffer / ANR scan | PASS | PASS | No fatal crash or ANR patterns found |
| APK metadata / provenance | PASS | PASS | Exact-run artifacts, independent SHA-256 and manifest evidence |
| Physical Samsung Galaxy S23 | NOT RUN | NOT RUN | Outside this emulator CI evidence |

The previous unmatched-quote defect and skipped-smoke finding are closed for source SHA `790c72555aa8b71b7b28f296eef5e9030788c238`. The documentation commit's own CI remains pending until its new workflows finish. M0 remains **NOT ACCEPTED** pending fresh exact-SHA workflows and independent retest.
