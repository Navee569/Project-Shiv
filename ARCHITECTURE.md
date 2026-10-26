# Shiv architecture and decisions

## Current boundaries
- UI: Compose in ShivInterface; Activity result launcher in MainActivity.
- Controller: state and owner configuration in ShivController.
- Deterministic domain: PrivacyPolicy, AuthorityPolicy, ResourceGovernor,
  Anticipation, SignalFacts, planner contracts.
- AI: ShivIntelligence (existing cloud/hybrid/command routing, unchanged);
  Brain interface; LiteRtBrain (actual LiteRT-LM 0.18.0 local model execution).
- Persistence: ShivVault (AES-GCM encrypted SharedPreferences with
  non-exportable Android Keystore key), ShivKnowledge, SignalQueue and AuditTrail.
- Android integrations: explicit Intent handoffs, WorkManager, optional notification
  listener, Android 31+ on-device speech.
- Diagnostics: ShivDeviceMonitor reads real phone power/RAM/thermal state.

## ARCHITECTURE_DECISIONS
1. OWNER EXCEPTION: existing cloud AI is preserved untouched. This means
   INTERNET remains in the manifest, despite the original no-network text.
2. No UI or AI model chooses its own action tier. AuthorityPolicy applies before
   handoff. Money/permanent-delete/send-message/privileged always require approval.
3. Denied app content is checked BEFORE encryption/storage/inference. Raw
   screenshots and accessibility text are NOT implemented.
4. LiteRT-LM requires local filesystem model paths. SAF references alone cannot
   be loaded; the app explains and copies selected files into private storage on
   first load. On-device load success is OWNER-VERIFY, never asserted by CI.
5. Deterministic power decisions accept actual sensor readings. No invented
   benchmark, tokens/s, battery drain or NPU acceleration is displayed.
6. Existing SharedPreferences encryption is a transitional storage design,
   not acceptable for M3/M11; migrate with key-loss, data-correction and
   migration tests before release.
7. Low-confidence notification facts remain labelled possible/hypothetical.
   No implicit event/calendar write or proactive interruption from unconfirmed facts.
8. Features requiring special privileges, a PC, Shizuku or signature permissions
   are NOT enabled by default. No shell-command execution via model output.
