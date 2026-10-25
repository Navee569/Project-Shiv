# Shiv Android

Shiv is an offline-first Android assistant beta starter built with Kotlin and Jetpack Compose.

## Included

- Compose chat interface with a simple local assistant response loop.
- Room chat/event storage encrypted through SQLCipher. The database key is random and wrapped by a non-exportable Android Keystore AES-GCM key.
- Small, deterministic on-device reminder extraction for phrases such as: Remind me to call Alex tomorrow; Remember to submit the form in 3 days.
- WorkManager scheduled notifications. Android 13+ notification permission is requested when a reminder is sent; if permission is denied, the reminder is saved but no notification is shown.

## Build

Open this folder in Android Studio (Hedgehog or newer), allow Gradle sync, then run the app configuration on an Android 8.0+ device or emulator. Requires Android SDK 35 and JDK 17. No API keys or paid services are used.

## Beta limitations

The assistant is intentionally local and rule-based; it does not call an LLM or access contacts, calendar, microphone, location, or other private data. Reminder extraction accepts relative days only and schedules 9:00 local time. Add event review/edit flows before broader beta distribution.
