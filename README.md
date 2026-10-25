# Shiv — native Android personal intelligence

**Status: experimental development, not production-cleared.**

This is an original Android app in package \`com.shiv.assistant\`, not an APK assembled by renaming OpenDroid. Its code, Compose user interface, capability registry, consent model, intelligence router and storage system are Shiv-owned implementations. The original Shiv README is preserved in [docs/ORIGINAL_SHIV_README.md](docs/ORIGINAL_SHIV_README.md); original working application sources were not available in GitHub.

## Product

- Futuristic Compose dashboard, aurora backgrounds and original Shiv orbital design.
- **Configurable intelligence:** LOCAL (deterministic offline), CLOUD (user-selected OpenAI-compatible HTTPS API) or HYBRID (offline commands plus opt-in cloud text). No outbound prompts without opt-in.
- Encrypted memory, chat history, provider credentials, and macros using Android Keystore AES-GCM; device backup disabled.
- Bounded multi-step plans, reviewed external Android handoffs, local relative reminders, audio dictation and TTS, explicit cloud image analysis, document import, and optional notification metadata.
- No background accessibility automation, silent posts, SMS, financial transfers or root shell.

**Reference to OpenDroid is an architecture/capability study, not imported Android application code.** See [reference study](docs/REFERENCE_STUDY.md).

## Setup

Install JDK 21, Android SDK 36, Gradle 9.7.0, then:

    python3 scripts/verify_shiv.py
    gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

CI publishes the native Shiv debug APK as the \`shiv-native-debug-apk\` artifact; this is not a release-signed APK.

## Requirements and limitations

The in-app feature matrix includes every capability family identified in the reference study, and labels items \`ACTIVE\`, \`CONNECT\`, \`PERMISSION\` or \`PENDING\`. **A visible module is not proof that its complete integration is implemented.**

Full offline neural model execution, model download, semantic knowledge graph, habit inference, full third-party UI automation, authenticated social publishing/analytics, complex bookings, privileged terminal/MCP, wakeword and live screen capture remain outstanding. See [SHIV_CAPABILITY_MATRIX.md](SHIV_CAPABILITY_MATRIX.md).

A build result is not a complete test or production readiness claim. No emulator/real-device and full third-party acceptance testing has been completed.
