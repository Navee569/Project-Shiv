# Shiv verification: verified CI evidence and outstanding release gates

## Automated CI evidence — verified

**Latest source commit: `8e48ed0`.**

- Debug APK, static checks, JVM unit suite and Android lint: **PASS**, [run 40259210693](https://github.com/Navee569/Project-Shiv/actions/runs/40259210693).
- Instrumented Android 8.0 (API26): **14/14 PASS** and Android 15 (API35): **14/14 PASS**, [run 40259210606](https://github.com/Navee569/Project-Shiv/actions/runs/40259210606).
- Final `shiv-native-debug-apk` artifact verified (68,202,553 bytes); debug-only, not a release build.
- New regressions include 37-signal backlog, owner approval and idempotence, chat-signal erasure, note correction, model hash checks, wrong DB key, rollback and concurrent writes.


- Build / APK assembly, Kotlin JVM tests and lint: PASS — https://github.com/Navee569/Project-Shiv/actions/runs/40220199926
- Android API 35 emulator: 4 tests PASS (SQLCipher file header and round-trip, encrypted vault, denylisted input, malformed tool checks) — https://github.com/Navee569/Project-Shiv/actions/runs/40220199919
- App-launch and Activity recreation smoke test: PASS — full 5/5 API 35 suite, https://github.com/Navee569/Project-Shiv/actions/runs/40220610385
- Security static checks: PASS, integrated into native workflow.
- **No coverage percentage claimed.** Emulator is not Samsung Galaxy S23.



## Additional tests required before release

1. Expand existing successful compile/unit/lint, SQLCipher wrong-key and encrypted-header checks to real device and additional failure modes.
2. Add real legacy-data migration interruption, Keystore invalidation, reboot, expiry cleanup and WAL/integrity failure tests. Correction, concurrency and rollback are already covered.
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


## Verify corrected parity-branch development additions

Start from the **successful final SHA's** CI APK, not an artifact from main.

1. Permissions > Calendar: decline permission and verify zero imports. Grant it,
   preview temporary entries, discard and verify no event was written. Preview
   again, approve one permitted non-sensitive event, and verify an encrypted
   event and redacted action log. Revoke READ_CALENDAR before approval;
   importing must stop. Repeat approval; it must not duplicate the event.
2. Permissions > Accessibility: add the full package identifier of a harmless
   test app, enable Shiv service manually in Android Settings, then ARM FOR 15S.
   Switch to a different app and confirm nothing is captured; switch to the
   exact allowed app and verify only possible date/time facts. A banking/OTP
   package or password field must never be recorded. STOP immediately
   disarms. Secure/FLAG_SECURE windows still need explicit real-device review.
3. Permissions > Reminder Authority: choose ASK_FIRST and review an explicit
   10-minute reminder. Switch to ACT_THEN_TELL, issue another explicit
   reminder, and verify it can be canceled by UNDO. Repeat undo: no second
   cancellation should be claimed. Messages and UPI must still require review.
4. Decisions > APPROVED event: press Undo and verify its local event is absent,
   pending reminder shows CANCELLED, and the decision remains UNDONE in the
   encrypted audit. Repeated undo does not duplicate changes.
5. Permissions > Contacts: grant READ_CONTACTS for a one-shot search. Confirm
   only a few names display, with no phone numbers/emails or saved copies.
   Revoke the permission and confirm no further reads. Test empty queries.
6. Repeat the latest APK build, JUnit/lint, and instrumented Android API26 and
   API35 suites. Emulator passes do not prove Galaxy S23 background behavior,
   Kannada/Telugu speech, real LiteRT model performance or power consumption.
