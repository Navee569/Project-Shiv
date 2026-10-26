# Shiv evidence-based feature audit

**Basis:** Owner uploads `SHIV_REQUIREMENTS.txt` and `PHASE2_REQUIREMENTS.txt`, inspected branch source, previous verified GitHub Actions. No `PROJECT_REQUIREMENTS.md` exists in this branch. The user's explicit cloud-AI API exception is retained; cloud transmission must remain separately opt-in.

**Interpretation:** `WIRED` = a usable implementation path exists; `PARTIAL` = meaningful code but acceptance details are missing; `MISSING` = no complete route; `OWNER-VERIFY` = only real phone can prove it; `DEFERRED` = gated Phase 2. **Compiles and passes tests ≠ complete functional parity.** The OpenDroid-inspired 45-entry in-app matrix is not acceptance evidence for these requirements.

## Phase 1 milestone by milestone (source check)

| Milestone | Status | Actual evidence | Remaining requirement(s) |
|---|---|---|---|
| M0 clean Android baseline | WIRED, CI-tested | Gradle wrapper, launcher manifest, `shiv-native.yml`, Android API35 emulator | Android Studio beginner run-config & Galaxy S23 physical install OWNER-VERIFY |
| M1 architecture | PARTIAL | domain contracts, `ShivRepository`, `ShivDatabase`, `Brain`, WorkManager, policy classes | no complete ViewModel/domain/UI separation; many controllers own persistence |
| M2 app shell | PARTIAL | native Compose Core, Chat, Automate, Memory, Memories, Models, Events, AI Status, Permissions, Decisions | all-screen navigation/rotation/accessibility/permission-denied UI coverage incomplete |
| M3 encrypted persistence | PARTIAL | 14 Room entities, SQLCipher wrapped key, encrypted vault migration; emulator encryption test | imported legacy interrupted migration, wrong-key, corruption, concurrency, schema evolution, export/restore and data-loss tests |
| M4 model AI | PARTIAL | real LiteRT-LM Brain load/infer, import, SHA-256 recheck, bounded priority queue, structured tool parser | exact model files not tested; delegate selection, cancel *active* inference, time-first-token, token budget, real 1B/E2B routing, strict schema per tool |
| M5 memory | PARTIAL | save, search, correction, delete, expiry filtering, provenance/confidence, encrypted snapshot | export/restore, semantic graph, dedupe/expiry garbage collection, full owner facts UI |
| M6 language/voice | PARTIAL | push-to-talk offline Android STT (where available), explicit Google TTS, kn-IN / te-IN / en-IN | language packs/quality OWNER-VERIFY; streaming speech/VAD/mic foreground sessions and optional speaker recognition missing |
| M7 proactive system | PARTIAL | encrypted save-first signal queue, WorkManager, commitment parser, configurable quiet rules, PowerPolicy, charging-only hypothesis job | all trigger sources, interrupt budget persistence, response reactions, reboot/staleness/dedup edge cases, full routine learn/act |
| M8 Android integrations | PARTIAL | notification listener, opt-in one-shot Accessibility, calendar read, post-call metadata, relative reminders, settings/app handoffs, local image labels | contacts, calendar write with permission, foreground listening service, per-app include/exclude UI, owner tier edit/undo, full action result verification |
| M9 privacy hardening | PARTIAL | SQLCipher, Keystore, denylist before storage, manual app awareness, extra owner approval, no silent calls/payments | secure encrypted export/restore, full data erasure, all native libs/network audit, private no-INTERNET flavor not implemented; cloud exception is opt-in |
| M10 measurements | PARTIAL | model load/reply elapsed time, RAM/thermal admission and queue count | reliable TTFT/tokens/s, measured energy/speech cost and Galaxy S23 baseline |
| M11 acceptance | NOT ACCEPTED | earlier compilation + JVM/lint + API35 emulator tests green on `713112f` | current changes must pass CI; device verification, full scenarios, release acceptance and owner sign-off |

## Binding owner decisions [N] — explicit review

