# Defect and Blocker Log

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
