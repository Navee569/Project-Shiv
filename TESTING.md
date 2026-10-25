# Shiv verification: verified CI evidence and outstanding release gates

## Automated CI evidence — verified

- Build / APK assembly, Kotlin JVM tests and lint: PASS — https://github.com/Navee569/Project-Shiv/actions/runs/40220199926
- Android API 35 emulator: 4 tests PASS (SQLCipher file header and round-trip, encrypted vault, denylisted input, malformed tool checks) — https://github.com/Navee569/Project-Shiv/actions/runs/40220199919
- App-launch and Activity recreation smoke test: PASS — full 5/5 API 35 suite, https://github.com/Navee569/Project-Shiv/actions/runs/40220610385
- Security static checks: PASS, integrated into native workflow.
- **No coverage percentage claimed.** Emulator is not Samsung Galaxy S23.



## Additional tests required before release

1. Compile `assembleDebug`, `testDebugUnitTest`, `lintDebug`; run SQLCipher wrong key and raw SQLite-open checks.
2. Run migration interruption, duplicate, correction, expiry, restart, concurrency, rollback and database integrity tests.
3. Fake Brain: malformed/unknown tools, rejected authority override, queue priority, cancellation, 64-size cap, restart/retry/once behavior.
4. Unit scenarios: commitment -> confirm before calendar write, Kannada/Telugu missing voice, denylisted bank app zero stored data, quiet hours, duplicate proactive suppression, owner correction takes precedence.
5. Emulator scenarios: no permissions, user-denied notifications, app data restore, process death, background limits, reboot WorkManager reschedule.
6. Review merged manifest permissions, native model libraries, hashes, certificate, network endpoints, secrets and model weights before releasing.
7. Device E2E for phone intents, private model import, model load/unload, storage conditions and thermal fallback.

## OWNER-VERIFY — Galaxy S23 (none measured yet)

| Feature | How to check on phone | Result |
| --- | --- | --- |
| Router .litertlm import | Files > browse local model > Models > select router > confirm copy > LOAD | Not checked |
| E2B fit / GPU | Import E2B, load while watching AI Status; note RAM/thermal/battery, time | Not checked |
| Kannada offline STT | In phone language packs install Kannada; Shiv Settings choose kn-IN; speak without internet | Not checked |
| Telugu offline STT | Repeat for te-IN with Wi-Fi/mobile data OFF | Not checked |
| Google TTS voices | Settings > General management > Language > Text-to-speech > Preferred engine > Speech Services by Google > gear > Install voice data | Not checked |
| Accessibility denylist | Manually enable service, try one-shot from *allowed* app and denylisted UPI app; UPI must create zero records | Not checked |
| Android Mic permissions | Start push-to-talk only from visible Activity, deny once then retry with permission | Not checked |
| Notification Access | Grant via Settings; verify denylists and OTPs never appear in memory | Not checked |
| Background WorkManager | Save an event trigger, force stop/kill and reboot; verify no duplicate actions | Not checked |
| Battery/heat | Compare phone Battery usage over multiple days, no phantom background microphone | Not checked |
| Samsung restricted settings | If sideload blocks service, use Settings > Apps > Shiv > ... > Allow restricted settings only after reading permissions | Not checked |
| Calls | Confirm no interception/recording of cellular call audio | Not checked |

Acceptance requires recording observations and resolving critical defects before any Phase 2 entry.
