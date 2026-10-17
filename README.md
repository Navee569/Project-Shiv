# Shiv — private Android beta

Shiv is a personal, offline-first assistant for your own Android phone. The beta keeps its profile, conversations, memories, events, reminders, drafts, and action log in an encrypted database on the phone. It has no network permission, does not call a cloud AI, and does not need an API key or paid service.

> This is a beta project, not a verified release APK. The assistant's `Brain` uses small on-device rules for replies, memory, and event extraction. Speech recognition and photo labels depend on Android/device support. Read the limitations below before relying on reminders.

## Easiest way to install and run

1. Install the current stable Android Studio from the official Android developer site. During first launch, accept the Android SDK setup and install Android SDK Platform 35 if Android Studio does not install it automatically. The project uses the Gradle wrapper and needs JDK 17; choose Android Studio's bundled JDK in **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
2. Download this repository (GitHub's **Code → Download ZIP**) and extract it. In Android Studio choose **Open**, then select the extracted `Project-Shiv` folder. Do not choose **New Project** or paste individual files.
3. Wait for Gradle sync and approve Android Studio's request to download the standard build tools/dependencies. These downloads are free; no model/API key or paid account is used. The bundled Gradle wrapper is version 8.9.
4. On the Galaxy S23, open **Settings → About phone → Software information**, tap **Build number** seven times, then return to **Settings → Developer options** and enable **USB debugging**. Connect the phone with a data-capable USB cable, unlock it, and accept the “Allow USB debugging” prompt.
5. In Android Studio select **Shiv** and your Galaxy S23 in the device selector, then press the green **Run ▶** button. Android Studio may offer to install the free SDK platform/build tools first.
6. Grant only the permissions for features you want when Shiv explains them in Settings. You can change them later in Android **Settings → Apps → Shiv → Permissions**.

## What is in this beta

- **Chat and memory:** brief, warm local responses; saved profile facts and preferences are recalled in replies. A correction such as “remind me 30 minutes early, not 10” updates the local reminder rule.
- **Events:** chat and listening-mode phrases can be parsed into an event with a title/time and optional person/place. Review upcoming events in the Events tab.
- **Reminders:** alarms, a notification, and an optional spoken reminder; event alarms are scheduled again after a reboot. Android may delay alarms under battery restrictions. Grant exact-alarm access for precise alarms; Shiv falls back to inexact alarms when it is not granted.
- **Voice:** press the microphone to dictate a message. Shiv can read replies/reminders using an installed offline text-to-speech voice.
- **Listening mode:** an explicit user-started foreground service shows an ongoing notification while using the microphone. It uses Android's on-device speech recognizer where available, extracts an event, persists only that event, and discards the transient recognition text. Use the notification's Stop action to end listening. Android/device support for an offline recognizer varies; if unavailable, listening reports that instead of silently using an online recognizer.
- **Calendar and notifications:** calendar import requires calendar-read permission. Notification access is a separate Android special-access setting; only recognizable event details are retained.
- **Camera:** opens only after you tap the camera button. Photo labels are analyzed on-device and the captured image is not saved by Shiv.
- **Action safety:** FREE covers local notes, memories, alarms, and reminders; ACT_THEN_TELL covers local rescheduling and drafting; ASK_FIRST gates destructive deletion and message handoff. For message handoff, Shiv opens the messaging composer for you to review and send; it does not send messages by itself.

## Privacy and security

Room data is encrypted with SQLCipher. A random database key is wrapped by an Android Keystore AES-GCM key; only the wrapped key is stored in app preferences. No API key is required, and the app manifest does not request internet access. Android backups are disabled to avoid copying the encrypted database/key material through device backup. Uninstalling the app or clearing its storage removes local data; keep that in mind before doing so.

## Phone permissions and settings

The app asks only when a feature is used. Depending on your Android version and features enabled, you may see:

- **Microphone** — voice dictation and listening mode.
- **Notifications** — reminder and foreground-listening notifications (Android 13+).
- **Camera** — on-demand photo description.
- **Calendar read access** — import existing events. Shiv does not write to your calendar.
- **Notification access** — optional special access under **Settings → Notifications → Device & app notifications** (wording varies by One UI version).
- **Alarms & reminders / exact alarms** — optional special access in **Settings → Apps → Special app access → Alarms & reminders**. Without this, reminders use an inexact fallback.
- **Samsung background allowance:** for listening that you start, go to **Settings → Battery → Background usage limits → Never sleeping apps → + → Shiv**. You can also open **Settings → Apps → Shiv → Battery** and select **Unrestricted** if your One UI version shows it. This increases battery use while long-running listening is active; stop listening from the notification when finished. Samsung and Android can still stop processes under extreme conditions.

Boot-reschedule and foreground-service declarations are handled by Android and are not additional runtime permission prompts. Shiv does not request contacts, SMS-send, location, or internet permissions.

## Important beta limits

- The local `Brain` is a simple rule-based beta, not a general conversational AI. It does not reason like a cloud LLM.
- Event extraction understands a limited set of natural-language dates/times. Check parsed events, especially dates without a clear AM/PM or time zone.
- Android's on-device speech recognition is not available on every phone/language. Shiv will explain when it cannot start; it will not upload audio as a fallback.
- Photo analysis returns simple object labels, not a rich free-form visual description.
- Listening is visibly active only after you start it. It is not a hidden always-on microphone and should not be treated as a reliable real-time transcription system.
- Notifications, exact alarm delivery, and background execution remain subject to Android and Samsung battery policies. Test reminders before relying on them for important appointments.
- This source project has not been built on the authoring computer because Android Studio/JDK 17/Android SDK are not installed there. A successful Android Studio build and Galaxy installation must be verified on your computer before relying on it.

## Data loss and cost

Clearing app data or uninstalling Shiv deletes its local encrypted memories and event history. There is no export/recovery flow in this beta. No cloud AI or paid API is connected, so the app makes no model/API charges. Android Studio's initial free SDK/dependency downloads may use your internet connection and data plan.
