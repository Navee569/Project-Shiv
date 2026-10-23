# Project Shiv Test Matrix

## Current remediation matrix — product source 914f5eaad5aceafa8f4c68840259d0386bd1af2e

Observed 2026-10-23. M1 remains NOT ACCEPTED / BLOCKED. This section reports the product revision above, not future checks of its documentation follow-up.

| Check | Result | Direct evidence / limitation |
|---|---|---|
| Full JVM suite | PASS | Valid XML: 80 tests, 0 failures, 0 errors, 0 skipped; seven suites |
| Real JSON runtime | PASS | BackupStreamsTest system-out identifies JSONObject and JSONArray in org.json:json:20240303 JAR |
| Debug app assembly | PASS | Local consolidated Gradle result; app.shiv / MainActivity verified with aapt |
| Android restore-test APK assembly | PASS | Six Android tests compile; execution remains BLOCKED |
| Debug lint | PASS with warnings | 0 errors, 27 warnings: GradleDependency 17, ObsoleteSdkInt 7, DataExtractionRules 1, InlinedApi 1, Recycle 1 |
| Envelope/settings validation | PASS (JVM) | Fractions, overflow, wrong types, singleton settings id, missing arrays, historical null profile envelope |
| Full URI restore path: malformed collection records/settings, corrupt/truncated/wrong password input | BLOCKED | BackupRestoreTest implemented and compiled, no running Android device |
| Real Room rollback after writes begin | BLOCKED | SQLite BEFORE INSERT abort trigger on final settings insertion; test asserts exact pre-restore contents across all ten tables, not just helper output |
| Historical profile:null restore | BLOCKED | Direct production restore test compiled; no Android execution |
| URI destination open/write failures | BLOCKED | Direct export tests compiled, test-only provider rejects writes; verifies partial-output cleanup/passphrase clearing when executable |
| Four-guest-CPU proof | BLOCKED | Script asserts requested/configured 4, one canonical config, mask 0-3, online count 4, processor IDs 0-3; hosted jobs did not start |
| Hosted push 39999347493 | FAIL | Terminal billing/spending-limit admission failure; no execution, no artifacts |
| Hosted PR 39999354009 | FAIL | Both jobs terminal billing/spending-limit admission failures; no execution, no artifacts |
| Source/checkout/target equality and synthetic merge provenance | BLOCKED | Assertions configured; no hosted checkout ran at this source |
| Offline-only/no outbound network | NOT VERIFIED | APK still contains transitive INTERNET/ACCESS_NETWORK_STATE permissions; no network capture |
| Keystore key loss/corruption and safe recovery | NOT VERIFIED | No new device evidence |
| Production action-tier/permission-denial negatives | NOT VERIFIED | Existing helper tests do not establish production enforcement |
| Deterministic ADB-offline fault and recovery | NOT VERIFIED | Historical real failure preserved; no new fault-injection run |
| Physical Galaxy S23 | BLOCKED | adb devices returned none |
| Comprehensive security scenarios/mitigations/retest | NOT VERIFIED | Mandatory acceptance gate remains open |

Local consolidated command: `gradlew.bat --no-daemon --max-workers=2 "-Pkotlin.compiler.execution.strategy=in-process" :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:printDebugTestRuntime --console=plain --stacktrace`. Result BUILD SUCCESSFUL. Local snapshot normalized trailing blank lines at staging; hosted exact-source verification remains mandatory. Local emulator cannot boot because the hypervisor driver is missing.

No GitHub archives exist for either fresh run: archive SHA-256/digest comparison, artifact IDs, hosted APK hash, cold-launch wait/state, queue repeats, logcat/crash exits, and effective guest CPU evidence are unavailable. Local APK SHA-256 is 2c7e5f67d45353aa8260f34d4c2197b4ac1a4bf1aeb9ac1c3aba7ebdb7b868d0 (136075923 bytes); do not present it as a hosted artifact hash.

History remains: 6880cd1d PR 39972768372 / push 39972762765 failed before execution (billing annotation now recovered); older 35bfc7bb PR 39961937452 / push 39961928171 failed; synthetic merge b3ac9bcee7c59db5903b644493ebc964154551ec stays separate; 39936972682 / job 120359223524 mandatory logcat failed with exit 255 during ADB offline, not timeout. No historical failure was erased or rerun.


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