| Decision | Status | Concrete evidence / gap |
|---|---|---|
| N1 save-first durable processing | PARTIAL | `ShivRepository.incoming` + Room `signals` + `SignalWorker`; now reschedules after 30 records, preserves review traces. Not every integration writes a queued signal; unsupported/very stale behavior remains |
| N2 layered two-local-model brain | PARTIAL | `Brain`, model import, router & conversation entries, `InferenceQueue`, memory gate; real candidate formats and auto-load/unload & token metrics unverified |
| N3 passive learning | PARTIAL | charging-only `PatternWorker`, `BeliefRecord`, inspect/delete; no location/usage/call-log data adapters or owner-verified routine quality |
| N4 Kannada, Telugu, English | PARTIAL + OWNER-VERIFY | `ShivVoice` offline recognizer selection, explicit TTS language validation; missing voice packs fail clearly; no recorded quality/latency or streaming barge-in |
| N5 always-ready battery-aware listening | MISSING / PARTIAL | `ModelGovernor` and `PowerPolicy` only; NO working always-on VAD, proximity/light duty cycling, user-initiated mic FGS, daily energy ledger |
| N6 call limitations and post-call | PARTIAL | `PostCallCheckIn` phone-state prompt; NO cellular call-audio interception (intentionally), recording importer missing; speakerphone is not guaranteed |
| N7 app awareness security | PARTIAL | `ShivAwareness` 15s one-shot, `SensitiveAppDenylist` and notification pre-filter; full per-app policy controls, pause notification, secure-window phone validation outstanding |
| N8 owner authority tiers | PARTIAL | deterministic `ActionAuthorization`, redacted action log, ASK_FIRST floor; owner-editable tier settings and ACT_THEN_TELL undo remain missing |
| N9 owner-only phone checks | OWNER-VERIFY | `TESTING.md`: live S23 model fit, speech, heat, battery, One UI, permission behavior cannot be validated by CI |
| N10 beginner experience | PARTIAL | click-by-click README for Android Studio, importing models, permissions; each unfinished integration still needs clear user-facing instructions |

## Required scenarios [M] and [Q] — coverage

| Scenario | Status | Notes |
|---|---|---|
| A commitment: Raj Thursday 5 | PARTIAL -> implemented owner approval path | parser creates ASK trace; owner can approve *unambiguous* date/time into encrypted event and relative reminder; ambiguity still asks without multi-turn slot-fill |
| B commitment spoken | PARTIAL | push-to-talk flows into chat and signal queue; no background mic service |
| C ignore reminders before 9am | WIRED | saved rule in Room and deferred notification in WorkManager |
| D what's on tomorrow | WIRED | direct Room event lookup, not fabricated |
| E quiet / don't interrupt | PARTIAL | deterministic policy and unit tests; not all triggers wired |
| F post-call -> meeting workflow | PARTIAL | metadata prompt and later owner text can reach commitment queue, but not a complete guided dialogue |
| G missing Kannada/Telugu TTS | WIRED + OWNER-VERIFY | error and install directions; actual local voice pack not checked |
| H denylisted bank notification/screen | WIRED + emulator tested | before-storage denylist, regression for app package filtering |
| I killed during queue | PARTIAL | encrypted state + WorkManager retry; backlog test added, force-stop/reboot exactly-once still not proven |
| J energy budget / urgent event | PARTIAL | deterministic policy unit tests; no measured daily energy ledger |
| K owner correction dominates hypothesis | PARTIAL | correction supersedes old fact; encrypted compatibility notes now corrected/deleted too; full belief-conflict reasoning not complete |

## Phase 2: specifically NOT under implementation gate

`PHASE2_REQUIREMENTS.txt` §11 requires P2-0 entry audit and Phase 1 M11 acceptance before P2-1. M11 is unaccepted, so every P2 item below is **DEFERRED**, not secretly omitted or falsely done.

