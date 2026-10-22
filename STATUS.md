# Project Shiv — Developer Milestone Status Report

**Report date:** 2026-10-21  
**Milestone:** M0 — Repository discovery and baseline build verification  
**Milestone state:** IN PROGRESS — acceptance not yet demonstrated  
**Repository:** `Navee569/Project-Shiv` (private)  
**Development branch:** `dev/m0-baseline-discovery`  
**Pull request:** [#1 — M0 baseline verification](https://github.com/Navee569/Project-Shiv/pull/1)  
**Original QA baseline commit:** `88626b1159243b0358e0736b5b2b16c7573ef64f`  
**PR merge revision from failed run:** `3bb9663f1784fd5a03f3e10861678c30acdfafbc` (merge revision, not the branch head)  
**CI fix commit:** `b759ab6b54398d890946feb8da6cf6f0498e09e1`  
**Previous documentation/cleanup revision:** `1c80c7b5332293190dc151c69040e76b9a83c358`  
**CI artifact correction commit:** `51ac55702374d96d122a5c22982773ea351f6b80`  
**Failed run under investigation:** [39635257720](https://github.com/Navee569/Project-Shiv/actions/runs/39635257720)  
**Latest completed verification run:** [39637748925](https://github.com/Navee569/Project-Shiv/actions/runs/39637748925) — FAILURE on branch head `8734e908f968d2d6205d90f1fe92278ee948f118`. **Corrective source revision:** `afe08a0fb3e94d6ee483fbfba0c3857d45b93116`. Follow-up runs [39639306213](https://github.com/Navee569/Project-Shiv/actions/runs/39639306213) and [39639315700](https://github.com/Navee569/Project-Shiv/actions/runs/39639315700) were queued for the corrective revision; no successful build is yet claimed.

> This report distinguishes repository inspection from commands actually executed. The available session can read and commit through GitHub but does not expose a local Android SDK/Gradle execution session. Do not claim a build, unit tests, lint, APK inspection, or device test passed unless a fresh CI run or a suitable local environment supplies evidence.

## Milestone change log

### M0 CI setup failure correction

**Finding:** The failed workflow's Android SDK setup step failed before SDK platform installation, toolchain recording, assembly, unit tests, or lint. The job summary shows those steps skipped; artifact upload succeeded but is not build evidence. The follow-up correction run was later observed as cancelled after branch activity; re-check Actions for a fresh run and do not infer success.

**Root cause:** The exact `android-actions/setup-android@v3/action.yml` declares `packages` default as `tools platform-tools`. Google no longer serves the legacy `tools` SDK package, and sdkmanager returns `Failed to find package 'tools'`.

**Correction committed:** `.github/workflows/android-ci.yml` explicitly configures `packages: platform-tools` and `accept-android-sdk-licenses: true` for setup-android@v3. The workflow still installs `platforms;android-35` and `build-tools;35.0.0`, accepts licenses, sets JDK 17, uses the existing Gradle wrapper, runs project discovery, debug assembly, unit tests, debug lint, and collects reports/artifact metadata. No checks were disabled.

**Source evidence:** The action's version-3 `action.yml` has the retired package in its default input. Its public documentation explains that the `tools` package is no longer served. The failing workflow log's first meaningful error is the SDK package failure.

## Repository/configuration inspection

| Check | Inspection result | Execution/verification state |
|---|---|---|
| `:app` module | `settings.gradle.kts` includes `:app` | Gradle discovery pending fresh CI |
| Android application plugin | Root declares Android Gradle Plugin 8.7.3; app applies `com.android.application` | Gradle configuration pending |
| Namespace / application ID | Both `app.shiv` in `app/build.gradle.kts` | APK metadata pending |
| Launcher | Manifest declares `.MainActivity` with MAIN/LAUNCHER and exported=true | Merged manifest / runtime launch not verified |
| Java source/target | Java 17 | Toolchain execution pending |
| Gradle wrapper | Gradle 8.9 distribution, SHA-256 configured, URL validation enabled; wrapper scripts/JAR present | Wrapper execution pending |
| SDK requirements | Platform 35 and build-tools 35.0.0 requested in workflow; platform-tools requested by action | Installation pending fresh CI |
| Unit tests | No `app/src/test` tree present at inspected source revision | Test task/count pending fresh CI |
| Instrumentation tests | No `app/src/androidTest` tree present at inspected source revision | No instrumentation tests currently identified |
| Android Studio run configuration | `.run/App.run.xml` exists; module/Gradle configuration must be confirmed after sync | Root cause not yet established |
| Device verification | No device execution evidence available | NOT RUN |

The root Gradle script pins AGP 8.7.3, Kotlin Android/Compose 2.0.21, and KSP 2.0.21-1.0.28. The project targets SDK 35 and Java 17. Compatibility must be confirmed by actual Gradle execution; source-level configuration alone is not a pass.

## Current acceptance status

M0 remains **NOT ACCEPTED** until a fresh run on the corrected source revision verifies all of the following:

- Android SDK setup succeeds.
- SDK platform 35 and build-tools 35.0.0 install; platform-tools remain available.
- JDK 17 and Gradle wrapper execute and their versions are recorded.
- `./gradlew projects` recognises `:app`.
- `./gradlew :app:assembleDebug` passes and the produced APK is inspected for package ID and launcher activity.
- `./gradlew :app:testDebugUnitTest` executes and its actual test counts are recorded. An empty test suite must be reported honestly.
- `./gradlew :app:lintDebug` passes.
- CI step conclusions, logs, test/lint reports and APK artifact are inspected.
- Android Studio run-configuration issue is investigated separately.
- Independent QA reviews the evidence.

## Git / CI evidence

- Base branch: `codex/shiv-complete-beta`
- Base revision recorded on PR: `96e5dd78eedb8f12cf1e2aee758364284de03276`
- Original QA branch-head revision: `88626b1159243b0358e0736b5b2b16c7573ef64f`
- Failed run's PR merge revision: `3bb9663f1784fd5a03f3e10861678c30acdfafbc`
- CI correction commit: `b759ab6b54398d890946feb8da6cf6f0498e09e1`
- Duplicate-workflow cleanup revision: `1c80c7b5332293190dc151c69040e76b9a83c358`
- CI artifact correction revision: `51ac55702374d96d122a5c22982773ea351f6b80`
- Corrected file: `.github/workflows/android-ci.yml`
- Failed workflow run: https://github.com/Navee569/Project-Shiv/actions/runs/39635257720
- Pull request: https://github.com/Navee569/Project-Shiv/pull/1

## Mandatory report for every milestone

At each milestone completion or handoff, update this file (or a linked milestone report) with:
1. Milestone ID/name and acceptance state.
2. Repository, branch, base revision, starting revision, and final commit SHA.
3. Exact files changed and concise change summary.
4. Commands/tasks actually run, exit codes, and links to full logs.
5. Build/test/lint outcomes with real counts, not inferred results.
6. Artifact paths, checksums and package metadata where applicable.
7. Defects found, fixes made, outstanding failures and risks.
8. Device/manual QA state and evidence.
9. Independent QA status and explicit acceptance decision.
10. Next milestone and blocking dependencies.

Never mark a milestone complete merely because code was committed, CI continued, or artifact upload succeeded.


## Independent QA findings — 2026-10-21

The independent QA report keeps M0 **NOT ACCEPTED**. Findings and follow-up actions:

- **SHIV-M0-001 (S2):** The CI artifact paths previously omitted the APK itself. Corrected in commit `51ac55702374d96d122a5c22982773ea351f6b80` by adding `app/build/outputs/apk/debug/*.apk` while retaining metadata, reports, tests, and the existing build/test/lint steps. This correction is committed but still requires a completed CI run and artifact inspection.
- **SHIV-M0-002 (S1):** Baseline build/test/lint verification was pending in run [39636975888](https://github.com/Navee569/Project-Shiv/actions/runs/39636975888) at the time of the QA report. The SDK setup and SDK platform/build-tools installation had succeeded; toolchain recording had completed; debug assembly was still running. No test/lint pass is inferred. Re-check this run and the run triggered by the APK-artifact correction.
- **SHIV-M0-003 (S2):** Android Studio run configuration remains unverified. Requires Gradle sync and actual run-configuration resolution; no source-level inference is treated as a pass.

### Current verification state after artifact fix

- Latest reported workflow correction commit: `51ac55702374d96d122a5c22982773ea351f6b80`.
- Workflow file re-read after commit; the APK glob is present.
- No successful run for this latest commit has yet been verified.
- No APK checksum, package metadata, test counts, lint outcome, or physical-device result is claimed.
- M0 remains **NOT ACCEPTED** until the latest applicable run finishes, artifacts are inspected, and independent QA retests the findings.


## Latest M0 regression result and corrective work — 2026-10-21

### Failed run preserved in history

The latest completed baseline run before these corrections was [39637748925](https://github.com/Navee569/Project-Shiv/actions/runs/39637748925), run number 19. The branch head tested by that run was `8734e908f968d2d6205d90f1fe92278ee948f118`; the pull-request workflow checked out synthetic merge revision `2b690adc16b403d63fdc7807c0d43da7b2ae7eee`.

Observed task results:
- SDK setup and SDK platform/build-tools installation: SUCCESS.
- Toolchain recording and Gradle project discovery: SUCCESS.
- `:app:assembleDebug`: FAILURE at `:app:kspDebugKotlin`.
- `:app:testDebugUnitTest`: SKIPPED.
- `:app:lintDebug`: SKIPPED.
- Artifact upload action: ran successfully, but reported no matching files; no artifact was uploaded.
- APK checksum, package metadata, test counts, and lint findings: unavailable because no APK/report artifact was produced.
- Android Studio sync/run configuration and device launch: NOT VERIFIED.

The recorded compiler diagnostics identify two independent source/toolchain issues:
1. LiteRT-LM 0.18.0 and transitive Kotlin libraries expose Kotlin metadata newer than the configured Kotlin 2.0.21 compiler can read.
2. `app/src/main/java/app/shiv/ProactiveWorker.kt` contains malformed source content, causing unmatched-parenthesis, unexpected-token, and missing-brace diagnostics. The corrupted content was already present at the original baseline and is now replaced by a clean implementation; this is not represented as a verified successful compile until CI confirms it.

### Corrective commits on `dev/m0-baseline-discovery`

- `8fc3edca704f852a159bf06ce85dfeac3155d3f0` — align Kotlin Android/Compose plugins to 2.2.21 and KSP to 2.2.21-2.0.5. These versions are within the published Kotlin Gradle Plugin compatibility range for the existing Gradle 8.9 / AGP 8.7.3 combination.
- `469da444f6e25b75607f3c44567c9652fd02d7f2` — pin LiteRT-LM to 0.16.1, avoiding the 0.18.0 dependency's Kotlin 2.4 metadata while retaining the on-device inference dependency.
- `afe08a0fb3e94d6ee483fbfba0c3857d45b93116` — replace the corrupted `ProactiveWorker.kt` content with valid Kotlin implementing quiet-hours checks, daily interruption limits, duplicate-event suppression, notifications, and periodic/one-time WorkManager scheduling.

The worker rewrite and dependency/toolchain changes are source corrections, not build verification. API compatibility, KSP processing, Room generation, APK assembly, unit tests, and lint must be proven by the follow-up CI run.

### Follow-up CI state at report update

The GitHub Actions API listed two runs for corrective source revision `afe08a0fb3e94d6ee483fbfba0c3857d45b93116`:
- [Run 39639306213](https://github.com/Navee569/Project-Shiv/actions/runs/39639306213), run number 23 — pending at inspection.
- [Run 39639315700](https://github.com/Navee569/Project-Shiv/actions/runs/39639315700), run number 24 — pending at inspection.

No completed job steps or artifacts were available from those pending runs at the time of this update. Their final results must be inspected before recording build/test/lint outcomes. If either run is cancelled by concurrency, use the surviving completed run or re-run the full verification on the same corrective source revision.

### Current M0 decision

M0 remains **NOT ACCEPTED**. The Android Studio configuration issue is still blocked by lack of an Android Studio/device execution environment. APK verification, unit-test counts, lint findings, and artifact provenance remain unverified until a corrected CI run completes successfully and produces inspectable outputs. Independent QA must retest the resulting revision before acceptance.


### Additional source correction after this report update

- Commit `4fea57277b8d5e32691df5f79d605252747f964c` moves the suspendable `dao.wasProactiveSent(...)` call out of a non-suspending list predicate in `ProactiveWorker.kt`. This avoids a Kotlin suspend-context compile error identified during static review of the new worker.
- CI runs for this code revision were observed as pending at the next inspection: [39639467167](https://github.com/Navee569/Project-Shiv/actions/runs/39639467167) (push, run 27) and [39639472921](https://github.com/Navee569/Project-Shiv/actions/runs/39639472921) (pull request, run 28). No completed results or artifacts were available at that inspection.
- The earlier runs for intermediate commits were cancelled by subsequent branch updates. The run for source revision `4fea57277b8d5e32691df5f79d605252747f964c` remains the relevant target for validating the corrected source. M0 remains **NOT ACCEPTED** pending completed build, unit-test, lint, artifact, and separate IDE/device evidence.


## M0 verification update — 2026-10-21, after run 31

This section supersedes earlier statements in this document that described run 31's build as pending. Preserve the earlier entries as historical records.

### Completed run 31

- Run: [39640948661](https://github.com/Navee569/Project-Shiv/actions/runs/39640948661)
- Run number: 31
- Event: push
- Tested branch revision: `d8938f9590dd0649feffd9578fb2ca8e81100ac2`
- Final conclusion: **FAILURE**
- Job: Build, unit tests, and lint
- Java: OpenJDK 17.0.20.1
- Gradle: 8.9
- `./gradlew projects`: BUILD SUCCESSFUL; root project `Shiv`, module `:app`
- SDK setup and Android SDK Platform 35 / Build Tools 35.0.0 installation: SUCCESS
- `:app:assembleDebug`: FAILURE at `:app:compileDebugKotlin`
- `:app:testDebugUnitTest`: SKIPPED
- `:app:lintDebug`: SKIPPED
- Artifact upload step: action completed, but no files matched; no artifact uploaded.

### Compiler findings from run 31

The Room/KSP failure from the previous run is no longer the failing task after updating Room runtime, KTX, and compiler from 2.6.1 to 2.7.2. The build progressed through `:app:kspDebugKotlin` and then failed at Kotlin compilation with these observed errors:

- `Brain.kt:260:99`: unresolved reference `text` in the LiteRT-LM async response collector.
- `Brain.kt:326:23`: unresolved reference `salutationGot`.
- `Brain.kt:343:48`: missing required `allowNotifications` argument to `generateDecision`.
- `PhotoCapture.kt:6:29` and `PhotoCapture.kt:27:32`: unresolved `CameraController`.

No APK, test report, lint report, or verification artifact was produced by run 31. No test count or lint finding count is available.

### Source corrections after run 31

- Commit `0a0fd2d5a423c1543881aae170dbd673b24048f8` — updated the LiteRT-LM streamed response handling to append the message's string representation, fixed string interpolation for the optional salutation, and passed `allowNotifications = false` for chat-driven decisions.
- Commit `b835d9e531dce1c2ad78d9ce11ca9bb7e9028858` — changed the `CameraController` import in `PhotoCapture.kt` to `androidx.camera.view.CameraController`.

These changes are not considered verified until the subsequent CI run completes.

### Next verification target

The branch head after the application fixes is `b835d9e531dce1c2ad78d9ce11ca9bb7e9028858`. A subsequent status-only commit may become the workflow checkout head while retaining these application changes. Verify the exact source revision and final outcome from the next completed run.

### Acceptance

M0 remains **NOT ACCEPTED**. The latest completed build failed; unit tests and lint were skipped; no APK or reports were uploaded; and Android Studio/device verification remains blocked. Do not advance to the next milestone until a corrected completed run and independent QA evidence satisfy the mandatory criteria.


## Independent M0 regression retest — 2026-10-22

### Exact-SHA result for `a942af9613f7561d671de5aba9b5cfdbd717d522`

Independent QA confirmed branch HEAD matched the requested SHA and both exact-SHA workflows passed:
- Push run [39654587925](https://github.com/Navee569/Project-Shiv/actions/runs/39654587925): `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, artifact upload all SUCCESS.
- Pull-request run [39654596592](https://github.com/Navee569/Project-Shiv/actions/runs/39654596592): same tasks and artifact upload all SUCCESS.
- JUnit XML: 5 tests executed, 5 passed, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors, 27 warnings; `UnsafeOptInUsageError` absent.
- Push artifact ID 12371746628; ZIP SHA-256 `c17e43fbf5ca684ecf00601a189d30054d53cf60f33887214b68bb3e0ba2e0ea`.
- PR artifact ID 12369614921; ZIP SHA-256 `091500e2a6a6c67c225dd69c92a263f688662ac0bb59d3aa6f6d5387caa59f2d`.
- APK metadata: application ID `app.shiv`, debug variant, versionCode 1, versionName 0.1.0, minSdk 26. Push APK SHA-256 `87f2d389f675312ec1325df6b2b20b24fb05ef5c365273333a543f48b96025f8`; PR APK SHA-256 `596345920536d963954d0c5188a1a635bc0012f89f37e31526ee734e898ecdf7`.
- Warning categories: GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, MissingApplicationIcon 1.

### Follow-up implementation from QA findings

The helper-only test previously recreated the production conditional instead of calling shared production code. The follow-up extracts the terminal-success gate into `PhotoCaptureCompletion.finishSuccess`; `PhotoCapture.kt` calls that same method around `onDescription` and `onClose`. A regression test now calls the shared production gate and checks that a failure-first/late-success sequence triggers neither success effect, closes once, invokes completion once, and preserves the original failure.

The exception policy is explicit: an exception from `onComplete` propagates to its caller. Terminal ownership is already claimed and image closure has already been attempted; success-side effects do not run because `finishSuccess` cannot proceed past the throwing `finish`. A focused test checks propagation and no duplicate cleanup. The helper intentionally does not catch `Throwable` broadly.

**Important:** the above exact-SHA CI results apply to `a942af9613f7561d671de5aba9b5cfdbd717d522`, before these follow-up changes. The new source/docs commit must receive its own exact-SHA CI verification. Do not reuse the prior results as proof for the new revision.

### Remaining M0 blockers

- Android Studio sync/run-configuration execution: NOT VERIFIED.
- APK binary manifest and launcher resolution: NOT VERIFIED.
- Physical-device/emulator install and launch, permission denial, camera preview/capture, ML Kit result, and real ImageProxy lifetime: BLOCKED / NOT VERIFIED.
- Real CameraX/ML Kit integration is not claimed by helper-level unit tests.
- M0 remains **NOT ACCEPTED**; do not begin the next milestone.