| M1-COMP-DAO-001 | Public Room DAO return type visibility | Kotlin/Room KSP build | FIX COMMITTED; exact-SHA verification pending | `ConfirmedPendingEvent` made public in commit `67a9085eb2af7ae210fe9d4cce6889749f92ba49`; do not mark PASS until compile/build succeeds on final SHA |
| M1-QUEUE-01 | Prioritized inference queue concurrency and ordering | JVM unit tests | NOT VERIFIED on current fix SHA | Queue tests were not run on `9c29f21738c2c0ddd0ae747bd408da6890d9a284` because compile failed; run queue-only tests first, then full suite on a later exact SHA |
| M1-ROOM-01 | Atomic pending-event confirmation / duplicate broadcasts / rollback | Room/instrumentation | NOT RUN | Compile blocker prevented runtime tests; test valid, expired, missing, repeated/concurrent confirmation and transaction rollback |
| M1-BACKUP-01 | Encrypted backup/restore negative cases and rollback | JVM + Android integration | NOT RUN | Wrong passphrase, truncation, malformed input, size cap, URI write failure and database rollback still require executed evidence |
| M1-KEY-01 | Android Keystore loss/corruption and startup data preservation | Android integration | NOT RUN | JVM state tests do not prove real Android Keystore behavior |
| M1-ACTION-01 | Production action-tier/notification/permission paths | JVM + Android integration | NOT RUN | Helper tests alone do not prove production entry points |
| M1-APK-01 | APK provenance / manifest / checksum | Build artifact inspection | NOT RUN on fix SHA | Must tie APK metadata and SHA-256 to exact source SHA and successful workflow |
| M1-EMU-01 | Strict emulator cold-launch / crash / ANR smoke | Emulator CI | NOT RUN on failed SHA; pending on fix SHA | A skipped or blocked emulator task is not PASS |


## M1 exact-SHA retest evidence — 2026-10-23

| Gate | Push run 39900500053 | Exact-source PR run 39900503027 | Evidence / limitation |
|---|---|---|---|
| Source SHA | PASS | PASS | `1629273b0ce29b57118828e295225b5b98929cb8`; PR exact-source checkout assertion matched |
| Kotlin / Room KSP compile and APK assembly | PASS | PASS | Prior public DAO return-type visibility compile blocker fixed |
| Queue-only test task | PASS | PASS | 10 tests passed in each required workflow; ten separate repeated runs were not yet available |
| Full JUnit suite | PASS | PASS | 72 tests, 0 failures, 0 errors, 0 skipped in each workflow |
| Lint | PASS | PASS | 0 errors, 26 warnings |
| APK manifest / provenance | PASS | PASS | app.shiv, version 1 / 0.1.0, minSdk 26, launcher app.shiv.MainActivity, label Shiv, icon present |
| Hosted emulator smoke | PASS | PASS | Install, clear-data, cold launch Status: ok, PID/activity, crash and ANR assertions |
| Android Keystore key loss/corruption integration | NOT RUN | NOT RUN | JVM key-state tests do not prove platform Keystore behavior |
| Room confirmation concurrency / duplicate / rollback integration | NOT RUN | NOT RUN | Transaction integration/failure injection remains required |
| Android backup URI write failure / restore rollback | NOT RUN | NOT RUN | Streaming helper tests do not prove ContentResolver or DB rollback behavior |
| Production action-tier and permission entry points | PARTIAL | PARTIAL | Helper tests pass; end-to-end production entry points remain unverified |
| Physical Samsung Galaxy S23 | NOT RUN | NOT RUN | Hosted emulator is not a physical-device test |
| Offline network capture / full security testing | NOT RUN | NOT RUN | No network-capture or penetration-test evidence |

### Exact artifact evidence
- Push APK artifact 12479047636: 136,059,539 bytes; SHA-256 `eee7d067809890098f6105832c3e77484e12b2549c7673e2418f25d1855c3db0`.
- Exact-source PR APK artifact 12478853527: 136,059,539 bytes; SHA-256 `f14690137250c66d4e3957e10ceabbec210111121957f1982690b93a5c3c1d18`.
- Push report artifact 12478888490; PR report artifact 12479395583.
- Lint warning categories: GradleDependency 15, ObsoleteSdkInt 7, Recycle 1, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1.
- Both same-SHA APK artifacts had matching size but different hashes. Reproducible-build status remains unconfirmed.

**Decision:** M1 remains BLOCKED / NOT ACCEPTED pending ten separately recorded queue-only runs, accurate documentation on the final commit, and explicit closure or project-owner scoping of remaining Android/data-integrity coverage.


## M1 evidence retention follow-up — 2026-10-23

