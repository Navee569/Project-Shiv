# Shiv implementation status — 26 October 2026

This file distinguishes **code written** from **CI verified** and **OWNER-VERIFY**.
The source of truth is the repository commit and its matching GitHub Actions run.
Cloud AI / hybrid paths are retained as the owner requested; this is an explicit
exception to the no-network policy in the original Phase 1 specification.

## Implemented in this development branch

- Original Compose UI; secure confirmation before outgoing Android app handoffs.
- Existing cloud/hybrid routing preserved, not refactored.
- Room + SQLCipher backed encrypted records with Android Keystore-wrapped random
  passphrase and lazy migration from legacy encrypted SharedPreferences; migration
  safety and physical encryption must still be OWNER-VERIFY.
- New 4-layer memory facts with owner corrections, deletion, evidence/provenance and safe deduplication.
- Deterministic app-package/OTP filters **before** notification content is stored;
  configurable extra denylist; guest mode, manual privacy-zone flag and sensing pause.
- Owner-entered events/tasks with strict local dates, encrypted agenda storage,
  editable completion/deletion and factual "What's on tomorrow?" answers from real
  saved records (owner/device verification pending).
- Encrypted, crash-resumable sanitized signal queue with WorkManager processing.
  Low-confidence possible-event extraction from permitted notification titles, including
  basic Kannada/Telugu keywords. Does NOT pretend to understand arbitrary messages.
- Read-only grants and revocation guidance screen for system microphone,
  notification, listener and biometric status (OWNER-VERIFY).
- Charging-and-idle-constrained daily consolidation worker, resource-governor policy,
  action authority hard floors, audit traces and on-device resource readings.
- LiteRT-LM 0.18.0 runtime loading from app-private model path, real local
  neural inference when the phone actually loads a supported .litertlm model.
  Model references registered via SAF; large file is copied only before running
  because LiteRT-LM needs a file-system path. Format compatibility is runtime-verified.
- Explicit on-device STT (Android 31+) and Google TTS selection; en-IN/kn-IN/te-IN
  availability must be checked on the device. No silent cloud-recognizer fallback.
- Tests for denial rules, power governor, authority, interruption logic and signal
  extraction. The configured CI suite must be green on the final commit.

## Incomplete — DO NOT MARK COMPLETE

- End-to-end Room + SQLCipher migration validation and future schema migrations.
  Passphrase-encrypted export/merge-restore is coded; migration and restore
  behavior are BUILT - OWNER-VERIFY, not yet cleared for production.
- Two-model resident router / on-demand large model scheduling, priority inference
  queue, structured JSON tool schemas, token metrics and measured power budget.
- Legitimate full-scope app awareness: accessibility denylist, UI extraction, contacts,
  usage stats, SMS, call-log check-ins, location/geofencing.
- Automatic anticipation from owner-verified routines, proactive morning/evening
  briefs, social graph, trust ladder, weekly self-evaluation and real-world outcomes.
- Offline OCR, camera understanding, mixed-language golden-set accuracy, wake word,
  speaker recognition, mic foreground-service sessions and streaming barge-in.
- ASK_FIRST biometric gating, encrypted backup/recovery, full undo, sandboxed
  privileged allowlist and owner-approved ADB grants. Shizuku needs separate approval.
- CI security/instrumentation, Android API 26–35 device matrix, real-phone testing,
  benchmark and battery measurement. Code existence is not device verification.

## Owner-verification boundary

The Galaxy S23 has NOT been used to verify any microphone, speech locale,
model-load speed, tokens/s, GPU/NPU, thermal behaviour, battery use or
accessibility feature. See TESTING.md for exact steps.

## Security caveats

Notification titles from non-denylisted sources are encrypted but remain personal
data; avoid enabling notification access until satisfied with permissions.
The denylist is conservative but cannot enumerate every Indian banking app.
Do not use this app as a production financial automation or call recorder.
