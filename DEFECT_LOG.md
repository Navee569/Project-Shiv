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
| M2-001 | S2 | FIXED — retest pending | Hoist unsent chat and profile drafts into the saveable shell state above conditional destinations; initialize profile drafts from persisted profile only once. | ShivNavigationTest actual shell navigation, unsent chat draft, profile draft and saved-state restoration |
| M2-002 | S2 | FIXED — retest pending | Observe permission status on lifecycle resume in Settings and Permission explanations; combine app notification enablement on all supported APIs with the Android 13+ runtime permission. | ShivNavigationTest resume refresh and API 28/API 33 notification capability boundaries |

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
