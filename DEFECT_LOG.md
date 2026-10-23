# Defect and Blocker Log

## QA remediation retest — 2026-10-24 Asia/Calcutta

Tested correction source: `cbad02629898b623a3c758c44c3c1d7ff3151139`; parent `4a2876d4f40175a14003cbc9d0ba131efe26ff87`. This dated addendum supersedes stale current-run claims below while preserving historical evidence. It reports already observed correction runs, not future checks of this documentation commit.

**CI correction developer retest: PASS. QA-EMU-CONFIG-001 remains OPEN pending independent retest. M1 remains BLOCKED / NOT ACCEPTED.** PR #1 is open, draft and unmerged.

| Executed job | Run / job | JVM XML | Repeated queue XML | Android restore XML | Strict cold-launch wait |
|---|---|---|---|---|---|
| Push source | 40010362807 / 120608862005 | 7 suites, 80 cases, PASS | 10 fresh runs x 10 cases, PASS | All 6 named cases, PASS | 3988 ms, COLD / Status: ok |
| PR regular synthetic merge | 40010369767 / 120608886086 | 7 suites, 80 cases, PASS | 10 fresh runs x 10 cases, PASS | All 6 named cases, PASS | 3273 ms, COLD / Status: ok |
| PR exact source | 40010369767 / 120608886155 | 7 suites, 80 cases, PASS | 10 fresh runs x 10 cases, PASS | All 6 named cases, PASS | 3922 ms, COLD / Status: ok |

Both workflows are terminal SUCCESS. Every listed XML group has 0 failures, 0 errors and 0 skips; declared counts were compared with actual testcase elements. Exact source/checkout/target equal cbad02629898b623a3c758c44c3c1d7ff3151139. Synthetic PR merge ff814a2b05b6b10fc8d13f90d87b0b7bfd34d475 is recorded separately.

All three executed jobs directly prove one canonical AVD config, requested/configured cores 4, guest mask 0-3, online count 4 and processor IDs 0,1,2,3. CPU_ASSERTIONS=PASS, smoke_exit=0 and restore_exit=0 are retained. Install, clear-data, strict launch, PID/activity, mandatory logcat/crash collection and crash/ANR assertions passed. Logcat and crash-buffer command exits are 0. These are API 35 emulator results, not physical S23 results.

The six production-path Android cases cover malformed records across all collections/settings, SQLite failure at the final settings insert with exact pre-restore comparison of all ten tables, historical profile:null restore, corrupt/truncated/wrong-password input, destination open failures and destination write failure with partial-output deletion/passphrase clearing. In-memory Room rollback evidence does not establish SQLCipher/Keystore key-loss safety.

Hosted lint: 28 warnings, 0 errors in each report (GradleDependency 17, ObsoleteSdkInt 7, InlinedApi 1, OldTargetApi 1, DataExtractionRules 1, Recycle 1). The historical local 27-warning result remains historical. Loaded JSONObject/JSONArray origins are retained in JVM test output and identify json-20240303.jar. Queue summaries independently record exit_code=0, task_executed=1 and junit_valid=1 for all ten repetitions.

All five available correction archives were downloaded; every ZIP SHA-256 matches GitHub's digest and each APK hash matches its own provenance JSON. Full IDs, digests, APK hashes, checkout provenance, test counts and launch evidence are in [the durable verification record](verification/2026-10-24-qa-remediation.json). No prior hashes were reused.

### Corrections and preserved intermediate failures

- e17bfb0458265c6d7112e7fd16891cc8f239dca5 fixes QA-EMU-CONFIG-001: exact trimmed INI keys/values accept legal whitespace and reject missing/wrong/similarly named/duplicate keys; independent guest CPU checks remain mandatory. Seven Python regression tests (with positive/negative subcases) passed. One wrapper script preserves separate smoke/restore exits and runs both; four success/failure combinations passed.
- e17bfb0 push 40005971076 and PR 40005977290 remain FAILURE: CPU and strict smoke passed, but JUnit initialization failed because one expression-bodied test returned Boolean instead of void. XML contains one initialization-error case; none of the six restore behaviors executed.
- 4a2876d4f40175a14003cbc9d0ba131efe26ff87 explicitly returns Unit from coroutine test entry points. Its push 40007957043 and PR 40007965244 remain FAILURE: XML records only two cases (one pass, destination-write test failed), not six completed cases. The test APK provider process could not load kotlin.jvm.internal.Intrinsics; four cases have no recorded outcomes. An earlier developer progress inference of five passes was corrected from the XML.
- cbad026 replaces that test-only provider with a framework-only Java class under the same name/authority. Read-only descriptor failure, partial-output deletion and verification remain intact. All six cases now execute and pass. No production Android behavior or acceptance gate was weakened.
- Baseline c01caea push 40002116758 and PR 40002121898 remain failed before installation because the old regex rejected hw.cpu.ncore = 4. Baseline exact-source archive 12523142047 was downloaded, digest-verified and replayed: old regex fails, corrected parser accepts the same independent guest evidence.
- Earlier billing-admission failures, 35bfc7bb unit/boot failures, and ADB-offline logcat exit 255 in run 39936972682 / job 120359223524 remain preserved. No historical run was deleted, rerun or reclassified as PASS.

