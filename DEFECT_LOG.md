# Defect log

| ID | Severity | Status | Fix | Regression coverage |
|---|---|---|---|---|
| M0-001 | S2 | CLOSED — tester PASS | Require a listed eventId in Brain notification arguments; dispatch and account for that exact event. Reject missing/unknown identities. | alertForBAccountsForBAndCannotRepeatB; missingAndUnknownEventIdsCannotDispatch |
| M0-002 | S2 | CLOSED — tester PASS | Room transactions serialize final cap/deduplication checks and a durable reservation before Android dispatch. Reservations count toward cap/deduplication. | overlappingWorkersWithOneDailySlotSendExactlyOneAlert; overlappingWorkersForSameEventSendExactlyOnce; failureAfterDispatchKeepsReservationAndPreventsRetryDuplicate |
| M0-003 | S2 | CLOSED — tester PASS | After inference and again immediately before dispatch, read current settings, tier, permission, event contents and time. Cancel disabled, quiet, expired, deleted or edited events. | disablingProactiveDuringInferenceCancelsAlert; enteringQuietHoursDuringInferenceCancelsAlert; deletingEventDuringInferenceCancelsAlert; expiringEventDuringInferenceCancelsAlert; editingEventDuringInferenceCancelsAlert; revokingPermissionDuringInferenceCancelsAlert; requiringConfirmationDuringInferenceCancelsAlert |
| M0-004 | S2 | CLOSED — tester PASS | SQL excludes delivered/reserved events before applying LIMIT 8. | firstEightSentEventsDoNotHideNinthUnsentEvent |
| M1-001 | S2 | CLOSED — tester PASS | Validate allowlisted action shapes and compare event targets with injected current time on every authorization, including after confirmation; expired targets do not reach event write/scheduling paths. | eventTargetsMustBeStrictlyInTheFutureAtAuthorizationTime; eventThatExpiresWhileAwaitingConfirmationIsRejectedWithoutWritesOrDispatch; rejectedEventAuthorizationDoesNotInvokeWriteOrDispatchCallbacks |
| M1-002 | S2 | CLOSED — tester PASS | Preserve SET_REMINDER minutes in inclusive range 0..1440. | reminderMinuteBoundariesRemainSupported |
| M1-001 (reopened) | S2 | CLOSED — tester PASS | Reauthorize the exact proposal against the injected clock after STARTED audit persistence returns and immediately before dispatch; record a denied final outcome without invoking the effect. | eventThatExpiresWhileStartedAuditWriteIsSuspendedIsRejected |
| M1-003 | S2 | CLOSED — tester PASS | Claim confirmation synchronously before launching the coroutine; only one overlapping confirmation can execute the pending action. | ActionConfirmationGateTest.claimIsExclusiveUntilReleased |
| M1-004 | S2 | CLOSED — tester PASS | Treat missing provider cursor as failure, replace imported rows transactionally, and report event/reminder/audit partial outcomes accurately. | CalendarSyncerTest null/failed source, persistence failure, reminder failure; RoomShivViewRepositoryTest replacement isolation |
| M1-005 | S2 | CLOSED — tester PASS | Restore old manifest component FQCNs and compatibility subclasses for persisted worker/receiver/service/activity identities; schedule proactive work using the legacy worker name. | LegacyComponentCompatibilityTest manifest registration and both worker constructors/superclasses |
| M2-001 | S2 | CLOSED — tester PASS | Hoist unsent chat and profile drafts into the saveable shell state above conditional destinations; initialize profile drafts from persisted profile only once. | ShivNavigationTest actual shell navigation, unsent chat draft, profile draft and saved-state restoration |
| M2-002 | S2 | CLOSED — tester PASS | Observe permission status on lifecycle resume in Settings and Permission explanations; combine app notification enablement on all supported APIs with the Android 13+ runtime permission. | ShivNavigationTest resume refresh and API 28/API 33 notification capability boundaries |
| M1-006 | S2 | CLOSED — tester PASS | Claim each pending event ID synchronously before coroutine launch; Save and Ignore share the claim, and stale candidates are checked against current storage. | ConfirmationRaceTest.doubleEventSaveCannotDispatchWhileFirstAuditIsSuspended |
| M1-007 | S2 | CLOSED — tester PASS | Atomically claim and remove a pending action before audit suspension; Cancel can log only an unclaimed action. | ConfirmationRaceTest.cancelAfterConfirmClaimCannotRecordCancelOrStopCapturedDispatch; cancelBeforeClaimPreventsConfirmation |
| M1-008 | S2 | CLOSED — tester PASS | Defer encrypted repository construction and operations to Dispatchers.IO, keeping SQLCipher/Keystore initialization out of ViewModel construction. | AsyncShivViewRepositoryTest.slowDatabaseCreationDoesNotBlockViewModelConstructionOrCallerThread |
| M2-003 | S2 | CLOSED — tester PASS | Track profile name and notes edits separately in saveable state; delayed profile hydration fills only untouched fields. | ShivNavigationTest.lateProfileLoadPreservesEditsAndFillsOnlyUntouchedField |
| M2-004 | S3 | CLOSED — tester PASS | Render tier badges as descriptive, non-interactive surfaces. | ShivNavigationTest.tierBadgeIsDescriptiveAndHasNoClickAction |
| M1-009 | S2 | FIXED — awaiting retest | Preserve listening candidates when the action record fails before dispatch; clear them after attempted or completed dispatch to avoid uncertain retries, and show the failure in chat. | PendingEventConfirmerTest failed STARTED audit, failed final audit, and attempted dispatch failure |

