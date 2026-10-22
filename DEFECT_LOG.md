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
