# Defect and Blocker Log

This log records confirmed defects separately from unverified risks. Do not label a suspicion as a reproduced defect.

## Open blockers

| ID | Severity | Area | Finding | Evidence | Status / next action |
|---|---|---|---|---|---|
| SHIV-M0-001 | Blocker | Build verification | The debug build, unit tests, and lint have not been executed in this development session, so baseline build health is unverified. | Repository inspection only; no Gradle execution facility exposed in this session. | Open. Run Android CI and capture actual task results. |
| SHIV-M0-002 | High | Automated tests | The inspected branch does not contain `app/src/test` or `app/src/androidTest` directories. | GitHub contents API returned 404 for both paths on the inspected branch. | Open. Add focused deterministic tests after baseline build is verified. |
| SHIV-M0-003 | High | Local AI | Presence of `Brain.kt` and a LiteRT-LM dependency does not prove the runtime can load a compatible model or infer offline. | Source and dependency declaration inspected; no inference execution performed. | Open. Verify APIs, model format, import path, and on-device offline inference. |

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