Local JDK17/Gradle8.9 validation at e17bfb0 executed --version, projects, assembleDebug, assembleDebugAndroidTest, testDebugUnitTest and lintDebug; clean build PASS, 80 JVM cases PASS, 27 local warnings/0 errors. Follow-up test APK rebuilds passed, bytecode verifies void test methods and absence of Kotlin dependencies from the standalone provider. Existing shell regressions, CPU regressions and orchestration failure cases pass. Final cbad026 exact-source hosted verification above is authoritative for this correction.

Remaining mandatory gates: physical Galaxy S23, production encryption/Keystore loss/corruption and safe recovery, production authorization/permission negative tests, offline network capture/no hidden fallback, deterministic ADB-offline fault/recovery and comprehensive security assessment. The APK's transitive INTERNET/ACCESS_NETWORK_STATE permissions and absence of network-capture proof remain documented. Green CI is not independent QA or M1 acceptance.


## Remediation checkpoint — 2026-10-23, product source 914f5eaad5aceafa8f4c68840259d0386bd1af2e

M1 NOT ACCEPTED / BLOCKED. The entries below supersede assumptions about 6880cd1d's new failing assertions while preserving all historical outcomes.

- **Fixed in code; JVM regression PASS:** JSON integer getters truncated fractions/wrapped overflow; settings parser silently defaulted/clamped malformed input. Validate exact integral values and ranges before conversion; require settings id=1 and exact Boolean type; remove defaults/clamping.
- **Fixed in code; Android retest BLOCKED:** profile:null previously became an invented default profile. Restore now preserves absence.
- **Fixed in code; Android retest BLOCKED:** JSONObject record getters could coerce field types. Validate every collection record before any transaction writes.
- **Fixed in code; Android retest BLOCKED:** destination copy failures could leave partial exports. Attempt destination cleanup and preserve cleanup errors on the original exception. SAF providers may refuse deletion and cannot guarantee atomic replacement; this remains a documented limitation.
- **Coverage added, not a behavioral PASS:** six direct Android backup tests compile, including real SQLite failure at the final settings insert and exact comparison of all ten tables after rollback. Also cover malformed records per collection, settings type failures, truncated/corrupt input, wrong password, null profile, URI open/write failures and passphrase clearing. No device execution occurred.
- **Confirmed external CI blocker:** GitHub annotations for both 6880cd1d and 914f5ea state jobs were not started due to failed account payments or spending-limit restrictions. This explains absent runners/steps/logs/artifacts; no new assertion stack trace is inferred. Owner action is required; no account financial setting was changed.
- **Confirmed fresh failures preserved:** 914f5ea push 39999347493 (job 120571083415) and PR 39999354009 (jobs 120571105892, 120571106280) are terminal FAILURE. Artifact lists empty. New always-upload steps cannot execute before a runner is admitted.
- **Local environment blocker:** no connected devices; read-only API 37 AVD launch failed because the Android Emulator hypervisor driver is missing. Physical S23 and Android restore execution remain BLOCKED.
- **Privacy acceptance gap:** locally built APK has transitive INTERNET and ACCESS_NETWORK_STATE permissions. No network-capture/no-egress proof exists; offline-only acceptance is NOT VERIFIED.
- **Other mandatory open gates:** Keystore key loss/corruption with preservation/recovery; encrypted persistence; production action-tier and unauthorized-action negatives; deterministic ADB-offline fault/recovery; comprehensive security assessment; physical S23 verification.
- **Verification bookkeeping:** final consolidated local build/app-test APK assembly/unit tests/lint passed; 80 JVM tests, no failures/errors/skips, lint 27 warnings/0 errors. Earlier local offline dependency-cache failure, Windows generated-JAR lock, and misquoted PowerShell option were diagnosed and retained rather than hidden. No test or gate was removed.

Historical 35bfc7bb141d798c1e26c1747592145b4c9f6de8 PR 39961937452 and push 39961928171 remain failed; exact-source job 120442925076 had unit-test failure and emulator boot timeout. Its synthetic merge b3ac9bcee7c59db5903b644493ebc964154551ec is not the source SHA. Historical ADB-offline run 39936972682 / job 120359223524 mandatory logcat exit 255 remains a real failure, never relabeled timeout/pass. 6880cd1d PR 39972768372 and push 39972762765 remain failed.

This report describes product source 914f5ea, not future workflow outcomes of its documentation commit. Independent QA and all mandatory evidence are still required.


This log records confirmed defects separately from unverified risks. Do not label a suspicion as a reproduced defect.

## Open blockers

