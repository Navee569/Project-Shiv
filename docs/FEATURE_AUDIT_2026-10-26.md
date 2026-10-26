# Shiv Phase 1 + Phase 2 feature audit — 26 October 2026

**Audit baseline:** `main@3b6c4e4`; this report also identifies incremental fixes
proposed in PR #3. Reading a file or passing CI is NOT equivalent to owner-phone acceptance.
Statuses: CODE = implementation exists but may be partial; PARTIAL = requirement not
completed end to end; MISSING = no working implementation found; OWNER-VERIFY =
must be measured/tested on the Galaxy S23. External cloud/hybrid AI is expressly
exempted from the Phase 1 no-network rule by owner instruction and is unchanged.

| Requirement area | Result / evidence | Remaining acceptance |
| --- | --- | --- |
| Phase 1 M0 Android baseline | CODE: CI Gradle assembleDebug/unit/lint workflow | Latest commit must pass; real install OWNER-VERIFY |
| M1 boundaries | PARTIAL: `Brain`, domain, persistence, workers, Compose | Controller remains monolithic; independent integration boundaries incomplete |
| M2 shell | CODE: Compose chat, memory, events/tasks, models, status, grants, privacy | Device navigation/rotation/accessibility OWNER-VERIFY |
| M3 encrypted storage | CODE: Room + SQLCipher, Keystore-wrapped secret, migrated key/value vault, encrypted backup | Schema migration, corruption, wrong-key, transaction, loss/recovery and restore instrumentation OWNER-VERIFY |
| M4 offline AI | PARTIAL: LiteRT-LM CPU, SAF import, bounded scheduler, strict local tool parser, proposed two-role routing in PR #3 | Both model format loads, delegate research, dynamic unload/prewarm, quality, token counts and S23 timings OWNER-VERIFY |
| M5 memory | PARTIAL: explicit facts, corrections, deduplication, provenance, saved rules and agenda | Complete episode/people/place graph, memory retention and evidence-scored retrieval |
| M6 speech | PARTIAL: on-device SpeechRecognizer when available, Google TTS locale check | Kannada/Telugu/mixed-language test corpus, interruption streaming, speaker ID and wake word OWNER-VERIFY/RESEARCH |
| M7 proactive decisions | PARTIAL: pure interruption policy, scheduled reminders, signal queue, owner reminder rules | Complete owner-routine learning, proactive brief, scheduled triggers, rate limits wired into all nudges |
| M8 Android integrations | PARTIAL: notification listener, dialer/SMS/email/app handoffs, local reminders, offline image OCR | Contacts, SMS source, call-ended check-ins, calendar reads, usage stats, accessibility, location and advanced provider integrations |
| M9 security | PARTIAL: privacy kill switch, guest state, package denylist, structured action gate and biometric memory unlock | Instrumented privacy tests, malicious screen source simulation, exported-components/permission auditing, full penetration test |
| M10 benchmark | PARTIAL: measured per-op durations and device stats read on phone | S23 power, heat, timing, error rate, storage and real battery comparisons OWNER-VERIFY |
| M11 release | PARTIAL: debug APK produced in CI | Full milestone acceptance, real phone, stability matrix, documented benchmark and security signoff |
| P2-1 governor | PARTIAL: pure policy + encrypted activity ledger; PR #3 connects admissions and daily budget | Background compute-lease coverage, real energy calibration, delegate calibration and pre-warm |
| P2-2 multi-tier memory | PARTIAL: structured facts, summaries, reason trace | Graph, semantic/episodic/procedural retention and idempotent verified consolidation |
| P2-3 anticipation | PARTIAL: interruption policy and reminder-tier suggestions | Behavior-derived routines, leave-now and preparation briefs, reaction feedback, weekly evaluation |
| P2-4 multilingual | PARTIAL: language selection, on-device STT and TTS | Real mixed-language golden-set tests, local STT evaluation, wake word, continuous sessions |
| P2-5 senses | PARTIAL: opt-in notification metadata and Latin OCR | Camera understanding, multilingual OCR, opt-in compliant screen/screenshot capture, incoming SMS facts |
| P2-6 device controls | PARTIAL: explicit owner approval, settings handoffs, biometric check and basic undo | Time-machine undo for supported actions, all privilege/grant flows, ADB permission research; Shizuku requires separate owner consent |
| P2-7 opt-in additions | NOT ENABLED as new features: explicit opt-in/approval required | Do not implement optional external model flavor, PC companion or model fine-tuning without written approval |
| P2-8 hardening | NOT COMPLETE | Reproducible final build and all physical-device acceptance tests |

## PR #3 scope: checks actually added

- Resource-gated local model loads and inference. A fresh device snapshot and encrypted
  ledger usage are read before a local chat; model loading expenses force a second check.
- Owner-selectable small router versus conversation model; one local engine swaps on demand,
  with no auto-fallback to cloud and no runtime self-authorization.
- Adjustable 1/5/10/30 minute daily budget, time-bounded inference cancellation requests, cancellation queue
  recovery, two-minute idle release of the large model and staged-model cleanup.
- Pure regression tests for budget boundaries, urgent bypass, role selection, and interrupted
  native inference; CI build/lint/tests, when completed, are the only automated validation.
- Full simultaneous two-model residency is **not** implemented; two-minute
  idle eviction and power budgets are coded but still require real-device
  verification and energy calibration. Cloud implementation remains unchanged.

## Release blockers

No claim that **every feature works** is possible without genuine instrumentation and
owner-phone tests; M3 migration failures could risk data, untested privileged integrations
would risk privacy, and live speech/thermal/battery/call/permissions need real Android/Samsung
behavior. The principal blockers are listed in STATUS.md and TESTING.md. The `ACTIVE`
labels in the public capability matrix describe bounded entrypoints, not end-to-end
functional parity with external apps.

## Follow-up: development pass on branch development/shiv-complete-pending-20261026

This initial audit described the baseline before PR #4. The newly implemented
bounded, opt-in paths are described in STATUS.md and SHIV_CAPABILITY_MATRIX.md.
They include deterministic event anticipation, owner feedback, inferred routines
from completed events, Calendar/Contacts/Usage previews, one-shot app-allowlisted
Accessibility, generic call-ended check-ins and reviewed OCR due-date tasks.

**Do not reinterpret this appendix as full Phase 1/2 milestone acceptance.**
The Android-only instrumentation APK is compiled in CI but has not been executed
on the Galaxy S23. Full simultaneous router residence, speech/wake word,
geofencing, many app providers, multilingual OCR, complete privileged device
control and broader real-device QA still remain. Cloud AI is unchanged.