M0 verification: 13 worker-level JVM tests use actual CoroutineWorker.doWork, real in-memory Room queries/transactions and fake Brain/notification ports. The narrow suite and final assembleDebug, testDebugUnitTest (16 tests total) and lintDebug passed locally. These tests use synthetic data and do not change production SQLCipher initialization. No device testing was performed; notification delivery uses a fake port.

M0 limit: a crash or uncertain dispatch/commit failure retains the durable reservation. It consumes a daily slot and suppresses that event to avoid a possible duplicate, even when delivery cannot be confirmed. A definite no-delivery result releases the reservation.

| Fix commit | CI run | Result |
|---|---|---|
| b6b533fec67217da7846bce5863ad9ddcee520fc | [40044537471](https://github.com/Navee569/Project-Shiv/actions/runs/40044537471) | PASS: assembleDebug, testDebugUnitTest, lintDebug |

Independent retest: PASS on commit 4838d058f6c6ba3c7c703aa9308ccd5ab09879e4. Tester confirmed M0-001 through M0-004 and reported no additional material defects in scope. Later milestones were not reviewed.

| Tested commit | CI run | Result |
|---|---|---|
| 4838d058f6c6ba3c7c703aa9308ccd5ab09879e4 | [40045079241](https://github.com/Navee569/Project-Shiv/actions/runs/40045079241) | PASS: assembleDebug, testDebugUnitTest, lintDebug |

M1 independent retest: PASS on commit 2bfeb8cb999278221eede2c7c54085bf2c1b74f1. Tester closed M1-001, M1-003, and M1-004 through M1-005; no additional material defects were found. M1 architecture acceptance is complete. M3 persistence verification, M4 queue requirements, and release acceptance remain separate.

| Tested commit | CI run | Result |
|---|---|---|
| 2bfeb8cb999278221eede2c7c54085bf2c1b74f1 | [40063996960](https://github.com/Navee569/Project-Shiv/actions/runs/40063996960) | PASS: assembleDebug, testDebugUnitTest, lintDebug |

M2 independent retest: PASS on commit adfa2b269426a267d1181a41e69f708b2223011b. Tester closed M2-001 and M2-002 and verified draft retention, saved-state restoration, resume permission refresh, notification API boundaries and explicit Tasks unavailability. No additional material defects were found. Tester reports CI evidence saved as M2_CI_adfa2b2.json; that file was not present in the developer checkout during this documentation update.

Broader M0–M2 retest on e6c128618c06e049dd99ba5149665b7fa5557862 superseded earlier M1/M2 acceptance: M0 PASS, M1 FAIL, M2 FAIL. Exact-commit CI run 40098685781 passed build, tests, lint and APK upload. The next retest of 0f5eee5 closed M1-006 through M1-008 and M2-003 through M2-004; M0 and M2 passed while M1-009 remained open. No phone behavior is claimed.

Developer local verification for the five-fix delivery: `assembleDebug`, `testDebugUnitTest` (68 tests, zero failures/errors/skips), and `lintDebug` passed. Exact-commit CI run [40100438617](https://github.com/Navee569/Project-Shiv/actions/runs/40100438617) passed build, unit tests, lint and APK upload; independent retest passed five fixes but found M1-009.

M1-009 developer local verification: the real-Room event confirmation regression passed, including retry after a failed STARTED audit, failed final audit after dispatch, and attempted dispatch failure. `assembleDebug`, `testDebugUnitTest` (71 tests, zero failures/errors/skips), and `lintDebug` passed. Exact-commit CI and independent retest remain pending.

| Tested commit | CI run | Result |
|---|---|---|
| adfa2b269426a267d1181a41e69f708b2223011b | [40097224474](https://github.com/Navee569/Project-Shiv/actions/runs/40097224474) | Tester independently verified PASS: assembleDebug, testDebugUnitTest, lintDebug and APK upload; no rerun initiated |

## M3 persistence foundation — implementation under review

Schema version 6 adds task, reminder, model metadata, source/confidence, and encrypted capture-queue records with an explicit 5→6 migration. Listening transcripts and notification summaries are committed before event extraction. A serialized WorkManager consumer claims due captures by priority, stores retry/lease state, commits event/reminder results idempotently, and resumes pending work at app start and reboot. Missing or invalid Keystore material fails closed; completed capture payloads are cleared while retryable/failed payloads remain encrypted. Model weights remain in app-private files; only metadata is stored in Room.

Regression coverage added for capture IDs/deduplication, priority order, save-before-scheduling, retry with retained input, repeated processing, concurrent claims, expired-lease recovery, transactional rollback, repository CRUD/correction/deletion, and version-5 migration. Android instrumentation exercises the production key-wrapper/SQLCipher path, plain-SQLite rejection, reopen persistence, wrong SQLCipher key, Keystore key loss, and encrypted migration. These changes are not M3 acceptance: exact-commit CI and independent Tester review are pending. No phone behavior is claimed.

M3 CI failure: commit 93e46d33f62971df10bed6c4ea8e96725899baed, run [40108334014](https://github.com/Navee569/Project-Shiv/actions/runs/40108334014). assembleDebug, testDebugUnitTest, and lintDebug passed; API 35 connectedDebugAndroidTest failed because the instrumentation process crashed before discovering any tests (0/0). The Actions log contains no process crash stack. Workflow now captures filtered emulator logcat and Android test reports on failure. M3 remains under investigation and has not been accepted.