| Phase 2 requirement | Status |
|---|---|
| P2-0 Phase 1 review + owner device baseline | NOT ACCEPTED |
| P2-1 central Resource Governor, calibration & energy ledger | DEFERRED; Phase 1 has only rudimentary thermal/RAM policy |
| P2-2 four-layer semantic memory, nightly consolidation and explainability | DEFERRED; Phase 1 facts/traces are foundations, not a graph |
| P2-3 anticipation, nudges, self-evaluation and trust ladder | DEFERRED |
| P2-4 advanced voice, code-switching, wake word, translation | DEFERRED / RESEARCH |
| P2-5 multilingual OCR, screenshot memory, intelligent notification triage | DEFERRED / RESEARCH |
| P2-6 privacy zones, guest mode, biometrics, safe privileged design | DEFERRED (no unauthorized Shizuku/ADB or root features) |
| P2-7 separate approved cloud app flavor, PC companion, LoRA | DEFERRED / OPTIONAL; owner's current optional API setting is an explicitly consented Phase 1 exception |
| P2-8 hardening + release | DEFERRED |

## Verified automated gates and limits

- Pre-audit commit `713112f`: clean debug build + JUnit + lint + **5 Android API35 tests** previously passed, re-run independently passed October 26.
- Audited final source commit `8e48ed0` passed APK assembly/JVM tests/Android lint (run 40259210693) plus 14/14 instrumented tests on both API26 and API35 (run 40259210606). See `DEFECT_LOG.md`.
- A passing Android emulator **does not** establish that a Gemma model file loads on an S23, Kannada or Telugu actually works offline, One UI allows the intended Accessibility access, nor that the battery/heat target is met.
- The cloud exception has network permission in the current manifest. A private build with no INTERNET permission is **not** implemented; never claim offline network isolation merely because LOCAL mode is selected.


## Addendum: parity branch completion from screenshot's 8e48ed0

This addendum belongs to a development child of the original audited branch,
not to the unrelated main implementation. Original 8e48ed0 API26/API35
evidence remains historical baseline and must not be applied to later commits
until their CI passes.

Closed *source implementation* gaps: owner-reviewed calendar import (M8),
per-app short Accessibility filtering (M8/M9/N7), owner reminder tier and
cancellation (M8/N8), atomic approved event undo (N8/scenario A),
read-only Contacts name search (M8), and mixed-case privacy denylist
hardening (M9). Added unit/instrumented coverage.

Major remaining milestone work in the table above remains open; most
importantly M11 has **not** been owner accepted on the S23. PHASE2_GATE.md
continues to apply. The configured cloud AI exception remains untouched.


## Incremental Phase 1 implementation, post-PR#5

Additional scoped source paths: transactional personal-record erasure (retains
imported model weights), SQLCipher expiry cleanup, owner-app denylist enforced
at notification/Accessibility/queue ingress, per-inference resource admission,
native model swap serialization and idle conversation model unloading, measured
first-fragment latency, and queue cancellation tests. Exact testing and
remaining work are detailed in STATUS.md.

These improvements **do not** constitute M11 acceptance or Phase 2 entry.
Do not conflate best-effort native cancellation with immediate NPU/CPU
termination. Do not count characters as model tokens. Cloud AI exception
remains as configured and unmodified.


## Combined development addendum: portable restore, user-started voice and dedup

The audited `development/shiv-feature-parity-audit` source now adds these
bounded Phase 1 implementation paths, subject to CI and S23 verification:
- M3/M5/M9: portable authenticated passphrase-encrypted owner data backup,
  schema-checked atomic restore, safe default reset for cloud/authority and
  inactive restored reminders. Private portable archives do not include
  imported `.litertlm` files or system-granted permissions.
- M6/N5: owner-requested **bounded** Android foreground microphone session
  using the system's on-device speech API only. It is **not** always-on
  hotword recognition and has not been validated with kn-IN/te-IN packs on S23.
- M7/N1: durable duplicate-background-signal prevention at the encrypted DB
  boundary without removing intentional repeated owner commands.

Outstanding coverage in the original milestone table is not magically
resolved: model quality/energy, provider integrations, foreground speech
behavior, encrypted data import from legacy interrupted upgrades, complete
undo, and physical M11 acceptance remain open. Phase 2 remains deferred per
the mandatory entry gate. Cloud AI API implementation remains untouched.