| ID | Severity | Area | Finding | Evidence | Status / next action |
|---|---|---|---|---|---|
| M0-TEST-COMP-001 | Blocker | Unit-test compilation | `PhotoLabelingPipelineTest.kt:394` invokes `InterruptSafeAwait.await` without required `awaitOnce`; test compilation fails and no tests execute. | Exact-SHA push run 39798977912 and PR run 39798987928; independent QA report dated 2026-10-22. | Remediation prepared: use the named argument and propagate worker-thread failures to JUnit. Keep open until fresh exact-SHA test task passes. |
| M0-CI-KVM-001 | High | Emulator CI | KVM permission check is inconsistent across push and PR hosted-runner instances. PR reported inaccessible KVM although `ls -l` showed mode 0666. | Push run 39798977912 passed KVM/smoke; PR run 39798987928 failed KVM and skipped downstream emulator checks. | Remediation prepared: diagnostic identity/ACL output and actual read-write open as workflow user. Keep open until both event runs pass without software fallback. |
| M0-COVERAGE-001 | High | Photo lifecycle tests | Lifecycle/concurrency behavior is unverified while test-source compilation fails. | No unit tests executed on source SHA `04175279087fba63423fecafdbe311fe568f17bc`. | Keep open until full suite compiles/passes and adapter races/terminality cases are independently reviewed. |
| M0-DOC-001 | Medium | Verification records | Historical docs did not reflect the latest exact-SHA failures. | Independent QA report for SHA `04175279087fba63423fecafdbe311fe568f17bc`. | Latest exact-SHA results added to STATUS.md, TEST_MATRIX.md and DEFECT_LOG.md; refresh after remediation workflows complete. |
| SHIV-M0-003 | High | Local AI | Presence of `Brain.kt` and a LiteRT-LM dependency does not prove compatible model loading or offline inference. | Source/dependency inspection only; no real inference execution. | Open. Verify model/runtime compatibility and on-device offline inference. |

## Confirmed defects

No code defect has yet been reproduced by executing the application in this session. Do not interpret this as proof that no defects exist.

## Closure policy

A defect can be closed only when its root cause, fix, regression test (where applicable), and verification evidence are recorded. Include the source commit and CI run or device-test evidence.
## Independent QA retest — SHA 26331bae82806ad2ed8e2041a0b2884859d28847

Confirmed failures: API 35 emulator userdata required 7372.80 MB with about 5619.80 MB available; unit tests failed compilation at PhotoLabelingPipelineTest.kt:108; lint passed with 0 errors and 26 warnings. The photo pipeline also had a listener-registration/image-lifetime risk, a terminal outcome race, and discarded secondary cleanup failures. The consolidated remediation is committed after this tested SHA; all fixes require fresh exact-SHA verification before defect closure. M0 remains NOT ACCEPTED.

## Independent QA report — SHA 76433e45a3fa4f607f399f96c3a0e9042400fb8d

Confirmed findings: both workflows failed before APK installation because `/usr/bin/sh` rejected `set -o pipefail`; JUnit passed 17/17 and lint passed with 0 errors/26 warnings. The fallback waiter could treat InterruptedException as task termination and release the ImageProxy early; worker-start failure was also unsafe. Adapter/fallback and deterministic concurrency tests were missing. Documentation was stale after workflows completed.

Consolidated follow-up fixes the POSIX shell mismatch and changes fallback waiting to retry interruptions until the task is terminal, restoring the interrupt flag afterward. Remaining adapter/concurrency coverage and exact-SHA CI are still required; M0 remains NOT ACCEPTED.


## Independent QA retest — 2026-10-22 — candidate SHA `f26c6900c2e15cc12b4feca3c0ae9af5c1a80f37`

Confirmed findings from exact-SHA push run 39739443060 and PR run 39739449813:
- D-001 original POSIX shell incompatibility: fixed in source; old pipefail error did not recur.
- D-006 BLOCKER: emulator smoke still failed before install/cold launch. Push run ADB broken pipe/offline; PR run adb install timed out.
- D-007 MEDIUM: ADB smoke commands lacked individual timeouts and clear device-state validation.
- D-002 HIGH: synchronous ML Kit listener-registration fallback can block the main/UI thread.
- D-003 MEDIUM: fallback catch scope can reclassify a consumer callback exception and call onFailure twice.
- D-004 HIGH/coverage: adapter-level listener-registration fallback and deterministic concurrency coverage are insufficient.
- D-005 MEDIUM: status/test/defect docs became stale after the exact-SHA workflows completed.

A follow-up candidate adds bounded ADB command execution and focused fallback scheduling/callback tests, moves waiting off the caller thread, and dispatches fallback callbacks to the main executor. All fixes are PENDING exact-SHA workflow and independent retest. M0 remains NOT ACCEPTED.


---

## Incident closure update — 2026-10-22

