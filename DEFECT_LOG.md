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