- M8/N4: one selected-image **Latin-only** offline OCR path and owner-approved
  strict future due-date task creation. Does not supply Kannada/Telugu OCR,
  continuous screen monitoring, general document reasoning or verified S23
  recognition quality.


## Additional verified-by-source implementation candidates — 26 Oct 2026

Previous table reflects older baseline; the later code now includes bounded
proactive agenda reminders plus owner reactions and cap adaptation; manually
granted aggregated Usage Access preview, encrypted measured daily LiteRT wall
time budget, and all-known-tool mandatory JSON schema validation. Actual
Android API26/API35 and native tests must be green on the final PR head
before treating these as CI-tested. None of these paths supplies Phase 2
M11 acceptance, true proactive external-app automation, Kannada/Telugu OCR,
a wake-word engine, or physical Galaxy S23 energy/thermal measurements.

In particular, source-displayed feature labels must not imply that Android
privileged behavior is available without the owner's runtime grant, nor that
AI-text tool proposals can self-authorize irreversible actions. CLOUD/HYBRID
intelligence was not changed and the Phase 2 requirements remain gated.

## 26 October 2026 follow-up at merge baseline `6c114476`

The branch was checked at PR #8's exact merge SHA, rather than `main`.
`ShivIntelligence.kt` already implements opt-in OpenAI-compatible cloud chat
and selected-image requests; `ShivController` saves mode, HTTPS endpoint,
model, encrypted API secret and explicit consent. This is an existing Phase 1
owner exception to the original local-only rule, not a new Phase 2 provider
integration. No live provider call or device behavior has been verified here.

One source defect was found in chat: literal `\\n` text inside a `//` comment
commented out the refresh of the in-memory notes list. A corrected or deleted
fact could therefore remain in the next chat's `notes` argument. The refresh
is restored and covered by an Android regression that adds then removes a note
between two chat submissions. The in-app capability list now describes the
existing Contacts name preview and scoped Accessibility preview with their
permission and access limits, instead of marking both as entirely planned.
Approved actions now log the observed result: invalid reminder time, an opened
external handoff, or a failed handoff, rather than logging every approval as
if the action succeeded. A regression covers invalid reminder approval.

This closes only those audited defects. The milestone table and owner/device
limitations above remain open; M11 has not been accepted and Phase 2 is gated.

### Pending local follow-up

The inference queue previously removed canceled waiting requests but did not
cancel cooperative active requests. The follow-up adds active
coroutine cancellation, a Chat STOP control, and a late-reply guard. A focused
JVM regression covers the queue handoff. Native build and API 26/35 validation
must run before this can be treated as verified or merged. The owner's M11
acceptance is still pending, so the Phase 2 gate remains closed.

Phase 1 N7 per-app source modes now permit owner configuration for notification
facts and one-shot screen facts separately. A sensitive app cannot be restored
by an INCLUDE rule, and policy is enforced at both read and encrypted-queue
ingress. Exact on-phone Accessibility behavior remains OWNER-VERIFY.

M5 explicit owner facts now use transactional exact deduplication with their
encrypted note index, including concurrent repeat-save regression coverage.
This does not supply a semantic people/place graph or measured embedding gain.

M0/M2 local verification found a malformed Gradle wrapper distribution URL
and seven inert Compose click handlers. Both source defects were repaired;
the final SHA still needs build/JVM/lint and API 26/35 instrumentation before
these repairs can be marked verified.

M5 local model inference now uses the previously unconnected bounded context
snapshot. It includes approved events, facts, rules, recent chat and action
outcomes, but not action arguments. The cloud implementation remains unchanged
and does not receive this snapshot. Model output quality with real imported
weights still requires Galaxy S23 owner verification.

Failed encrypted signals now remain visible as a count in AI Status, with an
owner-triggered retry that resets attempts. Android regression covers this
recovery path. The final local debug build, JVM suite, lint, instrumentation
APK compilation, and static checks passed; emulator execution on the final
SHA and Galaxy S23 acceptance are separate gates.