| Gate | Prior candidate result | Evidence / limitation | Current follow-up |
|---|---|---|---|
| Push workflow on SHA `a0a837dda149b8b75f1aabdaa189557f5f455c1b` | PASS | Run 39902332198, job 120246476726 | Fresh CI required for this follow-up commit |
| Exact-source PR workflow on prior SHA | PASS | Run 39902335066, exact-source job 120246486875; checkout assertion matched source SHA | Fresh CI required for this follow-up commit |
| Full unit tests | PASS | 72 tests, 0 failures/errors/skips in each required job; class counts recorded in STATUS.md | Recheck both current-SHA artifacts |
| Lint | PASS | 0 errors, 26 warnings: GradleDependency 15; ObsoleteSdkInt 7; Recycle 1; InlinedApi 1; OldTargetApi 1; DataExtractionRules 1 | Recheck both current-SHA artifacts |
| Repeated queue tests — push | PASS | 10/10 exit code 0; all ten logs individually inspected | Recheck new SHA |
| Repeated queue tests — exact-source PR | EXECUTION PASS / LOG INSPECTION BLOCKED | Ten exit codes were zero in action log, but artifact 12480145944 omitted run logs and summary; synthetic-merge artifact is not a substitute | Workflow now explicitly uploads `app/build/reports/repeated-queue-tests/**`; inspect exact-source summary and all ten logs on new SHA |
| APK provenance and hosted emulator smoke | PASS on prior SHA | APK artifacts 12479523710 and 12480145944; both hosted smoke gates passed | Recheck current-SHA artifacts |
| Keystore / Room rollback / backup URI failures / production action tiers / offline network capture / physical device / full security assessment | NOT RUN / UNVERIFIED | Existing JVM tests and hosted emulator smoke do not establish these behaviors | Keep blocked unless platform-level evidence is produced or project owner explicitly scopes acceptance |

**Current commit disposition:** verification pending. Do not mark M1 accepted until fresh push and exact-source PR runs pass, exact-source artifact retention is verified, documentation is current, and remaining project-owner Android/data-integrity acceptance requirements are satisfied or explicitly scoped.


## M1 exact-source retest — 2026-10-23 — source SHA `966aa26ab63bf72b15848496db5b3930e377670e`

