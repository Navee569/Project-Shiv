# Shiv — private personal Android manager

**Development branch:** `feature/shiv-phase1-implementation`. This is source development, not a finished release.

Shiv is his own Android app, built with Kotlin + Jetpack Compose. He keeps local data encrypted, helps with owner-approved actions, and can use **two locally imported LiteRT-LM models**: a small router and an on-demand conversation model. A **cloud AI API is an explicit exception**, available only when the owner switches to cloud/hybrid and opts in. Nothing contacts a cloud provider simply because a setting screen exists.

## Windows / Android Studio — OWNER STEPS

1. In GitHub, open the `feature/shiv-phase1-implementation` branch and select **Code > Download ZIP**.
2. Extract the ZIP, open **Android Studio > File > Open** and select its `Project-Shiv` folder.
3. Install the Android SDK 36 and use **JDK 21** in **Settings > Build, Execution, Deployment > Build Tools > Gradle**.
4. Sync Gradle. Select the `app` run configuration and a connected Galaxy S23 to build/run when you start testing.
5. For local models, download the `.litertlm` weights yourself onto the phone (they are **not in the APK**). Open **Shiv > Models > SELECT MODEL FILE > choose router/conversation > CONFIRM APP-PRIVATE COPY > LOAD**. Expect a large duplicate copy because the LiteRT runtime requires an actual filesystem path. The 1B and E2B candidate models need on-phone verification.
6. For Kannada/Telugu speaking voices: **Settings > General management > Language > Text-to-speech > Preferred engine > Speech Services by Google > gear > Install voice data**.
7. To allow optional notification awareness, use **Shiv > Settings > notification switch** and manually grant access in Android settings. To allow one-shot screen facts, use **Shiv > Permissions > Open Accessibility Settings**, read the warning and turn on Shiv only if desired; use the 15-second one-shot button to capture an allowed app.
8. For Samsung background reminders later: **Settings > Apps > Shiv > Battery > Unrestricted**, if you elect to allow reminders to run reliably. Device testing remains necessary.

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

Phase 1 Android compilation, JVM unit tests, Android lint and API 35 emulator encryption/privacy checks have run successfully on verified development commits. **Galaxy S23 device checks and broader acceptance tests remain pending.** This code must not be treated as an install-ready, verified APK. See [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [ARCHITECTURE.md](ARCHITECTURE.md) and the owner requirements files attached to the project conversation.

A number of Phase 1 requirements remain incomplete, notably complete continuous mic duty cycling, wake-word support, per-source permissions/awareness, tested LiteRT formats and delegates, recovery/restore, full scenario suite, and a verified device battery/runtime baseline. They remain visible as incomplete in STATUS. **Phase 2 is not started until M11 is accepted.**

Neither Shizuku nor root/kernel tooling is part of the current implementation. Shiv does not intercept cellular call audio.