- **D-001 ADB install hang — RESOLVED for the current workflow configuration and verified source revision `8dec101f5f399a135d9b5bfed0ae84356e8b063b`.** Root cause confidence: high. The Ubuntu runner could not access `/dev/kvm`, so android-emulator-runner disabled VM acceleration and launched QEMU with `-accel off`. Live snapshots showed high QEMU CPU/RSS, low host available memory, and guest PackageManager/system_server contention while install was stuck. A workflow step now enables and verifies access to the runner's ephemeral KVM device, then fails rather than silently starting software emulation. Push and PR runs 39773119551 and 39773127800 passed the full smoke sequence with hardware acceleration enabled.
- **D-002 / D-003 photo fallback scheduling and callback behavior — addressed and covered.** Callback executor rejection falls back to a separate main-thread completion executor; waiter-capacity rejection does not finish/release an image; tests verify task terminality, callback exceptions, late completion, saturation, and exactly-once cleanup. Both exact-SHA workflows passed 18 PhotoLabelingPipeline and 7 PhotoCaptureCompletion tests.
- **Historical signatures remain distinct.** Earlier device-offline periods and PR install `Broken pipe (32)` are reported as observations, not asserted to be identical to the install-timeout root cause. Intermediary cancelled workflows remain cancelled.

Current exact-run artifacts and APK hashes are recorded in STATUS.md and TEST_MATRIX.md. PR #1 remains open, draft, and unmerged.


## Independent QA retest — 2026-10-22 — SHA `04175279087fba63423fecafdbe311fe568f17bc`

- **M0-TEST-COMP-001 — BLOCKER:** test-source compilation fails at `PhotoLabelingPipelineTest.kt:394:44` because `awaitOnce` is omitted. Preserve the real-thread interrupt-restoration test and surface worker failures.
- **M0-CI-KVM-001 — HIGH:** KVM passes on push but fails on PR despite displayed mode 0666. Add runner identity/ACL diagnostics and verify actual read-write opening as the workflow user. Never skip KVM or silently fall back to software emulation.
- **M0-COVERAGE-001 — HIGH:** lifecycle/concurrency tests are unverified because compilation failed before test execution.
- **M0-DOC-001 — MEDIUM:** current exact-SHA evidence has been recorded, but the three milestone documents must be refreshed after the new push and PR runs.

M0 remains **NOT ACCEPTED** until all required gates pass on the same new source SHA and independent QA verifies both workflows and artifacts.


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


## Open findings from SHA `0fe5aea72f9841eb5466c878b90ac8fbb601df93`

| ID | Severity | Finding | Closure evidence |
|---|---|---|---|
| M0-EMULATOR-COLDSTART-001 | Blocker | Push cold launch returned `Status: timeout`; PR run passed. | Both event workflows pass strict launch, PID, activity, crash and ANR assertions on the same new SHA. |
| M0-ARTIFACT-PROVENANCE-001 | High | Tested artifacts omitted the APK binary despite recorded hash and size. | Download actual exact-run APK and independently recompute hash, size and manifest. |
| M0-COVERAGE-002 | High | Lifecycle/concurrency matrix is only partially proven by 34 tests. | Deterministic adapter/pipeline race, ownership, stale-generation and worker-failure tests. |
| M0-DOC-EVIDENCE-001 | Medium | Prior docs did not clearly show the push FAIL / PR PASS split and APK omission. | This dated section records exact evidence; add fresh run IDs after CI completes. |

M0 remains NOT ACCEPTED. Do not merge or close PR #1.

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

## Open defects confirmed on SHA `e7cfd4dbee8e1c26fa844d8bd69e38faa0b5137a`

| ID | Severity | Finding | Closure criteria |
|---|---|---|---|
| M0-SHELL-SYNTAX-001 | Blocker | Smoke script has an unmatched quote and malformed duplicated launch-gate structure; both event workflows fail syntax validation. | All three required `bash -n` checks pass and the smoke actually executes in both push and PR workflows. |
| M0-ANDROID-SMOKE-001 | Blocker | Runtime smoke was skipped; cold launch has no result on this SHA. | Both workflows pass strict `Status: ok`, PID, expected activity/top activity, fresh crash-buffer and ANR assertions. |
| M0-DOC-EVIDENCE-002 | Medium | Documentation lacked this SHA's actual test/run/artifact evidence. | This dated section records exact previous run IDs and artifact provenance; append new exact-SHA results after CI. |

Do not close these defects before their stated criteria pass. M0 remains NOT ACCEPTED.


## Independent M0 retest disposition — 2026-10-22

