# Shiv status — development-only checkpoint

**Branch:** feature/shiv-phase1-implementation  
**Automated verification:** latest application source commit `8e48ed0`: clean APK/build, unit tests, Android lint and static checks PASS (run [40259210693](https://github.com/Navee569/Project-Shiv/actions/runs/40259210693)); API 26 and API 35 emulator: **14/14 each PASS** (run [40259210606](https://github.com/Navee569/Project-Shiv/actions/runs/40259210606)). 68.2 MB `shiv-native-debug-apk` artifact verified. This is an automated test report, not device acceptance.
**Owner/device verification:** NOT RUN on Samsung Galaxy S23. Phase 1 M11 is NOT accepted.  
**Result:** partial Phase 1 implementation, NOT accepted M11. Never report complete feature parity from a status label.

| Requirement | Implemented source | Remaining before accepting |
| --- | --- | --- |
| M0 baseline | Original Shiv launcher, Gradle and CI kept; Gradle/Rom deps added | Full CI passed on tested commit; Galaxy S23 owner checks outstanding |
| M1 architecture | Interfaces, Room, Brain, queue, decisions and separation documented | ViewModels/repositories need complete modularization |
| M2 shell | Chat, Memory, Automation, Model Manager, AI Status, Events, Beliefs, Decisions, permissions | Launch/recreate Android instrumentation passed on API35; broader navigation/rotation/device QA pending |
| M3 persistence | Room+SQLCipher, wrapped key, 14 entity families, encrypted vault migration | API26/35 encrypted file/round-trip, wrong-key rejection, rollback, 24 concurrent writes passed; real legacy migration/key invalidation/restart test pending |
| M4 local AI | Real LiteRT-LM 0.18.0 Brain, two role imports, queue, status and strict JSON parser | model format/engine compatibility, latency, tokens/s, cancellation active inference, output budget |
| M5 memory | explicit facts, corrections, source/confidence, retrieval and context snapshot | export/restore, semantic links, expiry cleanup, full dedup and concurrency |
| M6 voice | on-device STT, Google TTS engine, three language locales | owner language packs, speaker recognition, session mic FGS/VAD, stream/barge-in |
| M7 proactive | WorkManager encrypted signal processing, quiet-hour/cap/power decision rules, basic charging-only hypotheses | full trigger registration, reactions, idempotence test and reminder UX |
| M8 integrations | notification listener filter, reviewed Android intents, one-shot Accessibility, bundled local image labels | contacts/calendar/call-end adapters, robust mic session, app per-source configuration |
| M9 privacy | denylist enforced before model/storage, no plaintext fallback, explicit cloud exception, hardened action policies | threat model, export/restore, merged manifest permission inspection |
| M10 measurements | elapsed load/reply time, queue count and memory admission inputs | TTFT, tokens/s, real thermal/battery/speech measurements ON OWNER DEVICE |
| M11 release | documentation and source staging | Build/JVM/lint and 14 tests each on API26/API35 passed; owner/real-model/real-device verification, remaining feature gaps and release acceptance remain |

## Owner-confirmed decisions tracking

- N1: encrypted queue + worker; 37-signal continuation and single-event confirmation tests passed. Process-death/reboot exactly-once and source-specific dedupe still need verification.
- N2: LiteRT router/conversation interfaces and manual model import implemented. Residency, delegates and real device fit remain OWNER-VERIFY.
- N3: deterministic repeated-pattern learner with beliefs, editable/delete UI; not every opt-in sensor integrated.
- N4: on-device recognizer and explicit Google engine; supported kn-IN/te-IN not verified.
- N5: passive energy governor present; no unconsented always-on recording or background microphone launch.
- N6: telephone call audio forbidden; post-call and Samsung file importer pending.
- N7: denied packages filtered before notification/Accessibility reading; one-shot service added; secure-window restrictions remain OWNER-VERIFY.
- N8: hard ASK_FIRST floor and redacted action logging for owner-approved events implemented and tested; editable tier settings and ACT_THEN_TELL undo UI remain pending.
- N9: phone-only checks in TESTING.md remain OWNER-VERIFY.
- N10: beginner steps in README.

## Verified externally vs not

Implementation references used: Android LiteRT-LM Kotlin API 0.18.0 and SQLCipher for Android Room integration. The specific candidate Gemma 3 1B and Gemma 3n E2B model files, Samsung Galaxy S23 performance, offline Kannada/Telugu STT, image-labeler script support, and One UI access restrictions are **NOT VERIFIED**. No measurements or success claims have been invented.

## Explicit cloud exception

The owner requested an exception for configurable cloud AI API. Cloud sending is optional, consent-gated, uses an owner-controlled HTTPS-compatible endpoint and is never the default. Sensitive Android signals and raw app awareness do not use that channel.

## October 26, 2026: source-by-source feature audit

See [FEATURE_BY_FEATURE_AUDIT.md](FEATURE_BY_FEATURE_AUDIT.md) for every Phase 1 milestone (M0–M11), binding decisions (N1–N10), scenarios (A–K), and the gated Phase 2 catalog. Repairs include owner-reviewed event/reminder creation, >30 signal continuation, memory correction/deletion, full chat/signal erasure, model SHA-256 verification off the UI thread, and redacted event audit logs.

**No declaration of complete feature parity:** encrypted export/restore, active inference cancellation, verified E2B/1B model performance, all awareness triggers, detailed owner tier controls/undo, microphone FGS, mixed-language quality, full sensor-learning adapters, and Samsung-specific validation are still incomplete or owner-verification-only. Phase 1 M11 remains **NOT ACCEPTED**. Phase 2 remains gated.


## Corrected-baseline development pass (from verified 8e48ed0, preserving edae3ef audit)

**Branch:** development/shiv-parity-completion-from-8e48ed0  
**Source:** development/shiv-feature-parity-audit@edae3ef (direct descendant of screenshot commit 8e48ed0).  
**Target:** merge back into development/shiv-feature-parity-audit, **not** into main.

Source changes in this pass:
- Calendar read is now a temporary, source-filtered preview. The owner individually
  confirms each event before writing the encrypted event and redacted audit record.
  Permission revocation blocks later import.
- One-shot Accessibility requires the exact non-sensitive app package to be
  supplied by the owner before arming for 15 seconds. Other packages are rejected
  before the view hierarchy is traversed; STOP disarms it.
- Reminder tier (ASK_FIRST by default, or ACT_THEN_TELL for explicitly requested
  local reminders only) is owner-controlled and stored encrypted. Undo marks a
  pending reminder canceled and requests WorkManager cancellation. Other
  communication and financial actions retain the hard ASK_FIRST floor.
- Approved owner-reviewed events can be undone atomically with their pending
  reminder and a preserved audit trace; previously displayed Android
  notifications cannot be recalled.
- Owner-initiated read-only Contacts searches return at most ten temporary
  display names. Phone numbers, email fields and the results themselves are
  not saved in Shiv memory.
- Case-insensitive banking/UPI/authenticator denylist matching now handles
  mixed-case package variants and subpackages.
- Added JVM and Android instrumentation regressions for consent, privacy,
  reminder authority, reminder undo, and event undo. Both Android API 26 and
  API 35 device-emulator runs are required before merge.

**Not completed by this pass:** full M11/real Galaxy S23 acceptance, local E2B/1B
real-model load, actual token/s and battery targets, always-ready offline
microphone sessions, advanced multilingual accuracy, complete person/place
graph and third-party integration contracts, fully general undo, secure export
and restore. Phase 2 remains gated by M11 acceptance and optional privileged
access remains owner-approval-only. These must not be described as done.
The existing cloud AI implementation in ShivIntelligence.kt is unchanged.


## 26 October 2026 additional Phase 1 integration — correct audited lineage

**New branch:** `development/shiv-phase1-remaining-integrations-20261026`, based on
`development/shiv-feature-parity-audit` at the verified post-PR#5 source.
Cloud intelligence implementation `ShivIntelligence.kt` is unchanged.

Development added in this pass (CI vs S23 verification to be reported separately):
- Explicit owner-data-erasure flow requiring the exact `ERASE SHIV DATA` phrase.
  One SQLCipher transaction removes Shiv's chat, memory, facts, events,
  reminders, rules, beliefs, decisions, owner preferences, action audit,
  encrypted cloud secret and queued private signals. Pending reminder jobs
  are canceled; local model *sessions* and app notifications are cleared.
  Model weight files/metadata remain to avoid destroying public GB assets.
  This is not a filesystem-level secure overwrite of storage blocks.
- Daily battery-aware WorkManager memory-expiry cleanup physically deletes
  expired memory records. Pending owner decision records are retained.
- Owner-encrypted per-app notification/Accessibility/queue exclusions with
  exact package validation and screen capture disarming. Built-in banking,
  authenticator and payment exclusions remain non-overridable.
- Local neural inference rechecks battery/RAM/thermal on every reply, uses
  the smaller router when eligible, serializes all chat inference and model
  load/unload, requests 2-minute large-model idle release, limits prompts and
  best-effort in-flight time. Logs only measured first-fragment latency and
  character count; **neither is a claimed token/s or battery measurement**.
- Separate throwaway SQLCipher instrumentation checks owner erasure,
  expiration, and retention of imported public model metadata. Pure JUnit
  tests exercise canceled inference-queue request recovery and app exclusions.

**Still incomplete/unverified:** Phase 1 M11 physical S23 acceptance, full
encrypted portable export/restore, hardware LiteRT model files and delegate
calibration, high-quality local Kannada/Telugu voice, continuous microphone
user-initiated service, complete third-party authenticated integrations,
general-purpose reversible automation, on-device energy profiling, and all
remaining gaps in FEATURE_BY_FEATURE_AUDIT.md. Phase 2 stays gated per
docs/PHASE2_GATE.md. Do not claim complete implementation or S23 testing.


## Final combined Phase 1 development pass — 26 October 2026

**Correct source:** `development/shiv-feature-parity-audit`, descendant of
the owner-verified `8e48ed0` test baseline. The separate `main` tree is not
used. **Cloud API implementation `ShivIntelligence.kt` has not been edited.**

New development implementations:
- Portable password-derived, AES-256-GCM authenticated SQLCipher owner backup
  and restore. Owners select the export/import document using Android SAF and
  supply a 12+ character passphrase. Backup files contain ciphertext, not
  plaintext. Restore verifies format/schema, bounds input and writes inside a
  single transaction. Model binaries remain device-specific and are excluded.
  Previously scheduled reminders/queued signals and sensitive permissions
  remain disabled on restore. Import resets cloud permission to LOCAL/OFF.
- Owner-initiated microphone foreground session, limited to 10 minutes or
  earlier for battery below 25%, severe thermal state or repeated recognition
  failures. Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` only on
  supported Android versions, foreground notice with STOP action, and a
  separate RECORD_AUDIO runtime grant. It does not record telephone audio,
  bypass background access limits or provide a wake word. Recognized
  non-sensitive utterances enter the encrypted ASK_FIRST signal workflow.
- SQLite-transactional deduplication for background notifications by source
  package and fact (10 minute window), plus short-window microphone retry
  deduplication. Deliberately repeated user-entered commands are not collapsed.
- Native and instrumentation regression tests for backup KDF, tampering,
  wrong password, SQLCipher restore/rollback, source dedup, microphone
  limits and speech privacy. All tests must pass at the final PR head SHA.

**Still not achieved:** M11 Galaxy S23 physical acceptance, verified real
1B/E2B LiteRT model files, actual Kannada/Telugu offline recognition quality,
true always-ready battery-aware wake-word VAD, speaker identification, live
offline multilingual OCR, end-to-end third-party OAuth/action contracts,
portable binary model weights, independent provider action undo, measured
delegate/energy benchmarks, all app UX/accessibility checks and M11 owner
signoff. Android emulators cannot settle those facts. The source requirements
explicitly gate **Phase 2** on M11 acceptance; no Phase 2 claim is made.


- Bundled Latin ML Kit offline OCR for explicit owner-chosen images, with
  transient preview, credential-text suppression and strictly parsed ISO
  due-date suggestions. The owner must provide a task title and exact 24-hour
  time before encrypted agenda creation. The selected image is not copied or
  transmitted. Kannada/Telugu OCR is still missing.


## 26 October 2026 — Additional scoped Phase 1 software integration

Source: development/shiv-feature-parity-audit; this is **not** the separate
main implementation. The existing cloud AI source ShivIntelligence.kt remains
untouched. No new unauthorized ADB, Shizuku, location, call recording or
always-on microphone access was added.

- Added optional hourly WorkManager agenda notices, visible only after an
  explicit owner toggle. Notifications contain generic text, not private
  event titles. Only encrypted owner-approved events are considered.
  Quiet hours 10 PM–9 AM, max three optional notifications per day, repeat
  suppression and battery-not-low constraints apply. Scheduling is inexact.
- Owner can mark the last optional notice ACTED, SNOOZED, DISMISSED or CORRECTED.
  Feedback history is encrypted, weekly report is displayed, and repeated
  negative reactions reduce (never raise) future *optional* daily caps.
  No model is allowed to fabricate a reaction or claim a task was completed.
- Owner-triggered Android Usage Access summary returns at most ten apps with
  more than five minutes foreground use in the prior day. Package denylist
  filtering runs before showing results. Read data is never persisted,
  included in AI context or exported. Special Usage Access remains OFF until
  granted manually in Android system settings.
- LiteRT local model load and inference now record actual elapsed wall-time
  in the encrypted daily ledger. Owner can choose 1, 5, 10 or 30 minutes
  per day; the governor reevaluates remaining allowance for each request.
  Leases enforce best-effort bounded coroutine cancellation. The ledger is
  not a battery mAh, GPU, NPU or tokens-per-second measurement.
- The local neural proposed-action validator now requires the mandatory
  fields and correct dates for every known tool, rejects extra/oversized
  arguments, and otherwise fails closed to STAY_QUIET.
- Tests added for proactive quiet-hours/dedup/cap, negative feedback policy,
  blocked usage sources, local daily budget boundaries and strict per-tool
  JSON contracts on Android emulators.

**Unfinished:** real device M11, S23 on-device actual E2B/1B formats and model
load/latency, Kannada/Telugu STT/OCR quality, always-ready wake word and
speaker recognition, full provider integrations, calibrated energy benchmarking,
continuous adaptive sensing, fully general reversible actions and several
architecture/UX/security acceptance items. Phase 2 gate remains intact in
docs/PHASE2_GATE.md. Do not report full feature completion.
