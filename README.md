# Shiv — native Android personal intelligence

**Status: experimental development, not production-cleared.**

This is an original Android app in package \`com.shiv.assistant\`, not an APK assembled by renaming OpenDroid. Its code, Compose user interface, capability registry, consent model, intelligence router and storage system are Shiv-owned implementations. The original Shiv README is preserved in [docs/ORIGINAL_SHIV_README.md](docs/ORIGINAL_SHIV_README.md); original working application sources were not available in GitHub.

## Product

- Futuristic Compose dashboard, aurora backgrounds and original Shiv orbital design.
- **Configurable intelligence:** LOCAL (deterministic offline), CLOUD (user-selected OpenAI-compatible HTTPS API) or HYBRID (offline commands plus opt-in cloud text). No outbound prompts without opt-in.
- Encrypted personal records using Room + SQLCipher with a random database key
  wrapped by Android Keystore AES-GCM. Existing encrypted SharedPreferences are
  migrated lazily. Device backup remains disabled.
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

A real LiteRT-LM on-device neural runtime is now integrated: import a .litertlm
file through Models, then choose LOAD OFFLINE MODEL. It requires an app-private copy
of the model because the runtime expects a filesystem path; allow ample storage
and verify actual loading on your Galaxy S23. The initial engine uses CPU;
router residency, larger-model resource arbitration and speed still need
device acceptance.

Full learned knowledge graphs, habitual autonomy, live third-party UI automation,
authenticated social publishing/analytics, complex bookings, privileged tools,
wakeword and live screen capture remain outstanding. See [SHIV_CAPABILITY_MATRIX.md](SHIV_CAPABILITY_MATRIX.md).

A build result is not a complete test or production readiness claim. No emulator/real-device and full third-party acceptance testing has been completed.


## New privacy and safety controls

Privacy provides optional sensing pause, Guest Mode, manual privacy zone, app
package exclusions and an audit log (protected while locked). Memory and payment
handoffs use a biometric/device-credential gate. Status measures battery,
thermal state and available RAM on the phone; no CI-generated values are used.
The cloud and hybrid AI modes are intentionally preserved as the owner's exception.

See STATUS.md, TESTING.md, DEFECT_LOG.md, ARCHITECTURE.md and
docs/ENCRYPTED_MIGRATION.md. Feature presence is not release acceptance.


## Offline OCR (Latin only)

Chat > Offline OCR opens a user-selected image. The app uses the bundled
ML Kit Latin text recognizer and displays a short ephemeral preview only;
recognized text is not automatically saved to the encrypted database or sent
to cloud AI. Kannada/Telugu-script OCR and secure identification of the source
app are not supported. Do not enable broad screen scanning.
