# Shiv status — development-only checkpoint

**Branch:** feature/shiv-phase1-implementation  
**Build/tests:** intentionally NOT RUN for this prompt (owner requested development now, testing later).  
**Result:** partial Phase 1 implementation, NOT accepted M11. Never report complete feature parity from a status label.

| Requirement | Implemented source | Remaining before accepting |
| --- | --- | --- |
| M0 baseline | Original Shiv launcher, Gradle and CI kept; Gradle/Rom deps added | Repeat full CI on this branch; wrapper and version compatibility check |
| M1 architecture | Interfaces, Room, Brain, queue, decisions and separation documented | ViewModels/repositories need complete modularization |
| M2 shell | Chat, Memory, Automation, Model Manager, AI Status, Events, Beliefs, Decisions, permissions | Navigation/rotation/process state QA |
| M3 persistence | Room+SQLCipher, wrapped key, 14 entity families, encrypted vault migration | schema migration suite, actual encryption/rollback/wrong-key verification |
| M4 local AI | Real LiteRT-LM 0.18.0 Brain, two role imports, queue, status and strict JSON parser | model format/engine compatibility, latency, tokens/s, cancellation active inference, output budget |
| M5 memory | explicit facts, corrections, source/confidence, retrieval and context snapshot | export/restore, semantic links, expiry cleanup, full dedup and concurrency |
| M6 voice | on-device STT, Google TTS engine, three language locales | owner language packs, speaker recognition, session mic FGS/VAD, stream/barge-in |
| M7 proactive | WorkManager encrypted signal processing, quiet-hour/cap/power decision rules, basic charging-only hypotheses | full trigger registration, reactions, idempotence test and reminder UX |
| M8 integrations | notification listener filter, reviewed Android intents, one-shot Accessibility, bundled local image labels | contacts/calendar/call-end adapters, robust mic session, app per-source configuration |
| M9 privacy | denylist enforced before model/storage, no plaintext fallback, explicit cloud exception, hardened action policies | threat model, export/restore, merged manifest permission inspection |
| M10 measurements | elapsed load/reply time, queue count and memory admission inputs | TTFT, tokens/s, real thermal/battery/speech measurements ON OWNER DEVICE |
| M11 release | documentation and source staging | compile, test, lint, instrument, reproduce APK, owner review & acceptance |

## Owner-confirmed decisions tracking

- N1: encrypted queue + worker added; crash idempotence / source-specific dedupe require verification.
- N2: LiteRT router/conversation interfaces and manual model import implemented. Residency, delegates and real device fit remain OWNER-VERIFY.
- N3: deterministic repeated-pattern learner with beliefs, editable/delete UI; not every opt-in sensor integrated.
- N4: on-device recognizer and explicit Google engine; supported kn-IN/te-IN not verified.
- N5: passive energy governor present; no unconsented always-on recording or background microphone launch.
- N6: telephone call audio forbidden; post-call and Samsung file importer pending.
- N7: denied packages filtered before notification/Accessibility reading; one-shot service added; secure-window restrictions remain OWNER-VERIFY.
- N8: owner policy tiers and ASK_FIRST floor code added; UI tier-edit workflow still pending.
- N9: phone-only checks in TESTING.md remain OWNER-VERIFY.
- N10: beginner steps in README.

## Verified externally vs not

Implementation references used: Android LiteRT-LM Kotlin API 0.18.0 and SQLCipher for Android Room integration. The specific candidate Gemma 3 1B and Gemma 3n E2B model files, Samsung Galaxy S23 performance, offline Kannada/Telugu STT, image-labeler script support, and One UI access restrictions are **NOT VERIFIED**. No measurements or success claims have been invented.

## Explicit cloud exception

The owner requested an exception for configurable cloud AI API. Cloud sending is optional, consent-gated, uses an owner-controlled HTTPS-compatible endpoint and is never the default. Sensitive Android signals and raw app awareness do not use that channel.
