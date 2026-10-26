# Shiv owner-portable backup and offline microphone session

This guide covers the implementation added on the audited
`development/shiv-feature-parity-audit` lineage. Cloud AI API implementation
remains unchanged.

## Passphrase-protected backup

Open **Permissions → Portable encrypted backup / restore**.

1. Choose **Export encrypted** and enter the same unique 12–256 character
   passphrase twice. Use the Android document picker to save a `.shiv` file.
2. Keep both the document and its passphrase independently. Shiv cannot recover
   the password. It never uploads or writes an unencrypted JSON backup file.
3. On the destination device open **Restore archive**. Enter the passphrase and
   type `RESTORE SHIV` before selecting the file.
4. Import replaces the existing Shiv private owner records; it is not a merge.
   Confirm the new data after restarting Shiv. Model weights are intentionally
   not portable; reimport compatible `.litertlm` files on the destination.
5. Restored reminders are labeled `RESTORED_INACTIVE`, previously queued
   background signals are inert, old ASK decisions are archived, privileged
   action tiers revert to ASK_FIRST, and cloud consent returns to LOCAL/off.
   The restored API secret remains encrypted but cannot be used without fresh
   owner consent. Reconfigure any ongoing reminders manually.
6. File size is bounded to 12 MB of plaintext owner records. Large or corrupted
   archives refuse to import; data validation happens before an all-or-nothing
   SQLCipher transaction. Password validation uses PBKDF2-HMAC-SHA256 (210,000
   iterations), AES-256-GCM, unique salts and nonces, with authenticated headers.
   The app never stores the archive passphrase.
7. A backup is not secure deletion of previously exported copies. Destroy any
   unneeded archive files through your chosen storage provider.

**Limitations:** Binary model files and device-specific Android permissions are
not part of portable backups. OEM WorkManager timing is not portable. Restores
are owner-initiated and require a real document picker; a complete S23 restore
acceptance test is outstanding.

## Owner-started offline microphone session

Open **Permissions → User-started offline mic session** and choose **Start
offline mic** while Shiv is visible. Grant RECORD_AUDIO explicitly. It never
starts on boot. Android displays an ongoing microphone notification with STOP;
the in-app STOP control also works.

- Requires Android 13+ and an available on-device recognizer and language pack.
  It does not silently switch to cloud speech.
- Lasts **up to 10 minutes**, stops below 25% battery, on severe thermal status,
  or after repeated recognition failures. It may terminate sooner if Samsung
  restricts the recognizer.
- Selected language is en-IN / kn-IN / te-IN. Availability and recognition
  accuracy of Kannada/Telugu depend on installed on-device language data;
  no speech pack is bundled or represented as verified.
- Partial speech and wake words are not persisted. Safe final utterances enter
  the encrypted ASK_FIRST signal queue; sensitive OTP and long digit strings
  are rejected. No messages, calls, payments or calendar events are confirmed
  autonomously.
- It does not record telephone call audio, bypass Android background-launch
  restrictions, provide speaker identification or claim a continuous,
  always-listening battery-efficient wake-word implementation.

## Tests

- JVM codec: randomized encryption, roundtrip, wrong key, tampered ciphertext,
  unsupported headers and short secrets.
- JVM mic policy: low battery, heat, duration, repeated errors and sensitive text.
- Android API26/API35 disposable SQLCipher instrumentation: encrypted export,
  full scoped restore, inactive restored reminders, reset cloud consent, wrong
  passphrase, malformed archive and unchanged existing data.
- Real S23 verification **still needed** for SAF provider behaviour, restore
  across devices, speech-packs, One UI foreground-microphone policy, battery,
  thermal measurements, background process death and model accuracy.

## Offline image text and reviewed due-date tasks

Tap **Vision** on Chat and select an image. The dialog now includes **LOCAL OCR**
alongside **LOCAL LABELS** and the separate, explicitly consented cloud image
API action. LOCAL OCR uses the bundled ML Kit Latin script model entirely on
device; it does not send the image or recognized text to cloud AI.

The temporary OCR preview only offers a due-date task if the visible text has
a strict ISO date such as `Due date: 2026-11-30`. The owner must review the title
and supply an exact future 24-hour time before **SAVE REVIEWED TASK** creates an
encrypted task. Dismiss keeps no task or image copy. Banking/OTP/password-like
recognition results are suppressed before reaching persistent storage.

**Limitations:** OCR can misread photos. Recognition of Kannada or Telugu
scripts is not provided by this bundled Latin model. The screen, chosen
image formats, calendar-time behavior and accuracy require S23 verification.
