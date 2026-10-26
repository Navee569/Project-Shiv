# Shiv defect log

## Fixed on development/shiv-feature-completion
- D-001 (build): using Kotlin 'any' on org.json.JSONArray caused compile errors.
  Fixed by indexed iteration. Regression coverage remains insufficient.
- D-002 (build): privacy setter functions collided with generated property
  setters. Renamed to updateSensingPaused, updateGuestMode, updatePrivacyZone.
- D-003 (security): previous NotificationListener stored banking/UPI/OTP
  notification titles. Added source and sensitive text denylist before write.
  Unit tests added for common sources and OTP patterns. Owner phone test pending.

## Open
- D-004 HIGH: Room + SQLCipher plus password-encrypted backup are coded,
  but require on-device validation of legacy migration, wrong-key behavior,
  backup reopening, restore and key loss.
- D-005 HIGH: full sensitive-source denylist review for Android providers
  including Accessibility, SMS, OCR and screenshots before granting access.
- D-006 PARTIAL/HIGH: owner-selected router/conversation model swapping,
  measured daily compute admissions, best-effort inference timeout and large
  model idle unloading are now implemented (PR #3). Full simultaneous
  small-router residency, delegate/performance calibration and real-device
  cancellation behavior are still unverified/incomplete.
- D-007 HIGH: full Android permission-aware action types and undo.
- D-008 MEDIUM: actual offline STT support for Kannada/Telugu unverified.
- D-009 MEDIUM: end-to-end notification-delivery and process-kill tests.
- D-010 MEDIUM: on-device LiteRT-LM model import/load not owner verified.
- D-011 MEDIUM: biometric gates and encrypted backup are coded, but device
  testing, backup key-recovery guidance and failure-case testing remain open.
- D-012 MEDIUM: no full Android integration or instrumentation suite.
- D-013 MEDIUM: queue is a JSON record inside SQLCipher's Room store rather
  than a normalized Room entity; 500-item low-priority cap requires drop log.
- D-014 LOW: phase2 activity energy ledger is not OS battery accounting.

- D-015 (fixed in follow-up): mixed-case bank package identifiers could bypass exact denylist checks; normalize both sides and unit-test.
- D-016 (fixed in follow-up): reminder rule from scenario C was not retained or enforced; deterministic parser and delivery guard added.
- D-017 (partial): queue cap drops now have an encrypted, content-free audit; unbounded audit retention and normalized queue tables remain open.

## 26 October targeted fixes (PR #3; phone verification pending)
- D-018 (fixed in code): conversation model usage previously called the
  governor only from UI/model load while chat inference ran without any
  lease. Chat now samples device state and encrypted daily usage before
  sending a local request, rechecks after staging and requests timeout.
- D-019 (fixed in code): independent native inference cancellation could
  terminate the scheduler drain and strand priority queue entries. The
  scheduler now continues on inference-only cancellations; regression added.
- D-020 (fixed in code): removing a model reference did not reclaim
  multi-GB app-private staged model bytes. Staged copies are now deleted,
  with the owner's original document left untouched.
- D-021 OPEN: native LiteRT cancellation may not halt CPU computation
  instantly. Wall-time timeout requests cancellation but is not proof of
  immediate engine termination; test on S23 before power claims.
