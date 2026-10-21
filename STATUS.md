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
**Latest documentation/cleanup revision:** `1c80c7b5332293190dc151c69040e76b9a83c358`  
**Failed run under investigation:** [39635257720](https://github.com/Navee569/Project-Shiv/actions/runs/39635257720)  
**Fresh verification run:** Pending result retrieval; no successful build is claimed.

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
- Latest branch revision after duplicate-workflow cleanup: `1c80c7b5332293190dc151c69040e76b9a83c358`
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
