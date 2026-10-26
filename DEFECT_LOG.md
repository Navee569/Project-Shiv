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
- D-004 HIGH: Room + SQLCipher, database migrations and data export/restore.
- D-005 HIGH: full sensitive-source denylist review for Android providers
  including Accessibility, SMS, OCR and screenshots before granting access.
- D-006 HIGH: owner-controlled two-model queue and performance gating.
- D-007 HIGH: full Android permission-aware action types and undo.
- D-008 MEDIUM: actual offline STT support for Kannada/Telugu unverified.
- D-009 MEDIUM: end-to-end notification-delivery and process-kill tests.
- D-010 MEDIUM: on-device LiteRT-LM model import/load not owner verified.
- D-011 MEDIUM: biometric gates, encrypted backup and recovery missing.
- D-012 MEDIUM: no full Android integration or instrumentation suite.
- D-013 MEDIUM: signal queue currently backed by encrypted SharedPreferences,
  not SQLCipher; 500-item low-priority cap with silent drop when full.
- D-014 LOW: phase2 activity energy ledger is not OS battery accounting.