Workflow run [39923689666](https://github.com/Navee569/Project-Shiv/actions/runs/39923689666) completed with both jobs successful. The exact-source job 120315249653 checked out and asserted `966aa26ab63bf72b15848496db5b3930e377670e`; the regular job 120315249339 used merge SHA `79a4b7623b67f1ca0acb09887e0812d1fd3b240f`, so its queue results are not substitutes for exact-head evidence.

| ID | Requirement | Status for source SHA `966aa26ab63bf72b15848496db5b3930e377670e` | Evidence / limitation |
|---|---|---|---|
| M1-CI-01 | Exact-source build | PASS | Exact-source job 120315249653 succeeded; checkout assertion matched source SHA. |
| M1-QUEUE-01 | Ten exact-source queue repetitions retained and valid | PASS | Artifact 12488143989; summary + ten logs + ten per-run JUnit XML files inspected. Each run: exit 0, 10 tests, 0 failures/errors/skips, task_executed=1, junit_valid=1. |
| M1-UNIT-01 | Full unit-test suite | PASS | 72 tests, 0 failures, 0 errors, 0 skipped in run 39923689666. |
| M1-LINT-01 | Debug lint | PASS | 0 errors, 26 warnings; warnings remain recorded, not suppressed. |
| M1-EMU-01 | Hosted emulator smoke and crash/ANR checks | PASS | Exact-source hosted emulator checks succeeded; does not establish physical-device behavior. |
| M1-APK-01 | Exact-source APK provenance | PASS | Exact-source APK 136,059,539 bytes; SHA-256 `5eba4bbb68e164e9ab06de98d3a4627efaa10f953743029e5508081056630278`. |
| M1-REGULAR-01 | Regular CI queue results are exact-head evidence | FAIL / NOT APPLICABLE | Regular job checked out merge SHA `79a4b7623b67f1ca0acb09887e0812d1fd3b240f`; keep separate from exact-source evidence. |
| M1-DOC-01 | Three documents current for run 39923689666 | REMEDIATION COMMITTED; fresh verification pending | This addendum is being added; new documentation commit requires fresh workflows and review. |
| M1-DATA-01 | Keystore fault injection | NOT VERIFIED | No direct fault-injection evidence. |
| M1-DATA-02 | Room transaction/rollback integration | NOT VERIFIED | JVM tests/compilation are not Android database integration evidence. |
| M1-BACKUP-01 | Backup URI write failure and restore rollback | NOT VERIFIED | No direct integration evidence. |
| M1-ACTION-01 | Production action tiers and permission denial | NOT VERIFIED | No production-entry-point end-to-end evidence. |
| M1-OFFLINE-01 | Offline network capture / no cloud fallback | NOT VERIFIED | No suitable network capture evidence. |
| M1-DEVICE-01 | Physical Samsung Galaxy S23 | NOT RUN / NOT VERIFIED | Hosted emulator is not a physical device. |
| M1-SEC-01 | Comprehensive security assessment | NOT VERIFIED | No complete assessment evidence. |
| M1-ACCEPT | M1 acceptance | BLOCKED / NOT ACCEPTED | Required platform/data/security gates remain unverified; PR #1 must remain open, draft, unmerged. |

The report archive was recorded as artifact 12489255574; exact-source APK artifact 12488143989. Artifact digests are listed in STATUS.md and must be independently recomputed from downloaded bytes before marking archive integrity PASS.


## Independent retest matrix — 2026-10-23 — candidate SHA `9ef8f73be7a522c62cd7623da299d4151ce2e7e3`

| Gate | Push run 39932801082 | PR run 39932808366 | Evidence / interpretation |
|---|---|---|---|
| Build / APK manifest | PASS | PASS in both jobs | PR exact-source job 120344972366; regular merge-result job 120344972695 also built and inspected APK |
| Exact-source SHA assertion | Not applicable; push event | PASS | Exact-source job asserts source and checkout SHA both equal `9ef8f73be7a522c62cd7623da299d4151ce2e7e3` |
| Queue tests, 10 repetitions | PASS | PASS in exact-source and regular jobs | Exact-source artifact 12493092228 retains ten logs and ten per-run JUnit XMLs; each run has 10 tests and zero failures/errors/skips |
| Full unit suite | PASS, 72/72 | PASS, 72/72 in both jobs | Zero failures, errors, or skipped tests |
| Lint | PASS | PASS in both jobs | 0 errors, 26 warnings: GradleDependency 15, ObsoleteSdkInt 7, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, Recycle 1 |
| Hosted emulator strict cold launch | PASS | Exact-source PASS; regular merge-result FAIL | Exact-source: Status: ok, LaunchState COLD, WaitTime 8441 ms. Merge result: Status: timeout, LaunchState UNKNOWN, WaitTime 13738 ms. Strict assertion correctly failed. |
| APK provenance / manifest | PASS | Exact-source PASS | Exact-source APK 136,059,539 bytes, SHA-256 `9e0916efe83941af90eeb3a3dd8fac561d026904b2999690789bb2ef3d6a01c8`; app.shiv, version 0.1.0, minSdk 26, targetSdk 35, MainActivity launcher, label/icon present |
| Documentation freshness for candidate SHA | Historical evidence only | Historical evidence only | This addendum is followed by workflow/docs commits; fresh workflows for the final documentation commit are mandatory |
| Keystore / Room rollback / backup URI failures / action-tier end-to-end / offline network capture / physical Galaxy S23 / full security assessment | NOT VERIFIED | NOT VERIFIED | Keep as explicit acceptance blockers |

### Emulator failure diagnosis and workflow change

The regular merge-result job's retained diagnostics show app PID 2601 and MainActivity RESUMED, but `reportedDrawn=false` and `firstWindowDrawn=false`; the crash buffer is empty. Logcat indicates skipped frames and heavy emulator/system activity; host memory showed about 3.0 GiB available with 2.2 GiB swap in use. Emulator output warned that four CPU cores would run the AVD more smoothly. Exact-source strict launch passed on the same source candidate. Root cause is not conclusively proven; resource contention/startup timing is plausible.

Workflow commit `07a2e5ff1a5123b87d473c99c39069efe6f0db79` raises emulator cores from 2 to 4 in both jobs. The strict `Status: ok` assertion and failure diagnostics remain unchanged. Verify fresh push and PR runs on the final docs commit; a successful exact-source job alone does not make the PR workflow green if the regular merge-result job fails.

**Decision: M1 BLOCKED / NOT ACCEPTED.** Preserve the failure and keep PR #1 open, draft, and unmerged.


## Retest matrix — 2026-10-23 — source SHA `9ca24c1a2375fc5d7418e944f9a6a0d1f5a5269e`

| Gate | Result | Evidence / limitation |
|---|---|---|
| Push workflow | PASS | Run 39936964872, push event, main job 120359239132 success. Exact-source PR job is skipped on push events. |
| Exact-source PR job | PASS | PR run 39936972682, job 120359223934; checked-out SHA equals source SHA `9ca24c1a2375fc5d7418e944f9a6a0d1f5a5269e`. |
| Regular PR merge-result job | FAIL | Job 120359223524 checked out synthetic merge SHA `0714eb753b06caac376b94d4e9f73a36329ff101`; smoke step exited 255 after strict launch passed. |
| Strict cold launch — exact source | PASS | Status: ok; LaunchState: COLD; WaitTime 12757 ms. |
| Strict cold launch — regular merge result | PASS | Status: ok; LaunchState: COLD; WaitTime 10082 ms. This does not override the later smoke failure. |
| Post-launch smoke / log capture — regular merge result | FAIL | `adb -s emulator-5554 logcat -b all -d -v threadtime -t 6000` exited 255. Failure artifact boot diagnostics recorded emulator-5554 as offline transport_id 2. |
| Effective emulator core configuration | NOT VERIFIED on prior run | Workflow input and action setup output both say cores 4, but emulator warning says currently using 2. Prior artifact lacks AVD config.ini. Workflow now records `hw.cpu.ncore` on failure. |
| APK provenance | PASS for candidate SHA | Exact-source APK 136,059,539 bytes; SHA-256 `c5c36c9808478ccad045f1e3d50866c5296dd3e23a368c78374589abacb06ff9`. Push APK hash `c0920d63266555cd6b5c322bfea913a03d230e97278fcf033befbcfed648def1`; different build jobs/source context means hash difference alone is not a defect. |
| Full unit tests | PASS for exact-source candidate | 72 tests; 0 failures/errors/skips across seven JUnit XML files. |
| Repeated queue tests | PASS for exact-source candidate | Ten logs and ten matching JUnit XMLs; each: 10 tests, 0 failures/errors/skips, exit 0, task_executed=1, junit_valid=1. |
| Lint | PASS for exact-source candidate | 0 errors, 26 warnings: GradleDependency 15; ObsoleteSdkInt 7; InlinedApi 1; OldTargetApi 1; DataExtractionRules 1; Recycle 1. |
| Documentation freshness | FAIL for source SHA above | Tester confirmed the three documents did not record these run IDs/outcomes. Addendum committed after this candidate; final-SHA workflows still required. |
| Data integrity, backup/restore, production permissions, offline network behavior, physical Galaxy S23, full security assessment | NOT VERIFIED | Mandatory platform/security evidence absent; retain acceptance blockers. |

### Failure diagnosis and remediation commits

Report artifact 12495271137 SHA-256 `857882b62554bcfd863a4c18a3f9f7385b1c87fb7e7f18fbb693a357475a2507` was independently downloaded and verified. It records the ADB device as offline during failure diagnostics. The actual failed command was the full logcat capture, exit 255; the string `timeout=20s` in the old status file did not establish a timeout. Commit `86a9cd59c99e9c75fcbfb428ac61f75319a39c2e` moves mandatory logcat/crash-buffer collection ahead of larger dumpsys calls and makes failure status reporting distinguish exit 255 from timeout. Commit `b0c6ffa275c3d9a950dc445b7e69bb5dd88b8167` adds the effective AVD CPU configuration to failure diagnostics.

The action input is `cores: 4` and action setup reports `Cores: 4`, while emulator startup emitted a warning that the AVD currently uses 2 cores. This discrepancy remains unresolved until the effective AVD config is inspected from fresh-run evidence.

**Decision: M1 BLOCKED / NOT ACCEPTED.** These changes are unverified until fresh push and PR workflows on the final docs commit complete. Keep PR #1 OPEN, DRAFT, UNMERGED.


## Independent retest update — 2026-10-23 — exact source SHA `7ed3514d8dae780ba4cbdd6ca8a87ba222dc4c6f`

This dated addendum records the latest independent tester evidence. It does not replace or erase any earlier failure or result. **M1 remains BLOCKED / NOT ACCEPTED; PR #1 remains OPEN, DRAFT, UNMERGED.**

### Latest workflow results

- Push run [39942727223](https://github.com/Navee569/Project-Shiv/actions/runs/39942727223), event `push`: overall SUCCESS; main job 120378302376 SUCCESS on exact branch head `7ed3514d8dae780ba4cbdd6ca8a87ba222dc4c6f`. Exact-source PR job skipped as expected for a push event.
- PR run [39942734835](https://github.com/Navee569/Project-Shiv/actions/runs/39942734835), event `pull_request`: overall SUCCESS. Exact-source job 120378314916 SUCCESS and asserts source SHA = checkout SHA = `7ed3514d8dae780ba4cbdd6ca8a87ba222dc4c6f`. Regular merge-result job 120378315122 SUCCESS on synthetic merge SHA `79d7732c09c75c48577962334e5a352413ab367d`. Do not conflate that merge-result SHA with the PR head SHA.
- Strict cold launch passed for exact source: exit 0, `Status: ok`, `LaunchState: COLD`, WaitTime 8731 ms. Regular merge-result strict launch passed: exit 0, `Status: ok`, `LaunchState: COLD`, WaitTime 11578 ms. Mandatory full logcat and crash-buffer captures both exited 0 before subsequent activity assertions.
- ADB emitted transient `adb: device offline` messages during emulator startup in both workflow logs. These did not recur as failures in mandatory post-launch logcat/crash-buffer capture. This is observed transient startup behavior only; cause and permanent resolution are not proven. Historical PR run [39936972682](https://github.com/Navee569/Project-Shiv/actions/runs/39936972682), regular job 120359223524, remains a real failure: `adb -s emulator-5554 logcat -b all -d -v threadtime -t 6000` exited 255 while transport was offline. Do not relabel or erase it.

### Artifacts, checksums, and test evidence

| Evidence | Push run 39942727223 | Exact-source PR run 39942734835 |
|---|---|---|
| Main / exact-source job | 120378302376 | 120378314916 |
| Regular merge-result job | N/A for push | 120378315122; synthetic merge SHA `79d7732c09c75c48577962334e5a352413ab367d` |
| APK artifact ID | 12496879163 | 12496884786 |
| APK archive SHA-256 (GitHub digest and independently recomputed ZIP SHA-256) | `cd429bd3b2bc304ceba16dc1312a82bc9878379a5dc34a82d809478e478925b2` | `eae04c025db819ddc2790a9307adbdedf8c935df71b8400869ad79d51d9cc324` |
| Verification report artifact ID | 12498115274 | 12497293053 |
| Report archive SHA-256 (GitHub digest and independently recomputed ZIP SHA-256) | `2c1b3a5d91b8bada408123cff88260617b4afac403633d3c44f56ca11eb1735f` | `e7b304e93e916d0a0ce0f3f7dd3053de3ff1224d9e5953c880129960e22c8948` |
| APK size | 136,059,539 bytes | 136,059,539 bytes |
| APK SHA-256 | `320bddd236fa612cb6d435dadee3fe6bd909f863e207e6174891f33e83eca3b1` | `fbed6ecc3835f8eaf96cc3541d854b4b1d91d5e33704fc423730d11edc4dac3a` |

The exact-source APK provenance JSON source SHA and checkout SHA match the PR head. Exact-source manifest: application ID `app.shiv`, versionCode 1, versionName `0.1.0`, minSdk 26, targetSdk 35, launcher `app.shiv.MainActivity`, label `Shiv`, icon present. The regular merge-result APK is a separate build: SHA-256 `574608e693e3c52c19ddb75d3e7523437d987fd70969a2367c41dd49a6520143`, size 136,059,539 bytes; do not use that hash as the exact-source APK hash.

- Exact-source report archive: seven main JUnit XML reports, 72 tests, 0 failures, 0 errors, 0 skipped.
- Repeated queue evidence: exactly ten logs and ten matching per-run JUnit XML reports; each row records actual exact head SHA, exit_code=0, tests=10, failures/errors/skipped=0, task_executed=1, junit_valid=1. JUnit reports independently parsed as valid.
- Lint XML: 26 warnings, 0 errors. Categories must be summarized from the retained XML before changing warning-category counts; prior candidate category counts are not assumed to describe this run.
- Archive digests listed above were independently recalculated from downloaded ZIP bytes and match GitHub's published digest.

### Four-core discrepancy — still open

Workflow input and action setup report `Cores: 4`, and configuration write logs `hw.cpu.ncore=4`; however, QEMU warned that four requested SMP CPUs exceed the KVM-recommended two, and emulator output warned AVD `test` would run more smoothly with four cores while currently using two. The latest success artifacts do not retain the actual AVD `config.ini` / effective guest configuration. Therefore effective four-core guest operation remains **NOT VERIFIED**. Preserve warnings and gather direct effective config/guest evidence in a retained artifact; do not infer success from action input alone.

### Acceptance gates still NOT VERIFIED

- Android Keystore key-loss/corruption fault injection and safe data preservation.
- Room transaction/rollback integration and failure atomicity.
- Backup URI write failure, malformed/partial backup handling, and restore rollback.
- Production action-tier and permission-denial behavior.
- Offline-only behavior and network capture proving no hidden cloud fallback.
- Physical Samsung Galaxy S23 verification.
- Comprehensive security assessment.

### Documentation freshness gate and next action

This addendum is being added to all three required documents on the branch. The new documentation commit must receive fresh push and PR workflows. After completion, independently re-fetch `STATUS.md`, `TEST_MATRIX.md`, and `DEFECT_LOG.md` from that exact new head and verify they record the new run IDs/job IDs and outcomes. Until that follow-up check passes, documentation freshness is not accepted. Do not merge or close PR #1.


## Fresh CI follow-up — documentation-remediation SHA a63e0aa782f9bff58c6b563ac38ef1b2a2a30fd3 — 2026-10-23

Fresh workflow outcomes (this section supplements and does not erase historical evidence):
- [Push run 39947033852](https://github.com/Navee569/Project-Shiv/actions/runs/39947033852): SUCCESS. Main job 120392092560 SUCCESS on exact SHA a63e0aa782f9bff58c6b563ac38ef1b2a2a30fd3; PR-only job 120392094468 SKIPPED as expected for push.
- [PR run 39947041925](https://github.com/Navee569/Project-Shiv/actions/runs/39947041925): SUCCESS. Regular build/test/lint job 120392118694 SUCCESS; exact-source smoke job 120392118796 SUCCESS. Exact-source source SHA = checkout SHA = a63e0aa782f9bff58c6b563ac38ef1b2a2a30fd3. Regular PR merge-result checkout SHA = 69fe24d65af46f106a281bf15b3355d8891c2dc2; do not conflate with PR head.

Fresh artifacts, independently downloaded and ZIP-SHA256 verified against GitHub digest:
- Push APK artifact 12500165037, digest/SHA256 a9596fe270c74f25178b03bd01c860ac32441783a326db0e4bf16501e2b16eb8 (72,901,412 bytes); APK SHA256 ac2ca7fc6d678aa0d486c5704d79ab4a17582bedbc2bf75ecec04210f3dfa317.
- Push report artifact 12498904244, digest/SHA256 c4dd3d881a20c73c9cddda58a2b42e73ff8d732ce60d68ead8bdcefe4ac92811 (73,613,355 bytes).
- PR regular merge APK artifact 12499262719, digest/SHA256 07fc67d18fd4905e2c012ea5838fe366216fead5738c5c4c3ed170555683e03a (72,901,442 bytes); APK SHA256 7eaa9b1421ea45929d34dc90cbbac2705909ccbabef4578b7bc0931401ecbc20, synthetic checkout SHA 69fe24d65af46f106a281bf15b3355d8891c2dc2.
- PR report artifact 12499013724, digest/SHA256 364081578bedccf9f9393e8da572acd7d618d774e6b32ad27e18e9d5c8fd4f9d (73,607,667 bytes).
- PR exact-source APK/evidence artifact 12499606348, digest/SHA256 7b94918c260a6166a14ec81207794ab30e217200c84b4caa83879770813e3be4 (73,558,461 bytes); APK SHA256 1bbcfc6d4e36bf840430263905748ba76c1e8c1932cc58a59f0a61ca853b16ad. All APKs are 136,059,539 bytes. Exact-source manifest: app.shiv, versionCode 1, versionName 0.1.0, minSdk 26, targetSdk 35, launcher app.shiv.MainActivity, label Shiv, icon present.

Test evidence for these fresh runs: seven JUnit XML reports, 72 tests / 0 failures / 0 errors / 0 skipped. Ten repeated queue logs and ten matching JUnit reports; exact-source runs target SHA a63e0aa782f9bff58c6b563ac38ef1b2a2a30fd3 and each records exit 0, 10 tests, no failures/errors/skips, task_executed=1, junit_valid=1. Lint: 26 warnings, 0 errors (GradleDependency 15, ObsoleteSdkInt 7, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, Recycle 1).

Strict cold launch passed in all three tested jobs: push WaitTime 15508 ms, PR regular merge-result 13301 ms, PR exact-source 10772 ms; each reports exit 0, Status: ok, LaunchState: COLD. Mandatory logcat and crash-buffer captures exited 0 in all three. Retained report artifacts show initial ADB state online and no device-offline text in retained smoke-report files; this does not prove no message occurred in unretained runner setup logs or permanent resolution of the historical transport issue.

Four-core configuration remains NOT VERIFIED: fresh artifacts still do not retain actual AVD config.ini or direct effective guest CPU/core evidence. Preserve prior QEMU/KVM warnings. Historical PR run 39936972682 / job 120359223524, logcat exit 255 while ADB offline, remains a real historical failure and must not be erased or relabelled as a timeout.

Still NOT VERIFIED: Keystore key-loss/corruption and safe preservation; Room transaction/rollback atomicity; backup URI failure, malformed/partial backup and restore rollback; production action-tier/permission-denial behavior; offline-only/network capture/no hidden cloud fallback; physical Samsung Galaxy S23; comprehensive security assessment. Green hosted CI does not accept M1. PR #1 remains OPEN / DRAFT / UNMERGED.

These results are now recorded for the tested SHA. This documentation commit must receive fresh push and PR workflows, which must be checked independently; M1 remains BLOCKED / NOT ACCEPTED until documentation freshness, direct four-core evidence, and mandatory acceptance gates are resolved or explicitly scoped by the project owner.


## Fresh CI follow-up — documentation commit SHA 7552ab8d7033f573366f23ad71895f9c21f1a81d — 2026-10-23

### Workflow results and provenance
- Push workflow [39949857107](https://github.com/Navee569/Project-Shiv/actions/runs/39949857107): SUCCESS on source SHA `7552ab8d7033f573366f23ad71895f9c21f1a81d`; main job 120401391960 SUCCESS, PR-only job 120401394400 SKIPPED as expected.
- PR workflow [39949863339](https://github.com/Navee569/Project-Shiv/actions/runs/39949863339): SUCCESS. Exact-source job 120401413657 SUCCESS with source SHA and checked-out SHA both `7552ab8d7033f573366f23ad71895f9c21f1a81d`. Regular merge-result job 120401413812 SUCCESS with synthetic merge SHA `d229f9de965de8e5dd955574c83a0a48478d74e8`; its artifact evidence records source SHA 7552ab8d... and checked-out SHA d229f9de.... Never conflate the PR source and merge-result build.

Artifacts independently downloaded and SHA-256 checked against GitHub artifact digests:
- Push APK artifact 12500567416, ZIP size 72,901,415 bytes, SHA-256 `68cee878e77908ceca61b9f41694d58fd68ca68b94c0b469fbe683d8dc07804b`.
- Push reports artifact 12500697048, ZIP size 73,199,096 bytes, SHA-256 `50a62ac415c913be2751fb7f4086995f388a2cd6d224a02ebb796ed1186fd124`.
- PR exact-source artifact 12500084192, ZIP size 73,156,877 bytes, SHA-256 `4aa2349303f244ad1cf6ef381cc2cb1a0fc6a90eda764c6de11c73b347d36d8c`.
- PR regular merge APK artifact 12499444576, ZIP size 72,901,439 bytes, SHA-256 `d9f93fe9e65291512c70f23ded63ec256ecce9dcc02ae32242b8f780dc858770`.
- PR reports artifact 12500436830, ZIP size 73,182,107 bytes, SHA-256 `3105188f2a7bad66d6b7921c62ba61c6a24c655f2a507ddd00456e4709723372`.
- Exact-source APK SHA-256 `1d2664a32557d6c6b61f124a888b96c49d34fea88b4d3e98e4d6728d5bdafb67`, size 136,059,539 bytes. Merge-result APK SHA-256 `f9d35ac27abdaebd34f325f88a418e145e56b9a1c3b0dcac6eb7d47140f094f3`, size 136,059,539 bytes.
- Manifest for both: application ID `app.shiv`, versionCode 1, versionName `0.1.0`, minSdk 26, targetSdk 35, launcher `app.shiv.MainActivity`, label `Shiv`, icon present.

### Tests and emulator evidence
- Fresh PR exact-source and merge-result report archives each contain seven JUnit suite XML files: 72 tests, 0 failures, 0 errors, 0 skipped.
- Each has exactly ten repeated queue logs and ten matching JUnit XML reports. Exact-source queue records show SHA 7552ab8d..., exit_code=0, tests=10, failures=0, errors=0, skipped=0, task_executed=1, junit_valid=1.
- Lint: 26 warnings, 0 errors (GradleDependency 15; ObsoleteSdkInt 7; InlinedApi 1; OldTargetApi 1; DataExtractionRules 1; Recycle 1).
- Cold launch passed: push WaitTime 12540 ms; PR exact-source 12488 ms; PR merge-result 15486 ms. All report exit 0, `Status: ok`, `LaunchState: COLD`; logcat and crash-buffer captures exited 0.
- Full runner logs still show transient `adb: device offline` during emulator startup before ADB comes online and smoke proceeds. Successful final smoke/logcat evidence does not prove the transport issue is permanently resolved. Preserve the startup timeline and recovery diagnostics. Historical run 39936972682 / job 120359223524 remains a genuine regular merge-result logcat exit 255 while ADB was offline; do not erase or relabel it.

### Four-core configuration
Four-core guest execution remains **NOT VERIFIED**. The run logs show action input `Cores: 4` and config write `hw.cpu.ncore=4`, but also warn requested SMP CPUs exceed KVM's recommended two and that the AVD is currently using two. The existing artifact does not contain the effective AVD config.ini or direct guest CPU count. A follow-up change now adds `effective-cpu-evidence.txt` collection to the emulator smoke report, recording the actual config.ini, emulator/QEMU command line, guest online CPU mask, `getconf _NPROCESSORS_ONLN`, and guest `/proc/cpuinfo`. This is instrumentation, not proof until a new workflow artifact demonstrates the actual count.

### M1 status and unresolved gates
M1 remains **NOT ACCEPTED / BLOCKED**. Still NOT VERIFIED: (1) Android Keystore key-loss/corruption injection and safe data preservation; (2) Room transaction rollback/failure atomicity; (3) backup URI write failure, malformed/partial backup, restore rollback; (4) production action-tier and permission-denial paths; (5) offline-only network capture/no hidden cloud fallback; (6) physical Samsung Galaxy S23; (7) comprehensive security assessment. Hosted unit tests and emulator smoke do not substitute for these direct tests. PR #1 must remain open, draft, and unmerged.

This commit records the just-completed workflows and adds effective CPU evidence collection. The new documentation/instrumentation commit must itself receive fresh push and PR workflows. After those finish, update this record with their exact outcomes and artifacts in a subsequent documentation commit; do not claim a commit's own future workflow results are already documented on that SHA.
