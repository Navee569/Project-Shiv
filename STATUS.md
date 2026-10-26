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
- Bounded, priority-aware local inference scheduler (32 pending slots,
  urgent > conversation > background; cancellation and queue diagnostics).
- LiteRT-LM 0.18.0 runtime loading from app-private model path, real local
  neural inference when the phone actually loads a supported .litertlm model.
  Model references registered via SAF; large file is copied only before running
  because LiteRT-LM needs a file-system path. Format compatibility is runtime-verified.
- Optional owner-selected offline Latin OCR preview using bundled ML Kit; no
  automatic storage or cloud transmission; Kannada/Telugu script OCR absent.
- Explicit on-device STT (Android 31+) and Google TTS selection; en-IN/kn-IN/te-IN
  availability must be checked on the device. No silent cloud-recognizer fallback.
- Tests for denial rules, power governor, authority, interruption logic and signal
  extraction. The configured CI suite must be green on the final commit.

## Incomplete — DO NOT MARK COMPLETE

- End-to-end Room + SQLCipher migration validation and future schema migrations.
  Passphrase-encrypted export/merge-restore is coded; migration and restore
  behavior are BUILT - OWNER-VERIFY, not yet cleared for production.
- Two-model resident router / on-demand large model scheduling, structured
  JSON tool schemas, actual token-rate metrics and calibrated power budget.
  A single-model bounded priority inference queue is built but device verification
  and stale-request tests remain open.
- Legitimate full-scope app awareness: accessibility denylist, UI extraction, contacts,
  usage stats, SMS, call-log check-ins, location/geofencing.
- Automatic anticipation from owner-verified routines, proactive morning/evening
  briefs, social graph, trust ladder, weekly self-evaluation and real-world outcomes.
- Kannada/Telugu-script OCR, camera understanding, mixed-language golden-set
  accuracy, wake word,
  speaker recognition, mic foreground-service sessions and streaming barge-in.
- Full reversible undo and owner-approved privileged ADB grants; any Shizuku
  integration needs separate owner approval. Biometric memory/payment gates and
  encrypted export/restore are coded but require device verification.
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

## Focused follow-up (2026-10-26)
- Structured JSON tool allowlist with exact-argument verification: malformed / unexpected fields fail to silence, never bypass permission tiers. This is a parser, not autonomous execution.
- Owner rule "Ignore reminders before 9 AM" is stored encrypted, auditable and applied to later notification delivery. Earlier reminders are suppressed, not postponed.
- Fixed casing in the India banking/UPI package denylist; protected-field/FLAG_SECURE guard for future screen extraction.
- Queue cap now records each low-priority drop without copying personal content; times in allowed titles receive higher priority.
- Still require device testing and remaining M/P2 gaps above. Cloud implementation unchanged.

## Reminder authority and trust-ladder follow-up
- Owner may choose ASK_FIRST (default) or ACT_THEN_TELL for explicitly requested local reminders.
- WorkManager ID is stored in encrypted vault; owner can request cancellation of latest scheduled reminder.
- After repeated owner-approved reminders, Settings displays a suggestion only; Shiv never alters its own tier.
- All external messaging, payments and privileged actions remain ASK_FIRST. Real-device cancellation race behavior is OWNER-VERIFY.

## Local-model structured action gating
- The local neural chat path now parses strict JSON proposals. Only supported tools
  (create/update event, absolute reminder, saved fact, reminder rule, clarification)
  proceed; unknown tools, malformed data and extra authority arguments fail closed.
- Supported actions show an ASK_FIRST dialog with exact model-provided arguments.
  Real agenda / reminder / memory writes occur only after owner approval and
  deterministic validation. Structured tool outputs from cloud remain untouched.
- WorkManager reminders are approximate; real timing, time-zone transitions and
  permissions still need Galaxy S23 testing.

## Latest audited local AI follow-up (PR #3; CI pending for branch tip)
- Local model inference now obtains a fresh device resource lease before chat;
  measured encrypted local model time is charged against a user-selected daily
  budget (1 / 5 / 10 / 30 minutes). Actual inference is canceled at lease timeout.
  This is a safety budget, **not** a measured battery percentage.
- Small router and conversation model may now be assigned explicitly by the
  owner. The one-engine LiteRT implementation swaps as resources allow, with
  local-router fallback if the selected conversation model cannot load. The
  switch may require additional local disk space. No automatic cloud fallback.
- Native inference cancellation no longer automatically strands queued local
  jobs. Regression tests cover the scheduler recovery and deterministic routing.
- Removing a model reference also removes its Shiv-owned staged copy; the
  owner's original picked file is preserved.
- **Still incomplete:** dedicated simultaneous router/conversation residency, idle-eviction
  hardware testing, actual performance/energy calibration, accessibility and broader
  integrations, passive routines, multilingual improvements and hardware QA.
- This scope does not alter `ShivIntelligence.kt` or cloud/hybrid behavior.
- See [feature audit](docs/FEATURE_AUDIT_2026-10-26.md) for requirement-level gaps.
