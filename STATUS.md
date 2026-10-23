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


## Independent QA follow-up — 2026-10-22 — SHIV-M0-004 to SHIV-M0-006

QA report: `Project_Shiv_M0_Independent_Regression_Retest_1f53acc.txt`, testing exact SHA `1f53acc34887245c94b30fd9b4ae20032476ce56`.

### Verified CI for SHA 1f53acc

- Push run [39708040012](https://github.com/Navee569/Project-Shiv/actions/runs/39708040012) and PR run [39708044974](https://github.com/Navee569/Project-Shiv/actions/runs/39708044974): both SUCCESS on the exact same SHA.
- Toolchain: Temurin OpenJDK 17.0.20.1, Gradle 8.9, Android platform 35, build-tools 35.0.0; Gradle project discovery recognized root `Shiv` and module `:app`.
- `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`: all SUCCESS.
- JUnit XML in both artifacts: 6 tests, 6 passed, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors, 27 warnings; categories unchanged from the previous tested revision: GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, MissingApplicationIcon 1. `UnsafeOptInUsageError` absent.
- Push artifact ID 12395862458, ZIP SHA-256 `c2b45080b8945abb83fc55e7e12c90f20e0d7959c28baba30f572b40b48aed76`.
- PR artifact ID 12396501321, ZIP SHA-256 `e2acd16b2f3241bda109915a89035997de854f4e0144ac318d02f5911bf36dbc`. QA independently computed both ZIP digests and confirmed matches.
- APK metadata: applicationId `app.shiv`, debug variant, versionCode 1, versionName 0.1.0, minSdk 26. Push APK SHA-256 `2e9a6b43fede47beb14cea0bf04ce9c15e8bd8a9498f45267118cb9980a19605`; PR APK SHA-256 `8bd952bd444876ea9a2e3e2cee81204f00bb07116f29a77d53dca4e37d38cc81`. The two APK hashes differ despite equal file size; no bit-for-bit reproducibility claim is made.

### SHIV-M0-004 — normal success-first gate test

QA found no test of a normal successful `finishSuccess` call that proved both success effects execute exactly once and later terminal calls are rejected. Added a deterministic test to `PhotoCaptureCompletionTest.kt` that checks first success accepted, both effect counters equal one, second success and late terminal failure rejected, cleanup and completion each once, and no terminal error. This source change is not verified until CI runs on its new SHA.

### SHIV-M0-005 — documentation freshness

This dated update records the prior exact-SHA CI, test, lint, artifact and APK evidence without relabeling it as evidence for the new source revision. The next CI run must verify the test addition's new exact SHA before its test count or build status is called current.

### SHIV-M0-006 — runtime verification remains blocked

Android Studio sync/run configuration, APK binary manifest and launcher resolution, physical-device/emulator installation and launch, camera permission denial, preview, capture, ML Kit description, and real asynchronous ImageProxy lifetime remain NOT VERIFIED or BLOCKED. Helper tests are not represented as real CameraX/ML Kit integration evidence.

**M0 decision: NOT ACCEPTED.** Do not approve or merge PR #1 or start the next milestone until the new exact-SHA CI passes, independent QA retests it, and all mandatory IDE/manifest/device requirements are satisfied.


## Independent M0 retest evidence update — 2026-10-22 — SHA `597be93da4b45e7a8caf780fb9628d85e307cc56`

This section records the independent regression report for the exact branch HEAD. It supersedes only prior statements that this revision's CI was pending; historical evidence is retained.

### Exact-SHA CI and test results

- Push workflow [run 39709266980](https://github.com/Navee569/Project-Shiv/actions/runs/39709266980): SUCCESS; checkout was exact commit `597be93da4b45e7a8caf780fb9628d85e307cc56`.
- Pull-request workflow [run 39709271615](https://github.com/Navee569/Project-Shiv/actions/runs/39709271615): SUCCESS for the same PR head. Its worktree was synthetic merge commit `8f742c27ce1890a492af9f6b89b0fead38d80f38` (head `597be93...` merged into base `96e5dd78eedb8f12cf1e2aee758364284de03276`); treat this as merge-result validation, not the exact branch tree.
- Toolchain: Temurin/OpenJDK 17.0.20.1+1, Gradle 8.9, Android platform 35, Build Tools 35.0.0, lint 8.7.3. Gradle wrapper validation and `./gradlew projects` succeeded; root project `Shiv`, module `:app`.
- In both runs, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and artifact upload succeeded.
- Actual JUnit XML suite `app.shiv.PhotoCaptureCompletionTest`: **7 executed, 7 passed, 0 failed, 0 errors, 0 skipped** in each artifact. All seven test names, including `normalSuccessFirstRunsSuccessEffectsExactlyOnce`, were present and passed.
- Lint XML in both artifacts: **0 errors, 27 warnings** — GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, MissingApplicationIcon 1. `UnsafeOptInUsageError` was absent.

### Artifact provenance and APK metadata

- Push artifact [ID 12396373994](https://github.com/Navee569/Project-Shiv/actions/runs/39709266980/artifacts/12396373994), size 72,897,698 bytes. GitHub digest and independently computed ZIP SHA-256 both matched: `99a1d2d94914d437acada936a11c2bc8deacca021faaed4e52a0608011eae41f`. APK size 136,026,076 bytes; APK SHA-256 `53ca69ad77fae89ecfbb24c8ea42ee22d6faf174b30609ba5f9a01a28475af53`.
- PR artifact [ID 12396114520](https://github.com/Navee569/Project-Shiv/actions/runs/39709271615/artifacts/12396114520), size 72,897,678 bytes. GitHub digest and independently computed ZIP SHA-256 both matched: `46b37713e571c446f5144f8f6d3070abc8969ed9d3dde374308d401c02f1bf7d`. APK size 136,026,076 bytes; APK SHA-256 `43270a96ecd6e4cb1e0459385b1869e937dd1637a681ce1f6f9a361cea59550f`.
- Both APK output metadata files report applicationId `app.shiv`, variant `debug`, versionCode 1, versionName `0.1.0`, minSdk 26. APK hashes differ; byte-for-byte reproducibility is **not** claimed. Output metadata is not binary-manifest inspection evidence.

### Code review and outstanding gates

The new success-first test invokes the shared production `PhotoCaptureCompletion.finishSuccess()` gate and checks both success effects, cleanup, and completion occur exactly once; duplicate success and late terminal failure are rejected, with null terminal error. Failure-first/late-success, exception propagation, cleanup-once, and error-preservation tests also pass at helper level. These deterministic unit tests do not establish real CameraX/ML Kit integration correctness.

Still NOT VERIFIED or BLOCKED:
- Android Studio Gradle sync and run-configuration resolution.
- Inspection of the packaged APK's binary manifest and launcher resolution.
- Physical-device/emulator install and cold launch.
- Camera permission-denial behaviour, preview, actual capture, and real ML Kit description.
- Actual asynchronous ImageProxy lifetime and exactly-once closure under success/failure.

QA could not run Android tooling or a device/emulator in its inspection environment. Do not infer these gates from CI success, APK metadata, or helper tests.

**M0 decision: NOT ACCEPTED.** PR #1 remains open/draft and unmerged. Do not approve/merge or start the next milestone until the mandatory IDE, binary-manifest, and device/runtime evidence is supplied and independently reviewed.


## Follow-up: automated APK manifest gate added — 2026-10-22

After independent retest of `eb218cbb02a6f501dd456b9e70b6da25170b772b`, the QA report confirmed all CI tasks and seven unit tests pass but identified binary APK manifest/launcher inspection as a mandatory gap. The CI workflow has now been extended to run Android Build Tools `aapt dump badging` against the assembled debug APK and fail unless it identifies package `app.shiv` and launchable activity `app.shiv.MainActivity`. The inspection output is collected as an artifact.

**Verification status:** This is a workflow change, not yet verified evidence. The new workflow must complete on its own exact commit; inspect its logs and uploaded manifest report before marking the automated binary-manifest check PASS. This does not verify Android Studio sync/run configuration, installation/cold launch, camera permission behaviour, preview/capture, ML Kit inference, or real asynchronous ImageProxy lifetime. Those remain NOT VERIFIED/BLOCKED.

**M0 remains NOT ACCEPTED.** Preserve PR #1 as open and unmerged until all mandatory criteria have independently verifiable evidence.


## Independent M0 retest — exact source SHA 8c860e197b0280c299ca890e1a0872bcf4f4fc0c — 2026-10-22

The independent report inspected the exact branch-tree push run and the separate PR merge-result run for this revision:

- Exact-commit push workflow [39712010450](https://github.com/Navee569/Project-Shiv/actions/runs/39712010450): SUCCESS on source SHA `8c860e197b0280c299ca890e1a0872bcf4f4fc0c`.
- PR workflow [39712016130](https://github.com/Navee569/Project-Shiv/actions/runs/39712016130): SUCCESS for the same PR head; the tested worktree was synthetic merge SHA `e21426dc26247226087a5273a9ef58dde75ed1ac`, with base `96e5dd78eedb8f12cf1e2aee758364284de03276`. This is merge-result validation, not the exact branch tree.
- Toolchain: OpenJDK 17.0.20.1, Gradle 8.9, Android platform 35, Build Tools 35.0.0, lint 8.7.3. Gradle project discovery, `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and artifact upload passed in both runs.
- JUnit XML suite `app.shiv.PhotoCaptureCompletionTest`: 7/7 passed, zero failures/errors/skips.
- Lint: zero errors and 27 warnings (GradleDependency 15, ObsoleteSdkInt 6, Recycle 2, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, MissingApplicationIcon 1).
- Packaged binary manifest inspection passed: `aapt dump badging` reported `package: name='app.shiv' versionCode='1' versionName='0.1.0'` and `launchable-activity: name='app.shiv.MainActivity' label='' icon=''`.
- Push artifact ID `12397658751`: archive digest independently matched `116803628393670796c1d71dfc0a7d1b2f366fba49a22fec954f25f511a4ab5a`; APK SHA-256 `4193e608c111026aeaa8727a8d7ee0a6a8dce86d0016ed98753beead5ad0e203`.
- PR artifact ID `12397003355`: archive digest independently matched `9cc2bcc4f3236152c61283073db1be8e57ce5c355cc9609a2a9cc8665b303003`; APK SHA-256 `2d9bf52a3611a165aa2f6b9fbf530c441fead244f8e66fbe82c60f2d3e3b8f45`. Both APKs are 136,026,076 bytes; hashes differ, so no byte-identical reproducibility claim is made.

### Remaining acceptance gaps (not silently waived)

- Android Studio Gradle sync and actual resolution/execution of `.run/App.run.xml`: NOT VERIFIED.
- Install and cold launch on a physical device: NOT VERIFIED.
- Camera permission denial, preview, real capture, and actual ML Kit description: NOT VERIFIED.
- Actual asynchronous CameraX `ImageProxy` lifetime and exactly-once closure through real callbacks: NOT VERIFIED.
- Lint still reports 27 warnings; the existing warning set includes missing application icon metadata.

**Decision: M0 NOT ACCEPTED.** PR #1 remains open/draft and unmerged. This evidence is tied only to SHA `8c860e1...`; the next commit requires its own CI and independent retest evidence. The following consolidated change adds an emulator install/cold-launch smoke check, but cannot substitute for a physical-device camera test or Android Studio validation.


## Consolidated remediation — 2026-10-22 — source revision pending CI

After the independent retest of SHA `8c860e197b0280c299ca890e1a0872bcf4f4fc0c`, a single consolidated source/workflow update was prepared:

- Camera preview is explicitly enabled alongside image capture. Previously the controller enabled only `IMAGE_CAPTURE`, which could leave the preview use case disabled.
- The photo screen now requests Android's runtime CAMERA permission before capture and explains denial instead of attempting camera use without permission.
- Added a vector launcher icon and declared it on the application, addressing the confirmed missing-application-icon lint warning.
- Added a CI static validation step for `.run/App.run.xml` structure and its module/application declarations. This is not a substitute for launching Android Studio and verifying a real Gradle sync/run.
- Retained the emulator clean-install/cold-launch smoke test added in the preceding commit.
- No real physical device is attached to this GitHub-only execution environment; physical-device camera testing and actual ML Kit output cannot be truthfully marked PASS from source inspection or emulator startup alone.

The new revision's build, unit tests, lint, APK manifest, emulator smoke test and static run-configuration check are all **PENDING exact-SHA CI evidence**. The camera/ImageProxy asynchronous ownership path still requires end-to-end device evidence; helper tests alone do not establish that. M0 remains **NOT ACCEPTED** until the new evidence is inspected and the remaining runtime gates are independently closed.


## Consolidated compile and runtime remediation — 2026-10-22 — pending exact-SHA CI

The independent developer remediation handoff identified a blocking compile failure in SHA `04954f747e84940f2f07c33f343804c23fd477f9`: `CameraController.PREVIEW` does not exist, and the combined expression passed a `BigInteger` where the configured CameraX API expects an `Int`. The invalid use-case expression has been removed. Preview is provided by attaching the `LifecycleCameraController` to the visible `PreviewView`; the controller's supported default use cases retain image capture.

The consolidated change re-checks camera permission on lifecycle resume, binds the camera only while permission is granted, and reports camera binding/capture/processing errors. CI checks the packaged APK label and icon, clears emulator logcat before launch to avoid historic crash false positives, and asserts the app process remains alive after startup. Emulator reports are included in the existing always-run artifact step.

**Verification status:** This GitHub-only edit session did not execute Gradle locally or a physical Android Studio session. Build, unit tests, lint, APK manifest, and emulator smoke checks are pending exact-SHA CI. Android Studio sync/run and real physical-camera/ML Kit/ImageProxy integration remain NOT VERIFIED until actual evidence exists. M0 remains NOT ACCEPTED.

## Independent QA finding and remediation record — 2026-10-22

The independent Tester AI reported a blocking compile failure on candidate branch SHA `e1cbd88051c62c09266040938066fbbd69965847`. The PR workflow [39715598035](https://github.com/Navee569/Project-Shiv/actions/runs/39715598035) failed while compiling the debug APK on synthetic merge SHA `87cd4947429d65a49aadfabc9f93a437be336389`.

Exact compiler diagnostic:
`PhotoCapture.kt:60:49 'val' cannot be reassigned.`

The cause is receiver/name shadowing in `PreviewView(it).apply { controller = controller }`. APK manifest inspection, emulator smoke, unit tests, and lint were skipped by that failed workflow. Artifact upload reported no matching files; no artifact was uploaded.

The remediation candidate changes the preview setup to assign `this.controller = cameraController` explicitly, retains the CameraX controller and image capture, improves emulator crash/ANR log collection, and records APK size and SHA-256 in CI reports. The accidentally committed tester prompt is removed in this remediation commit; tester prompts remain external deliverables.

**New-candidate verification state:** PENDING until a new exact-source push workflow and the corresponding PR workflow complete. No build, unit-test, lint, APK, or runtime pass is claimed here. M0 remains **NOT ACCEPTED** until independent QA retests the resulting exact SHA and required device/IDE limitations are resolved or explicitly handled under the acceptance rules.

## Consolidated remediation for independent QA findings — 2026-10-22

The exact-source push workflow [39718207920](https://github.com/Navee569/Project-Shiv/actions/runs/39718207920) and PR workflow [39718211150](https://github.com/Navee569/Project-Shiv/actions/runs/39718211150) both **FAILED** at Android emulator startup with repeated adb exit code 1 and `Timeout waiting for emulator to boot`. The app install/cold-launch script did not execute; this is not evidence of an app crash. The build and APK metadata steps passed, but unit tests and lint were skipped by the old sequential workflow. Artifact upload passed.

Observed artifacts on source SHA `cc5f07d951e7675bb2904dca08948462b5155468`:
- PR synthetic merge SHA: `32e69faa251c6d7d4e74c82e3a4443bbda4a8bdb`.
- Push artifact ID `12400717002`, digest `sha256:929a4f4dbe5aa1404aff104b854ef18f2a236f1444e622f9ffed4f841482b7ce`.
- PR artifact ID `12400936649`, digest `sha256:60ad72aed4efdb8b271749c8ea9a5cba74f1f319852322b6f4ed29ba2aa1fd62`.
- Push APK SHA-256 `6e5f5129486cb47544de86447b444c0968c86f8974be153e4d6548226415a153`; size 136026771 bytes.
- PR APK SHA-256 `09c96c8b589944807782e41b2dcab3351ee1069248d683582016607bdaf068f2`; size 136026771 bytes.
- Current-candidate unit tests and lint did not run; no historical test counts are carried forward.

The consolidated remediation candidate increases the emulator boot timeout, uses an explicit API 35 Pixel 6 AVD with software-rendering/no-snapshot options, adds always-run emulator diagnostics, and runs unit tests/lint after successful APK build even when emulator smoke fails. The image-labeling path is moved behind a testable cleanup pipeline that closes the ML Kit labeler before delivering the result, handles synchronous setup failures, and gates terminal callback delivery exactly once. New pipeline regression tests cover setup failure, success, failure, callback exceptions, duplicate/late callbacks and ImageProxy ownership ordering.

**New-candidate verification:** PENDING fresh exact-SHA push and PR workflows. No new build/test/lint/emulator pass is claimed until logs and reports are inspected. Android Studio sync/run, physical-device camera, real ML Kit output and real ImageProxy lifecycle remain NOT VERIFIED. M0 remains **NOT ACCEPTED**.

## Independent QA retest and remediation — 2026-10-22

QA tested source SHA 26331bae82806ad2ed8e2041a0b2884859d28847. Push run 39725211298 and PR run 39725215996 both failed. The emulator required 7372.80 MB userdata with about 5619.80 MB available; unit-test compilation failed at PhotoLabelingPipelineTest.kt:108 due to a callback parameter-order error; lint passed with 0 errors and 26 warnings. Push artifact 12403279532: ZIP digest bc69664e5576200fed70c2be99f4378ab2c32296d3f98ac9da96fef6599a21d4, APK SHA-256 7d8091027a4dc394fcd634a6aade6684056be44b923699455fb3e0915e5e4f95, 136026771 bytes. PR artifact 12403762254: ZIP digest b06cd53c0a7e0af7e825a850bf2da43a1737863b5470c0ec4778da0dd469f5fd, APK SHA-256 ba07e8c887e2ae73af222f7a740f794a53b0a61644d04362006cecbfd8fec43f, 136026771 bytes. These results are historical for the stated SHA only.

The next candidate reclaims unused runner disk space and checks available capacity before AVD startup, fixes the test lambda, uses one authoritative ML Kit task-completion listener with a fallback waiter if listener registration throws after task creation, synchronizes outcome selection with terminal ownership, and retains secondary cleanup failures as suppressed exceptions. New regression tests were added. All changes remain PENDING exact-SHA CI verification; M0 remains NOT ACCEPTED.

## Independent QA report and consolidated remediation — 2026-10-22 — SHA 76433e45a3fa4f607f399f96c3a0e9042400fb8d

Independent QA confirmed push run 39731106118 and PR run 39731112468 both failed because the emulator action invokes its script using `/usr/bin/sh`, which rejects Bash-only `set -o pipefail`. The emulator did boot after disk cleanup, but install/launch assertions did not run. Unit tests passed 17/17; lint passed with 0 errors and 26 warnings. Push artifact 12405783806 digest `0c23a9e240a9a57841721f0af40abddaaa999cf89bd3e6c88763bd347ee1df92`, APK SHA-256 `40c4066b8b12329577618047024d859ac2f51c6eb67dda933a15558986d50b89`, 136026771 bytes. PR artifact 12405607350 digest `ff66a9f65a78ef2e9fbcf039ec1d131e5cdd252b2111c2f3ce399e1fdfd82723`, APK SHA-256 `c375d82a092325e6596be1735c4d498a3c94149775c346e5c23dc5a0b54a55cd`, 136026771 bytes. These are historical for SHA 76433e45... only.

The consolidated follow-up changes the emulator script to POSIX-compatible `set -eu`. It also replaces the fallback worker thread with an interrupt-safe wait: interruption no longer counts as proof of task termination; waiting continues until task completion/failure, and the interrupt flag is restored afterward. A unit test covers retry-after-interruption through a small injectable wait seam. Adapter registration-failure behavior and callback executor remain areas requiring additional verification; no claim of full coverage is made.

All fixes are pending fresh exact-SHA push/PR CI. M0 remains NOT ACCEPTED until smoke assertions execute and pass, lifecycle risks are independently cleared, and documentation is updated with new run evidence.


## Independent QA retest — 2026-10-22 — candidate SHA `f26c6900c2e15cc12b4feca3c0ae9af5c1a80f37`

Independent QA verdict: **NOT ACCEPTED**. Exact-source push run [39739443060](https://github.com/Navee569/Project-Shiv/actions/runs/39739443060) and PR run [39739449813](https://github.com/Navee569/Project-Shiv/actions/runs/39739449813) both failed during emulator smoke after the POSIX shell issue was corrected. The push run encountered an ADB broken pipe and `emulator-5554 offline`; the PR run hung at `adb install` until the 20-minute step timeout. Neither run verified APK installation or cold launch. Both runs passed build, 18 unit tests, and lint (0 errors, 26 warnings). Exact artifact and APK hashes are in the independent QA report; those results apply only to this SHA.

QA also found that the listener-registration fallback blocks synchronously in the CameraX main-executor path and can invoke `onFailure` twice if the callback itself throws. The follow-up candidate changes the fallback to a worker waiter and main-thread callback dispatch, adds bounded per-command ADB timeouts and device-state checks, and adds focused fallback tests. These changes are **PENDING exact-SHA CI**. Executor-rejection paths intentionally do not signal completion, to avoid premature image release; they require review because they retain ownership without completing the user flow. M0 remains **NOT ACCEPTED**.


---

## Independent QA re-verification — 2026-10-22

**Verified code revision:** `8dec101f5f399a135d9b5bfed0ae84356e8b063b`  
**Parent:** `653c4000b3d0dae98a42afc45bfab6d4489fadc7`  
**Decision:** M0 CI/emulator smoke gates PASS for this exact source revision.

Fresh workflows on the same source revision both completed successfully:

- Push: [39773119551](https://github.com/Navee569/Project-Shiv/actions/runs/39773119551) — success.
- PR #1: [39773127800](https://github.com/Navee569/Project-Shiv/actions/runs/39773127800) — success; synthetic merge SHA `ed6edc59ea716e2bdd558b042fa102df5c38af14`, distinct from the source SHA.
- PR #1 remains OPEN, DRAFT, and UNMERGED.

### Root cause and fix

The failing Ubuntu jobs explicitly reported that the runner user lacked permission to use `/dev/kvm`. The emulator action consequently launched the x86_64 guest with `-accel off`. At the stalled install snapshots, QEMU consumed about 104–105% CPU and 4.1 GiB RSS while host available memory was 601–688 MiB; guest logs showed prolonged PackageManager/system_server monitor contention and ANR/startup trouble. This is direct evidence of software-emulation resource starvation as the cause of the repeated online-but-unresponsive guest/install failure. The PR `Broken pipe` and earlier ADB-offline signatures are recorded as separate observed failures; this experiment does not prove every historical signature had one identical cause.

The workflow now enables the ephemeral runner's KVM device rule, verifies read/write access, and fails closed if KVM is unavailable. On both fresh runs the log states `KVM device access verified for runner` and `disable Linux hardware acceleration: false`. With the same API 35 image/profile and unchanged install timeout, `adb install -r` succeeded in about 20 seconds in both runs. No smoke assertion was skipped or weakened.

### Exact-SHA verification

Both runs passed all smoke gates: exactly one online emulator selected; `sys.boot_completed=1`; APK install; app data clear; force-stop; logcat clear; cold launch with `Status: ok`; nonempty process PID; expected activity; and no app crash or ANR.

The JUnit XML contains 25 tests (PhotoCaptureCompletionTest 7; PhotoLabelingPipelineTest 18), with 0 failures, 0 errors, and 0 skipped in each run. The debug lint HTML was inspected: 26 warnings, no lint errors. `NO-SOURCE` Java compile tasks are not counted as tests.

APK size was 136,026,771 bytes. Push APK SHA-256: `85aee72cc1eb16491ae5c2c356c9f0847bdd0286015b8877cf471735365de050`; PR APK SHA-256: `489861c4993d5b5b0ca94ac81eb967d27c87a79f28c8b255205f7f1bd829c66d`.

Passing run artifacts:

- Push artifact 12423747424, 73,064,024 bytes, SHA-256 `ad3a9710e9e3b941eb43c27fbe1f2c95f0017025600b852842e44b96840017cc`.
- PR artifact 12423388487, 73,064,863 bytes, SHA-256 `2fe4ec7530c518d510464bdd3f75aee93929a3442bf63ff4377854dd2d76ce69`.

Emulator serial resolution now has an executable CI test for zero, one, and multiple online devices. Photo fallback tests cover terminal callback-executor rejection via a main-thread completion executor, waiter-capacity rejection without releasing an active image, saturation/pending state, failed task propagation, callback exceptions, late completion, and exactly-once image cleanup.



## Independent QA retest — 2026-10-22 — source SHA `04175279087fba63423fecafdbe311fe568f17bc`

**Verdict: NOT ACCEPTED.** PR #1 remains open, draft, and unmerged. Source/head SHA: `04175279087fba63423fecafdbe311fe568f17bc`; PR base SHA: `96e5dd78eedb8f12cf1e2aee758364284de03276`; PR synthetic merge SHA: `98543eaa915ad2314003199f6e967352afe95822`. These identifiers are distinct.

- Push workflow [39798977912](https://github.com/Navee569/Project-Shiv/actions/runs/39798977912): APK assembly and manifest gate passed; lint passed with 0 errors and 26 warnings; unit-test Kotlin compilation failed. Push KVM, device selection, shell syntax, and emulator install/cold-launch smoke passed.
- PR workflow [39798987928](https://github.com/Navee569/Project-Shiv/actions/runs/39798987928): failed at KVM validation and unit-test compilation; downstream emulator gates were skipped. It checked out synthetic merge SHA `98543eaa915ad2314003199f6e967352afe95822`.
- Unit tests did not execute on this SHA. Compiler error: `PhotoLabelingPipelineTest.kt:394:44`, cannot infer type parameter and missing `awaitOnce`. Preserve the real-thread interrupt restoration test and ensure worker-thread assertions propagate to JUnit.
- KVM validation was inconsistent between push and PR runner instances. The PR log showed mode `0666` but still reported access failure. Remediation must capture runner identity/ACL and test an actual read-write open as the workflow user; do not bypass KVM or allow software-emulation fallback.
- Push artifact 12434808168: ZIP digest `sha256:77a5c1f3f5fb7ada2373c5dbeb134a72b7564e560368387e4b037d23ed1a5e2a`; extracted APK SHA-256 `aac421b005ad1c5620a0317ef7763c3edcffb4f08287551b6373f2789421878e`, size 136043155 bytes. Metadata matched applicationId `app.shiv`, versionCode 1, versionName `0.1.0`, minSdk 26, launcher `app.shiv.MainActivity`, label `Shiv`, and icon present. The PR artifact is separate and built from the synthetic merge SHA.
- Lint warnings: GradleDependency 15; ObsoleteSdkInt 6; Recycle 2; InlinedApi 1; OldTargetApi 1; DataExtractionRules 1.

### Remediation in progress

The interrupt test now uses `awaitOnce = { ... }` and captures any raw worker-thread failure so JUnit can fail reliably. The KVM gate now records identity, groups, device metadata and ACL where available, applies mode 0666 after udev processing, and attempts a real read-write open as the workflow user. These changes are not verified until fresh exact-SHA push and PR runs complete and their reports/artifacts are inspected.

M0 remains **NOT ACCEPTED**. No lifecycle/concurrency tests count as passing for the failed test-compilation SHA.


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

### M0 handoff state

- Documentation evidence from source SHA `790c72555aa8b71b7b28f296eef5e9030788c238` is now recorded in this file.
- The documentation-only commit created from this update must itself receive fresh push and PR workflow results; those results are not yet known at authoring time.
- Keep M0 NOT ACCEPTED until both new exact-SHA workflows pass and independent QA retests the final documentation commit.


## M1 remediation update — 2026-10-23

**Previous independently tested head:** `9c29f21738e10ff2b6589a3636832a12fe91022d` — NOT ACCEPTED.  
**Confirmed blocker:** public `ShivDao.confirmPendingEvent()` exposed internal return type `ConfirmedPendingEvent`, causing Kotlin and generated Room/KSP compilation failure.  
**Fix commit:** `67a9085eb2af7ae210fe9d4cce6889749f92ba49` changes `ConfirmedPendingEvent` to a public data class at the source API boundary. Generated KSP files were not edited manually.

**Verification state at this update:** fresh exact-SHA push and PR workflows were queued/in progress for the fix commit. No build, test, lint, APK, or emulator result is claimed here. M1 remains NOT ACCEPTED pending successful exact-SHA checks and independent retest. PR #1 remains OPEN, DRAFT, UNMERGED.

Previous M1 retest evidence:
- Push run: https://github.com/Navee569/Project-Shiv/actions/runs/39896496608 — FAILED at `:app:compileDebugKotlin`.
- PR run: https://github.com/Navee569/Project-Shiv/actions/runs/39896500019 — FAILED at `:app:compileDebugKotlin`.
- Follow-up fix commit CI: https://github.com/Navee569/Project-Shiv/actions/runs/39898022978 (push), https://github.com/Navee569/Project-Shiv/actions/runs/39898027426 (PR).


## M1 exact-SHA retest addendum — 2026-10-23

**Source SHA independently retested:** `1629273b0ce29b57118828e295225b5b98929cb8`  
**Decision for that source SHA:** CI PASS; M1 remains **BLOCKED / NOT ACCEPTED** due to remaining evidence and integration coverage obligations.

### Workflow evidence
- Push run [39900500053](https://github.com/Navee569/Project-Shiv/actions/runs/39900500053): **SUCCESS**, build/unit-tests/lint job `120240739253`.
- PR run [39900503027](https://github.com/Navee569/Project-Shiv/actions/runs/39900503027): **SUCCESS**, exact-source job `120240748770`.
- Exact-source checkout and source assertion both matched `1629273b0ce29b57118828e295225b5b98929cb8`.
- PR general job `120240748544` used synthetic merge SHA `a66b6f2912a83a46d3e5dae7a3bcf1e5b48a99a5`; it is supplementary, not exact-head proof.

### Test and lint results
Both required workflows reported 72 tests, 0 failures, 0 errors, and 0 skipped:
- ActionTierPolicyTest: 4
- BackupStreamsTest: 9
- DatabaseKeyStateTest: 3
- NotificationEventOrchestratorTest: 7
- PhotoCaptureCompletionTest: 15
- PhotoLabelingPipelineTest: 24
- PrioritizedInferenceQueueTest: 10

Lint: **0 errors, 26 warnings** — GradleDependency 15, ObsoleteSdkInt 7, Recycle 1, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1.

### APK and emulator evidence
- Push APK artifact [12479047636](https://github.com/Navee569/Project-Shiv/actions/runs/39900500053/artifacts/12479047636), 136,059,539 bytes, SHA-256 `eee7d067809890098f6105832c3e77484e12b2549c7673e2418f25d1855c3db0`.
- Exact-source PR APK artifact [12478853527](https://github.com/Navee569/Project-Shiv/actions/runs/39900503027/artifacts/12478853527), 136,059,539 bytes, SHA-256 `f14690137250c66d4e3957e10ceabbec210111121957f1982690b93a5c3c1d18`.
- Report artifacts: push [12478888490](https://github.com/Navee569/Project-Shiv/actions/runs/39900500053/artifacts/12478888490); PR [12479395583](https://github.com/Navee569/Project-Shiv/actions/runs/39900503027/artifacts/12479395583).
- Both APKs independently verified against their evidence. Metadata: `app.shiv`, versionCode 1, versionName 0.1.0, minSdk 26, launcher `app.shiv.MainActivity`, label Shiv, icon present.
- Hosted emulator smoke passed install, clear-data, strict cold launch (`Status: ok`), PID/activity, crash-buffer, and ANR assertions in push and exact-source PR jobs.
- The same source SHA produced different APK hashes at the same byte size across runners. This is a reproducibility observation, not by itself a functional failure.

### Outstanding acceptance gaps
- The tester could not perform ten separate local queue-test invocations because it had no checked-out repository/Gradle environment. CI queue tests passed, but the prior workflow only ran the queue task once per job. The CI workflow is now being updated to run ten separately logged queue-test invocations on future commits.
- Actual Android Keystore loss/corruption and preservation of existing encrypted data; Room confirmation transaction concurrency/duplicate/rollback behavior; backup restore rollback and Android ContentResolver URI write failures; production action-tier/permission enforcement; offline network capture; physical Galaxy S23 and full security testing remain **NOT RUN / unverified**.
- This report's results apply to source SHA `1629273b0ce29b57118828e295225b5b98929cb8`. The documentation/workflow update in this commit requires its own fresh push and PR CI runs.

PR #1 remains OPEN, DRAFT, and UNMERGED. Do not mark M1 accepted based only on green CI.


## M1 evidence closure follow-up — 2026-10-23 — prior tested SHA `a0a837dda149b8b75f1aabdaa189557f5f455c1b`

**Prior candidate decision remains BLOCKED / NOT ACCEPTED.** This entry preserves the independent tester's exact-SHA findings and records the remediation now committed for fresh verification. It does not claim the new workflow/documentation commit has passed CI.

### Completed evidence on prior candidate
- Push run [39902332198](https://github.com/Navee569/Project-Shiv/actions/runs/39902332198): SUCCESS; build/unit-tests/lint job `120246476726`.
- PR run [39902335066](https://github.com/Navee569/Project-Shiv/actions/runs/39902335066): SUCCESS; exact-source job `120246486875`.
- Exact-source checkout assertion: `source_sha=checked_out_sha=a0a837dda149b8b75f1aabdaa189557f5f455c1b`.
- PR general job used synthetic merge SHA `8c7b71db85a0139173396464ab82606bb62ec76e`; its artifacts are not exact-source evidence.
- Both required jobs: 72 tests, 0 failures, 0 errors, 0 skipped. Per-class counts: ActionTierPolicyTest 4; BackupStreamsTest 9; DatabaseKeyStateTest 3; NotificationEventOrchestratorTest 7; PhotoCaptureCompletionTest 15; PhotoLabelingPipelineTest 24; PrioritizedInferenceQueueTest 10.
- Lint: 0 errors, 26 warnings (GradleDependency 15, ObsoleteSdkInt 7, Recycle 1, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1).
- Push queue loop: 10/10 exit code 0; all ten individual logs inspected. Exact-source PR queue loop: 10/10 exit code 0 in the action log, but its artifact omitted the per-run logs and summary. Do not substitute synthetic-merge logs.
- Push APK artifact [12479523710](https://github.com/Navee569/Project-Shiv/actions/runs/39902332198/artifacts/12479523710): 136,059,539 bytes; SHA-256 `beb4b04b7f6c169b55f89825f4bffe87453620725ffcf784bc3f75e2614a0bc4`.
- Exact-source PR APK artifact [12480145944](https://github.com/Navee569/Project-Shiv/actions/runs/39902335066/artifacts/12480145944): 136,059,539 bytes; SHA-256 `bb5a903247db2f96aecf495f0805258b3ab9a537e176d5c9bb708680ea9cd0ca`.
- Report artifacts: push [12479777417](https://github.com/Navee569/Project-Shiv/actions/runs/39902332198/artifacts/12479777417); PR general report [12479946841](https://github.com/Navee569/Project-Shiv/actions/runs/39902335066/artifacts/12479946841). APK metadata: app.shiv, versionCode 1, versionName 0.1.0, minSdk 26, launcher app.shiv.MainActivity, label Shiv, icon present. Hashes were independently recomputed and matched evidence JSON.
- Hosted emulator smoke passed on push and exact-source PR jobs, including install, clear-data, strict cold launch Status: ok, PID/activity, crash and ANR assertions.
- Same-size APKs had different hashes; retain as a reproducibility observation unless deterministic APK output is a formal requirement.

### Remediation in this commit
- The exact-source artifact upload path now explicitly includes `app/build/reports/repeated-queue-tests/**`, in addition to the existing APK, provenance, JUnit, lint, and emulator evidence. The upload retains `if: always()` so logs can be retained after prior-step failures; missing-file behavior remains warning-only.
- `TEST_MATRIX.md` and `DEFECT_LOG.md` receive the corresponding prior-candidate evidence and evidence-retention defect.
- **Verification pending:** the current commit's push and exact-source PR workflows must complete; then inspect the exact-source artifact for the summary and exactly ten individual logs and inspect each log at the new exact source SHA.

### Remaining M1 acceptance gaps
Actual Android Keystore loss/corruption and encrypted DB preservation; Room confirmation transaction concurrency/rollback; backup restore and ContentResolver URI failures; production action-tier/permission paths; offline-only network capture; physical Samsung Galaxy S23; and full security testing remain NOT RUN / unverified. M1 remains BLOCKED / NOT ACCEPTED. PR #1 must remain open, draft, and unmerged.
