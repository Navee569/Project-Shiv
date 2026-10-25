# Shiv Phase 1 Architecture

## Boundaries

- **UI:** original Compose main experience plus Models, AI Status, Events, Beliefs, Decisions and Permissions panels
- **Controller:** `ShivController` (existing UX), `PhaseOneController` (model/diagnostics integration)
- **Domain:** `ShivPlanner`, `ActionAuthorization`, `StructuredToolParser`, `ProactivePolicy`, `SensitiveAppDenylist`
- **Persistence:** Room `ShivDatabase` with SQLCipher `SupportOpenHelperFactory`; passphrase protected by non-exportable Android Keystore AES-GCM key
- **Compatibility:** `ShivVault` migrates existing encrypted preferences to SQLCipher before using them; no plaintext fallback
- **Memory:** encrypted Room facts, events, rules, patterns, decision traces and bounded context snapshot
- **Intelligence:** `Brain` contract, real LiteRT-LM local inference, `InferenceQueue`, optional explicitly consented cloud API exception
- **Integrations:** WorkManager signal processing and reminders, on-device speech coordinator, optional notification listener and one-shot Accessibility awareness

## ARCHITECTURE_DECISIONS

**AD-001 — Shiv is the application.** No upstream APK source or feature implementation is fetched at runtime or built by CI.

**AD-002 — Zero plaintext personal-data fallback.** Database encryption key is generated once and wrapped by Keystore; losing it is an explicit recoverability error, not a reason to recreate a cleartext DB.

**AD-003 — Save first.** Relevant incoming signals are stored in encrypted Room before the worker considers them. Worker produces reviewable traces, not irreversible actions. A PROCESSING item reverts to RETRY after process death; retries are idempotent by signal ID.

**AD-004 — Deterministic authority.** Model JSON is only a proposal. Action policies, payment floor and privacy exclusions are independent of the model. Sensitive app exclusions precede data access.

**AD-005 — Layered intelligence.** Deterministic rules, short router model and on-demand conversation model are separate; model weights are not downloaded or bundled. The owner manually selects models and authorizes an app-private copy.

**AD-006 — Explicit network exception.** Cloud prompts/images leave Shiv only when owner selects CLOUD/HYBRID and explicitly opts into the API destination. No analytics, telemetry, background sharing or cloud model download.

**AD-007 — Respect Android.** No call-audio interception, background microphone service from boot, silent Accessibility enabling, root, Shizuku, or other apps' private files.

**AD-008 — Phase gate.** Phase 2 remains deferred until Phase 1 M11 is accepted; optional privileged workflows require a separate approved design.

## Compatibility and remaining architecture work

The original connected repository did not contain the prior Kotlin SQLCipher source described in its archived README. We can preserve and migrate the *available* encrypted preferences, but cannot reconstruct absent prior source/migrations. New schema is version 1. Database migration tests and schema export review are not executed in this development-only iteration.

Room creates encrypted records, and former ShivVault data is re-encrypted as SQLCipher records. The legacy migration is idempotent by key but must undergo kill/restart and key-loss testing before owner migration.

The runtime keeps two Brain instances, but resource-budget scheduling, token streaming into TTS, exact GPU/NPU delegate tests, durable model-import resume, and all Phone-only diagnostics are still unfinished.