Retested source SHA: `790c72555aa8b71b7b28f296eef5e9030788c238`. Push run [39812687992](https://github.com/Navee569/Project-Shiv/actions/runs/39812687992) and PR run [39812695904](https://github.com/Navee569/Project-Shiv/actions/runs/39812695904) both **SUCCESS**. The PR source SHA is `790c72555aa8b71b7b28f296eef5e9030788c238`; the PR workflow checked out synthetic merge SHA `fcc8b773f5a8e63e35ca4a647b7aaa59fa9d2543` (base SHA `96e5dd78eedb8f12cf1e2aee758364284de03276`).

### Defects closed on the tested candidate, with evidence

| ID | Status on `790c72555aa8b71b7b28f296eef5e9030788c238` | Closure evidence |
|---|---|---|
| M0-SHELL-SYNTAX-001 | **CLOSED for this candidate** | All three required `bash -n` checks passed in both push and PR workflows; unmatched quote no longer present. |
| M0-ANDROID-SMOKE-001 | **CLOSED for this candidate** | Both emulator smoke steps executed and passed strict cold launch, PID, activity/top-activity, fresh crash/ANR and summary assertions. See run-specific metrics and artifact hashes in the dated retest section above. |
| M0-DOC-EVIDENCE-003 | **Addressed for tested SHA; final verification pending** | This documentation update records the current tested SHA, both run IDs, exact artifact IDs/hashes, test/lint results, and runtime results. Fresh CI for the resulting documentation commit is still required. |

Historical failed-run entries above are retained unchanged; closure here applies only to the stated candidate and does not erase historical failures. Do not close unrelated product/lifecycle/security defects. Physical Samsung Galaxy S23 and Android Studio run-configuration checks remain unverified.

### Current M0 decision

**NOT ACCEPTED pending** successful push and PR workflows on the new documentation commit SHA and independent QA retest. Do not merge or close PR #1; it must remain open, draft, and unmerged.


## M1 blockers from independent retest — 2026-10-23

| ID | Severity | Area | Finding | Evidence | Status / next action |
|---|---|---|---|---|---|
| M1-COMP-DAO-001 | Blocker | Kotlin visibility / Room KSP | Public `ShivDao.confirmPendingEvent()` exposed internal `ConfirmedPendingEvent`; compilation failed in source and generated DAO. | Push run 39896496608 and PR run 39896500019 on SHA `9c29f21738e10ff2b6589a3636832a12fe91022d`. | Source fix committed as `67a9085eb2af7ae210fe9d4cce6889749f92ba49` by making the result type public. Await exact-SHA compile, tests, lint and emulator evidence; do not close until verified. |
| M1-QUEUE-UNVERIFIED-001 | High | Inference queue tests | Queue tests, including priority/FIFO and close/capacity races, were not executed because compilation failed. | Independent M1 retest report for SHA `9c29f21738c2c0ddd0ae747bd408da6890d9a284`. | Run queue-only test first, then full suite on a successful exact SHA; review JUnit XML and assertion outcomes. |
| M1-DATA-UNVERIFIED-001 | High | Backup, Keystore, Room, action tiers | Data-integrity and Android integration paths remain unverified; source inspection is not behavioral proof. | Independent M1 retest report dated 2026-10-23. | Add/run relevant failure-injection and Android integration tests; document NOT RUN/BLOCKED areas honestly. |
| M1-DOC-STALE-001 | Medium | QA documentation | Previous documents reflected older M0 history and did not record current M1 blocker. | Independent M1 retest report dated 2026-10-23. | This addendum records blocker and fix commit. Refresh exact tested SHA, workflow results, test counts, lint counts, APK checksum and emulator evidence after successful runs. |


## M1 retest disposition — 2026-10-23

| ID | Severity | Area | Current evidence | Status / next action |
|---|---|---|---|---|
| M1-QUEUE-ORDER-TEST-001 | High | Priority/FIFO queue test setup race | Fixed on SHA `1629273b0ce29b57118828e295225b5b98929cb8`; push run 39900500053 and PR exact-source run 39900503027 passed; exact assertion remains `active, high-1, high-2, middle, low`. | CI passed once per event workflow. Add repeated separately logged runs in CI; do not claim ten local runs were performed. |
| M1-COMP-DAO-001 | Resolved for tested SHA | Kotlin / Room KSP visibility | Public `ConfirmedPendingEvent` compiled and debug APK assembled on the exact tested SHA. | Fix verified by compilation on that candidate; keep historical failure record. |
| M1-DATA-UNVERIFIED-001 | High / acceptance blocker | Keystore, Room transaction, backup/restore, production action-tier behavior | JVM helper tests pass, but actual platform/data-integrity failure injection is not evidenced. | Add targeted tests at appropriate Android integration layer or explicitly scope as a release blocker; never label PASS from source review alone. |
| M1-DOC-STALE-001 | Medium | QA evidence freshness | This addendum records completed CI evidence for source SHA `1629273b0ce29b57118828e295225b5b98929cb8`. | The current documentation/workflow commit still requires its own fresh push and PR workflows. |
| M1-APK-REPRO-001 | Low / investigate if required | APK reproducibility | Same source SHA and same APK size but distinct verified SHA-256 across push and exact-source PR runners. | Not proven functional defect. Confirm whether byte-for-byte reproducibility is a project requirement; document or investigate accordingly. |

### Completed evidence for source SHA 1629273b0ce29b57118828e295225b5b98929cb8
- Push run 39900500053: SUCCESS; job 120240739253.
- PR run 39900503027: SUCCESS; exact-source job 120240748770. PR general job 120240748544 used synthetic merge SHA `a66b6f2912a83a46d3e5dae7a3bcf1e5b48a99a5`.
- Both required workflows: 72 tests, 0 failures, 0 errors, 0 skipped; lint 0 errors / 26 warnings.
- Push APK artifact 12479047636: 136,059,539 bytes, SHA-256 `eee7d067809890098f6105832c3e77484e12b2549c7673e2418f25d1855c3db0`.
- Exact-source PR APK artifact 12478853527: 136,059,539 bytes, SHA-256 `f14690137250c66d4e3957e10ceabbec210111121957f1982690b93a5c3c1d18`.
- Report artifacts: 12478888490 (push) and 12479395583 (PR).
- Strict hosted emulator smoke passed in both required workflows.

Historical failed-run entries are retained. No physical device, real Keystore fault injection, Room rollback integration, backup URI failure integration, offline network capture, or full security testing is claimed. M1 remains BLOCKED / NOT ACCEPTED.


## M1 exact-source evidence-retention defect — 2026-10-23

| ID | Severity | Area | Evidence on prior SHA `a0a837dda149b8b75f1aabdaa189557f5f455c1b` | Remediation / closure criteria |
|---|---|---|---|---|
| M1-QUEUE-ARTIFACT-001 | High | Exact-source PR CI artifact | PR run 39902335066, exact-source job 120246486875, recorded ten queue invocations at the exact source SHA with exit_code=0, but artifact 12480145944 omitted `repeated-queue-summary.txt` and `run-01.log` through `run-10.log`. Artifact 12479946841 contains logs from synthetic merge SHA `8c7b71db85a0139173396464ab82606bb62ec76e` and cannot substitute. | Workflow now includes `app/build/reports/repeated-queue-tests/**` in the exact-source upload path. **Open until fresh exact-source CI uploads the summary and exactly ten logs, all ten are inspected, each invocation records the new exact SHA, and every exit code is zero.** |
| M1-DOC-FRESHNESS-002 | Medium | QA evidence freshness | Independent retest found STATUS.md, TEST_MATRIX.md and DEFECT_LOG.md did not record run IDs 39902332198 / 39902335066 and their findings. | This addendum records prior-candidate results and limitations. **Fresh verification of this documentation/workflow commit remains pending.** |
| M1-DATA-UNVERIFIED-001 | High / acceptance blocker | Android/data integrity and security | Actual Keystore fault injection, Room transaction/rollback integration, backup URI failure/restore rollback, production action-tier entry points, offline network capture, physical Galaxy S23 and full security testing remain unverified. | Add/run appropriate platform-level tests or retain as blockers according to project-owner criteria. Never claim PASS from source review or green JVM CI alone. |

Prior-candidate CI summary: push run 39902332198 and PR run 39902335066 both succeeded; each required workflow had 72 tests, 0 failures/errors/skips, and lint 0 errors / 26 warnings. Both hosted emulator smoke checks passed. Push queue logs 10/10 individually passed; exact-source queue execution reported 10/10 zero exit codes but logs were not retained in its artifact. APK metadata and SHA-256 values are recorded in STATUS.md. M1 remains BLOCKED / NOT ACCEPTED until evidence-retention closure and broader acceptance requirements are addressed.


## M1 QA remediation status — 2026-10-23 — exact-source run 39923689666

| ID | Severity | Updated finding | Status / closure criteria |
|---|---|---|---|
| M1-DOC-FRESHNESS-002 | Medium | STATUS.md, TEST_MATRIX.md, and DEFECT_LOG.md did not record run 39923689666 or source SHA `966aa26ab63bf72b15848496db5b3930e377670e`. Dated evidence addenda are now being added to all three documents. | **OPEN pending** fresh push and exact-source PR workflows for the resulting documentation commit, plus independent re-fetch confirming the three documents agree. Preserve all prior history. |
| M1-QUEUE-ARTIFACT-001 | High | Exact-source artifact 12488143989 was inspected for source SHA `966aa26ab63bf72b15848496db5b3930e377670e`; it retained the summary, run-01.log through run-10.log, and ten corresponding JUnit XML reports. All ten runs were reported valid and passing. | **Resolved for candidate SHA `966aa26ab63bf72b15848496db5b3930e377670e` only**, subject to fresh independent verification on the new documentation commit. Do not erase the prior candidate's artifact-retention failure. |
| M1-DATA-UNVERIFIED-001 | High / acceptance blocker | Keystore fault injection, Room transaction/rollback integration, backup URI write failure and restore rollback, production action-tier/permission end-to-end behavior, offline network capture, physical Galaxy S23, and comprehensive security assessment remain unverified. | **OPEN — acceptance blocker.** Add and execute appropriate platform-level tests or retain as blockers. Never close based on source review, JVM CI, APK build, or hosted emulator alone. |
| M1-APK-REPRO-001 | Low / investigate if required | Regular and exact-source APKs were each 136,059,539 bytes but had different SHA-256 values; regular job used merge SHA `79a4b7623b67f1ca0acb09887e0812d1fd3b240f`, exact-source job used `966aa26ab63bf72b15848496db5b3930e377670e`. | **Investigate only if byte-for-byte reproducibility is a formal requirement.** Different source trees mean the hash difference alone does not establish a defect. |
| M1-REGULAR-SHA-001 | Medium / evidence classification | Regular job queue results were stamped with merge SHA, not PR head SHA. | Keep regular job evidence separate; exact-head queue acceptance relies on exact-source job 120315249653 and artifact 12488143989. |

### Evidence recorded for run 39923689666

- Run: https://github.com/Navee569/Project-Shiv/actions/runs/39923689666
- Regular job 120315249339: SUCCESS on merge SHA `79a4b7623b67f1ca0acb09887e0812d1fd3b240f`.
- Exact-source job 120315249653: SUCCESS; source and checkout SHA `966aa26ab63bf72b15848496db5b3930e377670e`.
- Exact-source queue artifact 12488143989; verification report artifact 12489255574.
- Ten exact-source queue repetitions: each exit code 0, 10 tests, 0 failures/errors/skips, target task executed, JUnit validated.
- Full suite 72/72; lint 0 errors / 26 warnings; hosted emulator smoke PASS (not physical-device evidence).
- Exact-source APK SHA-256 `5eba4bbb68e164e9ab06de98d3a4627efaa10f953743029e5508081056630278`, size 136,059,539 bytes.

M1 remains **BLOCKED / NOT ACCEPTED**. PR #1 remains open, draft, and unmerged.


## M1 emulator cold-launch timeout — 2026-10-23

| ID | Severity | Finding on candidate SHA `9ef8f73be7a522c62cd7623da299d4151ce2e7e3` | Status / remediation |
|---|---|---|---|
| M1-EMU-STARTUP-001 | High / PR gate failure | PR run [39932808366](https://github.com/Navee569/Project-Shiv/actions/runs/39932808366), regular job 120344972695 failed strict hosted emulator launch on synthetic merge SHA `ce54c6186c52c1274055a991c2e6ff68039a3ff1`: `Status: timeout`, `LaunchState: UNKNOWN (-1)`, WaitTime 13738 ms. Exact-source job 120344972366 passed on the actual PR head. | **OPEN pending fresh CI and diagnosis.** Diagnostics: app PID 2601 existed; MainActivity was RESUMED but `reportedDrawn=false` / `firstWindowDrawn=false`; crash buffer empty; logcat showed skipped frames/system load; host memory about 3.0 GiB available and 2.2 GiB swap used. Emulator warned that 4 cores would run more smoothly. This supports but does not prove resource contention/startup timing. Workflow now requests 4 cores in both jobs; strict `Status: ok` assertion remains mandatory. Fresh push and PR workflows on the final docs commit must confirm whether the failure recurs. Do not close on exact-source success alone. |
| M1-DOC-FRESHNESS-002 | Medium | Previous milestone documents lacked the 39932801082 / 39932808366 outcomes and retained emulator diagnostics. | **OPEN pending** fresh workflows on the final documentation commit and independent re-fetch confirming all three documents match the final SHA and results. |
| M1-DATA-UNVERIFIED-001 | High / acceptance blocker | Keystore fault injection, Room transaction/rollback integration, backup URI write failure/restore rollback, production action-tier/permission denial, offline network capture, physical Samsung Galaxy S23, and comprehensive security assessment remain unverified. | **OPEN — acceptance blocker.** Never close based on source review, JVM CI, APK build, or hosted emulator smoke. |
| M1-QUEUE-ARTIFACT-001 | High | Exact-source artifact 12493092228 for candidate SHA retained summary, ten run logs, and ten per-run queue JUnit XML files. Each log/report pair records the target SHA, exit 0, 10 tests, 0 failures/errors/skips, task executed and valid JUnit. | **PASS for candidate SHA only**, subject to fresh verification on final documentation commit. Historical prior artifact-retention defect remains in history. |

### Evidence links and results

- Push run [39932801082](https://github.com/Navee569/Project-Shiv/actions/runs/39932801082): SUCCESS; main job 120344951189. Exact-source PR job was skipped because the event was push.
- PR run [39932808366](https://github.com/Navee569/Project-Shiv/actions/runs/39932808366): FAILURE overall; exact-source job 120344972366 SUCCESS; regular merge-result job 120344972695 FAILURE.
- Exact-source APK artifact [12493092228](https://github.com/Navee569/Project-Shiv/actions/runs/39932808366/artifacts/12493092228), archive SHA-256 `36a4bb1b4d87290f5d6dbdb24e910b4e151f383fa3d40c0303015f62e9903cd1`; APK 136,059,539 bytes, SHA-256 `9e0916efe83941af90eeb3a3dd8fac561d026904b2999690789bb2ef3d6a01c8`.
- Report artifact [12492718116](https://github.com/Navee569/Project-Shiv/actions/runs/39932808366/artifacts/12492718116), archive SHA-256 `2f1dcbbc37f993eeb67a8bf29573156e6122ae7ba6c379460e8caec5a069fdf6`.
- Full unit suite 72 tests, zero failures/errors/skips; lint 0 errors / 26 warnings; exact-source ten queue runs all passed.
- Workflow adjustment commit `07a2e5ff1a5123b87d473c99c39069efe6f0db79` increases emulator CPU cores to four in both jobs. It is not considered verified until fresh runs complete on the final documentation SHA.

M1 remains **BLOCKED / NOT ACCEPTED**; PR #1 must remain open, draft, and unmerged.


## M1 regular PR smoke exit 255 / ADB offline transport — 2026-10-23

| ID | Severity | Evidence / finding | Status / closure criteria |
|---|---|---|---|
| M1-EMU-ADB-OFFLINE-001 | High / PR gate failure | PR run 39936972682, regular job 120359223524 on synthetic merge SHA `0714eb753b06caac376b94d4e9f73a36329ff101`, failed after strict launch passed. Exact failing command: `adb -s emulator-5554 logcat -b all -d -v threadtime -t 6000`, exit 255. Artifact 12495271137 records `emulator-5554 offline transport_id:2` in boot diagnostics. Previous commands had passed; activity top output showed MainActivity. | **OPEN pending fresh CI.** Commit `86a9cd59c99e9c75fcbfb428ac61f75319a39c2e` moves mandatory logcat/crash-buffer capture ahead of heavier dumpsys activity snapshots and corrects status wording so exit 255 is not labelled as a timeout. Log capture remains mandatory; any failure remains a failure. Fresh PR run must establish whether the transport failure recurs. Current evidence does not prove why ADB went offline. |
| M1-EMU-CORES-001 | Medium | Workflow input and action setup report `cores: 4` / `Cores: 4`, but emulator emitted warning that AVD test runs with 2 CPU cores. Prior failure artifact did not preserve AVD `config.ini`. | **OPEN / NOT VERIFIED.** Commit `b0c6ffa275c3d9a950dc445b7e69bb5dd88b8167` records actual `hw.cpu.ncore` and related properties from AVD config in failure diagnostics. Inspect fresh artifact before asserting effective four-core configuration. |
| M1-DOC-FRESHNESS-002 | Medium | Tester found STATUS.md, TEST_MATRIX.md and DEFECT_LOG.md omitted push run 39936964872 and PR run 39936972682. | Addenda now record these outcomes and the precise logcat failure. **OPEN pending** fresh push/PR workflows on final docs SHA and independent re-fetch/consistency review. |
| M1-DATA-UNVERIFIED-001 | High / acceptance blocker | Keystore fault injection, Room transaction/rollback, backup URI/malformed backup/restore rollback, production action-tier/permission denial, offline-only network capture, physical Galaxy S23 and comprehensive security assessment remain unverified. | **OPEN — acceptance blocker.** Direct platform/security evidence required; no closure from JVM CI or hosted emulator smoke. |

### Candidate run evidence

- Push [39936964872](https://github.com/Navee569/Project-Shiv/actions/runs/39936964872): SUCCESS, push event, job 120359239132 success. Push APK artifact 12494647410, ZIP SHA-256 `59f9ddf2562a55b5b6162763d9f53339aac9a1a8ec8ec86156b612b42f79bdae`; APK 136,059,539 bytes, SHA-256 `c0920d63266555cd6b5c322bfea913a03d230e97278fcf033befbcfed648def1`.
- PR [39936972682](https://github.com/Navee569/Project-Shiv/actions/runs/39936972682): overall FAILURE; exact-source job 120359223934 success; regular merge-result job 120359223524 failure on synthetic SHA `0714eb753b06caac376b94d4e9f73a36329ff101`.
- Exact-source artifact 12495072068 ZIP SHA-256 `fdde00e25d85ee5c8fe22d8bf70583cfb4cbec780be2f3b2db25d0cae6f926d9`; APK 136,059,539 bytes, SHA-256 `c5c36c9808478ccad045f1e3d50866c5296dd3e23a368c78374589abacb06ff9`.
- Report artifact 12495271137 ZIP SHA-256 `857882b62554bcfd863a4c18a3f9f7385b1c87fb7e7f18fbb693a357475a2507`.
- Exact-source suite: 72 tests, 0 failures/errors/skips; queue 10/10 valid; lint 0 errors / 26 warnings. Exact-source cold launch Status: ok, LaunchState COLD, WaitTime 12757 ms. Regular merge-result cold launch Status: ok, LaunchState COLD, WaitTime 10082 ms, but subsequent logcat exit 255 makes the overall job FAIL.

M1 remains **BLOCKED / NOT ACCEPTED**. PR #1 must remain OPEN, DRAFT, and UNMERGED.


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
