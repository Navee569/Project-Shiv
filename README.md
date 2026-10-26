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
a selectable small ROUTER and conversation CHAT role now offer one-engine
resource-governed on-demand switching (PR #3). In Models select SET ROUTER and
SET CHAT after importing separate files. On the Status screen, select a daily
model-compute budget and press MEASURE NOW. Real model loading, performance,
privacy and energy measurements still need S23 device acceptance. This build
does **not** keep two neural engines resident simultaneously and does not keep two models resident; the owner-selected large conversation model
is set to release after two idle minutes, a behavior still requiring device testing.
Energy and battery calibration remain unverified.

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

For a feature-by-feature audit, including unimplemented families and real-phone
release blockers, see [docs/FEATURE_AUDIT_2026-10-26.md](docs/FEATURE_AUDIT_2026-10-26.md).
Existing cloud/hybrid AI integration remains unchanged.


## Permissioned anticipation and Android awareness (new development path)

Automate includes optional quiet-hour/cap-limited agenda nudges, morning/evening briefs,
evidence-based routine hypotheses, a temporary preview of Android calendar items,
on-demand Contacts name lookup, aggregate Usage Access preview, and generic
after-call check-ins. These features are disabled until the owner grants the
corresponding permission or opts in. Calendar events are stored only after clicking
IMPORT PREVIEWED EVENTS; Contacts and Usage previews are never retained.

Privacy provides one-shot Accessibility inspection. The owner must explicitly
enable the service in Android settings AND add a specific source-package allowlist;
arming lasts at most 15 seconds. The feature extracts only possible scheduling
facts and never persists raw screen text. An opt-out/kill switch is present. Android
may suppress or redact secure windows; do not rely on this for financial apps.

The weekly report uses the owner's own reactions, not assumed real-world success.
All collected persistent records remain encrypted. Optional features and actual
runtime operation still require Galaxy S23 testing. See TESTING.md. The cloud
AI API, including the previously configured hybrid mode, is unchanged.
