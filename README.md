# Shiv — private personal Android manager

**Development branch:** `development/shiv-feature-parity-audit`. This is source development, not a finished release.

Shiv is his own Android app, built with Kotlin + Jetpack Compose. He keeps local data encrypted, helps with owner-approved actions, and can use **two locally imported LiteRT-LM models**: a small router and an on-demand conversation model. A **cloud AI API is an explicit exception**, available only when the owner switches to cloud/hybrid and opts in. Nothing contacts a cloud provider simply because a setting screen exists.

## Windows / Android Studio — OWNER STEPS

1. In GitHub, open `development/shiv-feature-parity-audit` and select **Code > Download ZIP**. Avoid the separate `main` branch when checking this audit.
2. Extract the ZIP, open **Android Studio > File > Open** and select its `Project-Shiv` folder.
3. Install the Android SDK 36 and use **JDK 21** in **Settings > Build, Execution, Deployment > Build Tools > Gradle**.
4. Sync Gradle. Select the `app` run configuration and a connected Galaxy S23 to build/run when you start testing.
5. For local models, download the `.litertlm` weights yourself onto the phone (they are **not in the APK**). Open **Shiv > Models > SELECT MODEL FILE > choose router/conversation > CONFIRM APP-PRIVATE COPY > LOAD**. Expect a large duplicate copy because the LiteRT runtime requires an actual filesystem path. The 1B and E2B candidate models need on-phone verification.
6. For Kannada/Telugu speaking voices: **Settings > General management > Language > Text-to-speech > Preferred engine > Speech Services by Google > gear > Install voice data**.
7. To allow optional notification awareness, use **Shiv > Settings > notification switch** and manually grant access in Android settings. To allow one-shot screen facts, use **Shiv > Permissions > Open Accessibility Settings**, read the warning and turn on Shiv only if desired; use the 15-second one-shot button to capture an allowed app.
8. For Samsung background reminders later: **Settings > Apps > Shiv > Battery > Unrestricted**, if you elect to allow reminders to run reliably. Device testing remains necessary.
9. For optional cloud AI, open **Shiv > Settings > INTELLIGENCE ROUTING**. Select **CLOUD** or **HYBRID**, enter your provider's OpenAI-compatible chat-completions HTTPS URL and model identifier, then enter your own API key. Check **I consent to sending my prompts to this provider** and tap **SAVE INTELLIGENCE SETTINGS**. Only use a provider you trust: open-ended chat text and an image you explicitly choose for cloud analysis can leave the phone. Select **LOCAL** or clear consent to stop cloud requests. No provider key is included with Shiv, and no live provider has been verified by this audit.
10. For a specific app source, open **Shiv > Permissions > PER-APP SOURCE CONTROL**, enter its full Android package name and choose **INCLUDE**, **NOTIFICATIONS ONLY**, or **EXCLUDE**. This setting does not grant Android permission or arm screen inspection; those remain separate owner actions. Built-in banking and authenticator exclusions cannot be overridden.

## Feature-by-feature evidence and tests

Start with [FEATURE_BY_FEATURE_AUDIT.md](FEATURE_BY_FEATURE_AUDIT.md). It audits **every Phase 1 milestone, every binding owner decision, required scenarios, and Phase 2 prerequisites**. Green automated CI must not be mistaken for completed functionality.

**Audited baseline `6c114476` (PR #8):** [APK, JVM, lint](https://github.com/Navee569/Project-Shiv/actions/runs/40324381275) and [API26/API35 emulator tests](https://github.com/Navee569/Project-Shiv/actions/runs/40324381064). Check the latest CI evidence for any newer changes before installing an APK.

Use **Shiv > Memories** to inspect, correct or delete encrypted facts. Use **Shiv > Decisions** to review candidate commitments; an unambiguous future date/time can be explicitly approved into an encrypted Shiv event and local reminder. In **Events**, the owner can separately grant calendar read/write access, select a writable phone calendar and confirm export of a local event. No external event is written by the commitment approval alone.

## Core modules added in this development branch

- Private Room + SQLCipher database with Android Keystore-wrapped random passphrase; one-way legacy encrypted-preferences migration (old records are deleted only after copying).
- Profile, memory with provenance/confidence, rules, events, tasks, reminders, chat, action logs, model metadata, encrypted signal queue, beliefs, decision traces.
- LiteRT-LM 0.18.0 Brain runtime (not a fake rule-based chatbot), local router/conversation model import, SHA-256, memory/battery checks, inference priorities and error reporting.
- Three language selections (Kannada, Telugu, English) for on-device Android STT and Google TTS; no implicit cloud recognition.
- Save-first WorkManager signal processing, owner-review trace generation, deterministic proactive/quiet-hours policy, charging-only hypothesis learning.
- Deterministic FREE / ACT_THEN_TELL / ASK_FIRST authority engine with a hard ASK_FIRST floor for sensitive actions; no model-controlled tiers.
- Opt-in notification facts with Indian banking/payment/authenticator denylist, one-shot restricted Accessibility facts, reviewed Android handoffs and encrypted event/calendar integration.
- Bundled **offline image labeling**, distinctly labeled from OCR or scene understanding. Cloud image analysis is an owner-approved API exception.
- Original Shiv Compose UI plus Models, AI Status, Events, Beliefs, Decisions, and Permissions.

## Limitations

The audited baseline passed native build, JVM tests, Android lint and API26/API35 emulator checks. Newer commits require their own CI results. **Galaxy S23 device checks and broader acceptance tests remain pending.** This code must not be treated as an install-ready, verified APK. See [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [ARCHITECTURE.md](ARCHITECTURE.md) and the owner requirements files attached to the project conversation.

A number of Phase 1 requirements remain incomplete, notably always-ready wake-word support, fully tested per-source awareness, verified LiteRT formats and delegates, full third-party integration and reversible automation, complete scenario coverage, and a verified device battery/runtime baseline. Portable encrypted backup/restore and a user-started bounded microphone session exist, but need owner/device acceptance. They remain visible in STATUS. **Phase 2 is not started until M11 is accepted.**

If Shiv opens a **storage needs attention** screen, tap **Retry opening** first.
The app does not recreate an unreadable encrypted database silently. If its
Android Keystore key was lost or the database is damaged, recover from a
portable owner backup after starting fresh. **Erase and start fresh** requires
typing `ERASE SHIV STORAGE` and permanently removes Shiv's private records on
this phone; external phone calendar events are unaffected. Imported model
files may remain but need to be selected again.

AI Status lets the owner choose CPU, GPU or NPU for local model loading and a
64–1024 output-token limit. Changing either unloads loaded models; press LOAD
again in Model Manager. GPU/NPU compatibility is model/device dependent and
must be checked on the Galaxy S23. The Chat microphone shows partial offline
speech text and can speak local model sentences as they arrive; saying a new
request stops the prior speech/reply. The owner-started 10-minute mic session
can require the word “Shiv” before each command, pauses during sleep hours
unless overridden, and pauses when proximity plus darkness indicates a pocket.
This phrase gate does not identify the speaker and is not an always-on listener.

When notification summaries or a short screen preview are enabled, Shiv shows
an ongoing awareness notification. Tap PAUSE to stop both sources before
content is read. RESUME restores notification summaries but never rearms a
screen preview. The same controls are in Shiv > Permissions. Allow Shiv
notifications so the control can appear; capture stays off if Android blocks
the notification permission or channel.

Neither Shizuku nor root/kernel tooling is part of the current implementation. Shiv does not intercept cellular call audio.
