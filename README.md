# Shiv — personal Android beta source

Shiv is a private, offline-first Android assistant project for one Galaxy S23. The source includes encrypted local storage, chat, basic event extraction, event reminders, voice input/output, visible listening mode, calendar/notification access, photo labels, editable action tiers, password-encrypted backup/restore, and an optional on-device language model.

**This is source code, not a finished APK or a verified release. It has not been built or run.** The new LiteRT-LM integration, the Room migration, backup/restore, and S23 behavior all need Android Studio/device verification before you rely on them. The model tool agent, proactive manager loop, quiet hours/interruption limits, and several broad permissions are not complete; details are in “Known limits.” This is not yet the full human-like manager described in the project brief.

## Setup for a beginner

1. Install Android Studio from [developer.android.com/studio](https://developer.android.com/studio). During first launch, accept the Android SDK setup. The project needs Android SDK Platform 35 and JDK 17; in Android Studio open **Settings → Build, Execution, Deployment → Build Tools → Gradle** and select the bundled JDK 17 as the Gradle JDK.
2. Open the private [Project Shiv repository](https://github.com/Navee569/Project-Shiv) and switch to branch `codex/shiv-complete-beta`. Choose **Code → Download ZIP**, extract it, then in Android Studio choose **Open** and select the extracted project folder (the folder containing `settings.gradle.kts`). Do not create a second Android project.
3. Let Android Studio sync Gradle and install its requested free Android build tools. The app has no AI API key, cloud model account, network permission, or paid service. Standard Android Studio / Gradle dependencies need internet during the first sync.
4. On the S23, open **Settings → About phone → Software information**, tap **Build number** seven times, then return to **Settings → Developer options** and enable **USB debugging**. Connect with a data-capable USB cable, unlock the phone, and accept its USB debugging prompt.
5. In Android Studio select the **Shiv** app configuration and your Galaxy S23, then press **Run ▶**. This project has not yet been verified to build; if Android Studio reports an error, save the complete error text before changing files.

## Optional on-device chat model

The generative model is optional and is not included in Git or the app. Until one is installed, Shiv says so plainly; deterministic event/reminder parsing remains available. LiteRT-LM’s [Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) runs `.litertlm` model files on Android. The integration attempts GPU and falls back to CPU. It keeps one inference at a time.

1. Create a free Hugging Face account, open the model pages below, and accept the Gemma terms if requested. These are free downloads but use internet data and several hundred megabytes of phone storage. No model cost is charged.
2. For the small router, download `mobile_actions_q8_ekv1024.litertlm` from [FunctionGemma 270M mobile actions](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions).
3. For chat, download `gemma3-1b-it-int4.litertlm` from [Gemma 3 1B IT LiteRT](https://huggingface.co/litert-community/Gemma3-1B-IT).
4. Copy the model files to the phone using USB or the phone’s Files app. In Shiv open **Settings → Local AI models → Choose fast model / Choose chat model**, then select each downloaded file. Shiv copies it into its private app storage. Models are not part of the encrypted personal database backup; keep your original model downloads if you may need them again.

The model files are large and the memory/runtime impact on the S23 has not been measured for this app. Start with the chat model only if storage or responsiveness is a concern. The app does not download models itself.

## What the current source does

- **Local encrypted database:** Room with SQLCipher. A random SQLCipher database key is wrapped by an AES-GCM key held in Android Keystore. The manifest requests no internet permission. Android Auto Backup is disabled. Existing database version 1 is preserved by a migration to version 2.
- **Backup:** Settings can create an AES-GCM encrypted `.shivbackup` protected by a passphrase (PBKDF2-HMAC-SHA256). Restore replaces existing Shiv data only after the passphrase verifies and the complete backup parses. Keep the passphrase safe: it is never saved and cannot be recovered. Export a backup before uninstalling or clearing app storage.
- **Chat and memory:** profile, chat history, saved facts, events, reminder rule, drafts, and recent action history are available in the app. Basic event extraction is deterministic, so it does not need a model. Generic chat can use an imported local LiteRT-LM model. No cloud AI is used.
- **Events/reminders:** basic dates with explicit weekday/relative day and time can be extracted from chat. AlarmManager reminders are restored after reboot. Android may use an inexact fallback unless exact-alarm access is enabled.
- **Voice/listening:** one-shot dictation and the explicitly started foreground listening service request Android’s on-device speech recognizer. Shiv stores only recognized event fields, not audio or transcripts. Offline speech availability depends on Android’s installed language model.
- **Camera:** only opens after you tap the photo button; bundled ML Kit image labels provide a simple local guess. It is not a rich visual description model, and photo analysis has not been verified on your phone.
- **Calendar/notifications:** calendar sync is read-only. Optional Notification Listener access inspects notification text in memory and stores only recognized events.
- **Action tiers:** the Settings screen stores configurable minimum tiers for supported actions; raising event, reminder, memory/profile, reschedule, or draft actions to ASK_FIRST triggers a confirmation. Sending a message, deleting data, and other destructive/people-facing actions remain confirmation-gated. Android permission grants (such as notification access) are also controlled by Android Settings.

## Privacy, recording, and encryption

The assistant database is encrypted at rest using SQLCipher, and the key is wrapped by Android Keystore. To verify the database, find `shiv-memory.db` under Android **Settings → Apps → Shiv → Storage** only if your file manager can access app-private storage (normally it cannot without debugging/root). A copied raw file should not open as a normal SQLite database in DB Browser for SQLite. This is a practical check, not an independent security audit. Do not share the encrypted database or backup as though encryption makes it harmless.

Listening uses a visible ongoing Android notification and saves event fields only. Get consent before listening to other people; recording laws vary by location. Shiv cannot record the other side of a cellular phone call, continuous camera access is not implemented, and Android may limit microphone access during calls. Stop listening when it is not needed.

## Permissions and phone settings

Grant only the permissions for features you choose. Android may ask at first use:

- **Microphone** — dictation and user-started listening mode.
- **Camera** — on-demand photo capture.
- **Notifications** — reminder and listening notifications (Android 13+).
- **Calendar read** — optional read-only calendar sync.
- **Notification access** — optional special access under **Settings → Notifications → Device & app notifications** (wording differs between One UI versions). This lets Shiv inspect visible notification text.
- **Alarms & reminders / exact alarms** — optional special access under **Settings → Apps → Special app access → Alarms & reminders**. Without it, Android can delay reminders.
- **Foreground microphone service** — the app declares Android 14’s microphone foreground-service type; Android shows the active listening notification while it runs.
- **Samsung background allowance:** for user-started long listening and reliable background alarms, open **Settings → Battery → Background usage limits → Never sleeping apps → + → Shiv**. If shown, also open **Settings → Apps → Shiv → Battery → Unrestricted**. This can use more battery. Samsung may still stop background work in power-saving conditions.

Shiv does not request contacts, SMS sending, location, phone-call recording, or internet access. Notification reading is not the same as unrestricted access to messages; some apps do not expose their full contents in notifications.

## Known limits and not-yet-finished brief items

- The on-device model integration supports optional local `.litertlm` import and GPU/CPU inference. It has not been built or tested. The fast model is currently used as a simple message router, while the chat model writes generic responses; this is **not yet** the requested strict JSON tool-calling agent with schema validation/retry and complete per-tool tier enforcement.
- There is no continuous proactive planning loop, event-approaching brain check, unlock-after-gap trigger, learned habit model, daily interruption limit, quiet-hours setting, device temperature/queue dashboard, or stale-priority inference queue. Reminders are deterministic alarms; they are not proof that a human-like manager is monitoring your day.
- Listening works only while you explicitly start a visible foreground service. Android’s on-device recognizer may not be available for your language/device. It cannot reliably hear every in-person promise and is not a call recorder.
- Dates/times are parsed by limited local rules. “Thursday at 5” without a clear AM/PM is assumed to be 5 PM for hours 1–7; inspect events before relying on them. Vague times are not yet resolved by a follow-up agent workflow.
- Contacts, general inbox/message reading, location context, usage stats, direct message sending, and spending-money actions are not implemented. Shiv opens a message composer for review instead of sending.
- Notifications, alarms, background services, and speech recognition vary by Android version and Samsung battery settings. Verify each reminder with a non-critical test event.
- No successful Android build or S23 run has been verified. Treat this as an early beta source snapshot, not a finished release.

## Try-it checklist after a successful build

1. Save a profile and a memory (for example, “Remember that I prefer reminders 30 minutes early”), then confirm it appears in the Memory tab and action log.
2. Tell Shiv: “Meeting with Raj Thursday at 5 at the station.” Check the date, PM assumption, person/place, and reminder in Events.
3. Change a tier such as “Save an event or reminder” to **ASK_FIRST** and confirm the next matching chat event waits for confirmation.
4. Create an encrypted backup with a passphrase. Restore it only after confirming that replacement of local data is intended. Try an incorrect passphrase against a spare copy; it should fail without replacing the database.
5. Enable notifications and exact alarms, create a non-critical reminder, then check it after reboot and after setting Shiv as a Never sleeping app.
6. Test one-shot dictation, photo labels, calendar sync, notification listener, and listening mode individually. Verify no transcript appears in chat, memory, or the action log.
7. Install an approved local model and check Settings status, local chat, GPU/CPU fallback, responsiveness, and phone temperature. Model behavior/performance needs real S23 testing.

## Data loss and cost

Uninstalling Shiv or clearing app storage deletes its local data and Keystore-wrapped database key. Use and verify an encrypted backup before doing that. A wrong/lost backup passphrase cannot be recovered. No cloud AI or paid API is connected. Android Studio, Gradle dependencies, and the optional model downloads are free, but downloads may use your internet data plan.
