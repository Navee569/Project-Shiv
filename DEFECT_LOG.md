# Defect log

| ID | Severity | Status | Fix | Worker regression |
|---|---|---|---|---|
| M0-001 | S2 | Fixed (CI); awaiting independent retest | Require a listed eventId in Brain notification arguments; dispatch and account for that exact event. Reject missing/unknown identities. | alertForBAccountsForBAndCannotRepeatB; missingAndUnknownEventIdsCannotDispatch |
| M0-002 | S2 | Fixed (CI); awaiting independent retest | Room transactions serialize final cap/deduplication checks and a durable reservation before Android dispatch. Reservations count toward cap/deduplication. | overlappingWorkersWithOneDailySlotSendExactlyOneAlert; overlappingWorkersForSameEventSendExactlyOnce; failureAfterDispatchKeepsReservationAndPreventsRetryDuplicate |
| M0-003 | S2 | Fixed (CI); awaiting independent retest | After inference and again immediately before dispatch, read current settings, tier, permission, event contents and time. Cancel disabled, quiet, expired, deleted or edited events. | disablingProactiveDuringInferenceCancelsAlert; enteringQuietHoursDuringInferenceCancelsAlert; deletingEventDuringInferenceCancelsAlert; expiringEventDuringInferenceCancelsAlert; editingEventDuringInferenceCancelsAlert; revokingPermissionDuringInferenceCancelsAlert; requiringConfirmationDuringInferenceCancelsAlert |
| M0-004 | S2 | Fixed (CI); awaiting independent retest | SQL excludes delivered/reserved events before applying LIMIT 8. | firstEightSentEventsDoNotHideNinthUnsentEvent |

Verification: 13 worker-level JVM tests use actual CoroutineWorker.doWork, real in-memory Room queries/transactions and fake Brain/notification ports. The narrow suite and final assembleDebug, testDebugUnitTest (16 tests total) and lintDebug pass locally. These tests use synthetic data and do not change production SQLCipher initialization. No device testing was performed; notification delivery uses a fake port.

Limit: a crash or uncertain dispatch/commit failure retains the durable reservation. It consumes a daily slot and suppresses that event to avoid a possible duplicate, even when delivery cannot be confirmed. A definite no-delivery result releases the reservation.

| Fix commit | CI run | Result |
|---|---|---|
| b6b533fec67217da7846bce5863ad9ddcee520fc | [40044537471](https://github.com/Navee569/Project-Shiv/actions/runs/40044537471) | PASS: assembleDebug, testDebugUnitTest, lintDebug |
