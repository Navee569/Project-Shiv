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
- D-006 HIGH: single-model prioritized inference queue is implemented,
  but two-model hot/cold residency, stale-job cancellation and calibrated power
  gating are not complete.
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
