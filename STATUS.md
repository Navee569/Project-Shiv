# Shiv status — development-only checkpoint

**Current development branch:** `development/shiv-phase1-audit-fixes-20261026`. The CI results below are historical and do not cover the current batch.
**Automated verification:** latest application source commit `8e48ed0`: clean APK/build, unit tests, Android lint and static checks PASS (run [40259210693](https://github.com/Navee569/Project-Shiv/actions/runs/40259210693)); API 26 and API 35 emulator: **14/14 each PASS** (run [40259210606](https://github.com/Navee569/Project-Shiv/actions/runs/40259210606)). 68.2 MB `shiv-native-debug-apk` artifact verified. This is an automated test report, not device acceptance.
**Owner/device verification:** NOT RUN on Samsung Galaxy S23. Phase 1 M11 is NOT accepted.  
**Result:** partial Phase 1 implementation, NOT accepted M11. Never report complete feature parity from a status label.

| Requirement | Implemented source | Remaining before accepting |
| --- | --- | --- |
| M0 baseline | Original Shiv launcher, Gradle and CI kept; Gradle/Rom deps added | Full CI passed on tested commit; Galaxy S23 owner checks outstanding |
| M1 architecture | ViewModel, typed owner/Phase 1 repositories, Room, Brain, Android adapters and queue | Verify new boundaries in API 26/35 CI; controller still coordinates approved platform actions |
| M2 shell | All primary screens, saved navigation, loading/empty/error/permission messages and accessible tabs/headings | Run new 12-screen navigation/recreation/accessibility tests on API 26/35; owner TalkBack review pending |
| M3 persistence | Room+SQLCipher, wrapped key, 14 entity families, atomic legacy vault migration, encrypted v1→v2 upgrade, integrity-gated startup and confirmed recovery | Run new migration/corruption tests on API 26/35; real key-loss and owner backup recovery pending |
| M4 local AI | Real LiteRT-LM 0.18.0 Brain, two role imports, queue, status and strict JSON parser | model format/engine compatibility, latency, tokens/s, cancellation active inference, output budget |
| M5 memory | explicit facts, corrections, source/confidence, retrieval and context snapshot | export/restore, semantic links, expiry cleanup, full dedup and concurrency |
| M6 voice | on-device STT, Google TTS engine, three language locales | owner language packs, speaker recognition, session mic FGS/VAD, stream/barge-in |
| M7 proactive | WorkManager encrypted signal processing, quiet-hour/cap/power decision rules, basic charging-only hypotheses | full trigger registration, reactions, idempotence test and reminder UX |
| M8 integrations | notification listener filter, reviewed Android intents, one-shot Accessibility, bundled local image labels | contacts/calendar/call-end adapters, robust mic session, app per-source configuration |
| M9 privacy | denylist enforced before model/storage, no plaintext fallback, explicit cloud exception, hardened action policies | threat model, export/restore, merged manifest permission inspection |
| M10 measurements | elapsed load/reply time, LiteRT TTFT/decode stats, speech engine timing and conditional whole-device battery energy delta | Audible speech and attributable power/thermal baseline ON OWNER DEVICE |
| M11 release | documentation and source staging | Build/JVM/lint and 14 tests each on API26/API35 passed; owner/real-model/real-device verification, remaining feature gaps and release acceptance remain |

### Current five-row source batch

IDs 04, 07, 08, 11 and 12 now have source paths for presentation/data/Brain/
Android boundaries, per-screen states, saved accessible navigation, atomic
legacy migration, encrypted v1→v2 migration and owner-driven storage recovery.
The Activity checks storage on an I/O thread and does not open private screens
on an integrity or key failure. Focused JVM and instrumentation tests were
added. API 26/35 CI and Galaxy S23 owner checks remain outstanding; M11 is
not accepted.

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

## Follow-up audit from PR #8 merge `6c114476` — 26 October 2026

The optional cloud chat and selected-image integration already exists in
`ShivIntelligence.kt` and remains completely unchanged. It requires the owner
to choose CLOUD/HYBRID, enter an endpoint/model, and separately enable cloud
transmission; local mode cannot send. Live provider responses are unverified.

The chat memory refresh was accidentally inside a line comment with literal
`\\n` characters. It now runs before every response so corrected or removed
notes cannot remain in the controller's cached list. An API26/API35 Android
regression exercises add/remove between chat submissions. The capability list
now accurately labels the existing temporary Contacts and short on-demand
Accessibility previews as requiring device permission.
Owner-approved reminders and external handoffs now record their actual result
in the redacted action log; approval alone no longer reads as successful
execution. An Android regression covers a reminder with an invalid time.

These repairs do not establish complete Phase 1 parity or M11 owner acceptance.
Remaining gaps and Galaxy S23 OWNER-VERIFY checks in this file, the audit and
`TESTING.md` remain open. No Phase 2 or optional privileged feature was started.

## Local Phase 1 work awaiting final native verification

Interactive chat now offers STOP while a model/provider reply is in progress.
Cancellation removes waiting inference requests and cancels cooperative active
coroutine inference; a late reply is rejected before encrypted chat storage.
A JVM regression covers active cancellation followed by a successful urgent
request. LiteRT's native execution may take longer to stop, so this is not a
claim of immediate hardware cancellation. Native build, JVM tests, lint and
API 26/35 instrumentation are required on the final commit.

Owner-encrypted per-app source modes now distinguish INCLUDE, NOTIFICATIONS
ONLY and EXCLUDE. The notification listener and one-shot Accessibility service
check mode before reading content; the encrypted signal repository checks it
again before storage. Default INCLUDE preserves existing opt-in behavior, while
Android grants, one-shot arming, global owner exclusions and the built-in
sensitive-app denylist remain independent gates. Malformed encrypted policy
denies capture. JVM and Android regressions cover these boundaries. This still
does not establish universal access to third-party screens or S23 One UI behavior.

Explicit owner memory now deduplicates exact repeated facts inside a SQLCipher
transaction and updates the encrypted chat-note index in that same transaction.
Eight concurrent repeat saves are covered by an Android regression. Observed
non-owner repetitions remain separate evidence for the pattern learner.

Local Windows verification exposed and fixed a malformed Gradle wrapper URL;
the checked-in wrapper can now launch Gradle 9.7.0. The initial local build,
JVM tests and lint passed after that fix. Kotlin warnings also exposed seven
UI buttons whose click lambdas returned method references without invoking
them (model cancel, calendar discard, Usage Access settings/clear, contact
clear, mic stop, feedback refresh). Their handlers now call the intended
controller methods. The native build, JVM tests and lint passed again after
this UI repair; API 26/35 instrumentation remains for final CI.

Local LiteRT requests now receive a bounded snapshot of encrypted owner
events, explicit memories, rules, recent conversation and redacted action
outcomes. The context is constructed only inside `ShivBrainHub`, so the
existing cloud transport in `ShivIntelligence.kt` is unchanged and receives
no automatic snapshot. The prompt marks stored content as untrusted data,
limits its size, and excludes action arguments. JVM tests cover bounds and
argument exclusion. Real model quality remains OWNER-VERIFY.

The owner reported that Phase 1 M11 Galaxy S23 acceptance is still pending.
The explicit Phase 2 entry condition therefore remains unmet; no Phase 2
implementation or privileged access is claimed here.

Failed encrypted signals are now counted in AI Status and can be returned to
the retry queue by the owner. Retrying resets the attempt counter and records
no signal content in a plaintext log. An Android regression verifies retained
failure state and retry; it does not prove that a repeatedly failing source
will eventually process successfully.

## Follow-up on five absent voice/model audit rows

LiteRT backend choice (CPU/GPU/NPU) and output-token cap are now owner settings;
changing either unloads models so the next explicit LOAD uses the saved choice.
The runtime cache directory and loaded conversation are reused. A requested
delegate may fail to load on a given phone/model; Shiv reports that failure
without claiming acceleration. AI Status exposes observed first-fragment and
routing times. LiteRT's current response type exposes no exact generated-token
count in the response fragment. The conversation benchmark API now supplies
actual decode count, TTFT and tokens/s after local inference. The status panel
shows these measurements when the runtime supplies valid values.

Push-to-talk now shows partial recognized text, can cancel an active reply when
the owner starts speaking again, and feeds local LiteRT text fragments into
sentence-sized TTS chunks. Deterministic and cloud responses still speak after
completion; exact token-level TTS timing and speech quality remain unmeasured.
The visible
owner-started 10-minute microphone session now pauses recognition during
10 PM–9 AM unless the owner overrides it, and when proximity plus darkness
suggests the phone is pocketed. A session-only optional "Shiv" phrase gate
filters recognized commands. This is not speaker recognition and does not
create an autonomous always-on wake-word listener. Those capabilities still
require separate on-device model, enrollment and Galaxy S23 evaluation.

## Awareness pause control (audit ID 46)

An ongoing Android notification now has PAUSE/RESUME for owner-enabled app
awareness. Notification and screen services check the global pause before
reading and again before storage. PAUSE revokes a screen token; RESUME never
restores it. If notification permission or this notification channel is
blocked, source capture fails closed. Reboot restores the notification only,
not a screen token. API 26/35 execution remains pending on the new commit.
