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
