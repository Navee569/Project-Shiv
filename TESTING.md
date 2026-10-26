# Shiv testing and owner verification

## CI (automated on GitHub Actions)
- Python static safety checks including manifest permissions.
- :app:assembleDebug, :app:testDebugUnitTest, :app:lintDebug.
- CI proves compilation and deterministic logic only, **not hardware behaviour**.

## OWNER STEPS — Galaxy S23 (must be performed by the owner)
1. On Windows open https://github.com/Navee569/Project-Shiv/actions .
2. Choose the newest **Shiv Native Android** successful run and download the
   shiv-native-debug-apk artifact, or open Android Studio and Run > Run 'app'.
3. On the Galaxy S23 enable installation from the chosen file/app only if you
   understand sideloading. Install and open Shiv.
4. Go to **Privacy**; enable PAUSE SENSING and confirm notification summaries are
   not collected. Turn guest mode on/off and ensure it is still off after restart.
5. In **Settings**, test Notification Access using Settings > Notifications >
   Advanced settings > Notification access (Samsung wording may differ).
   Send a harmless notification from a calendar app; confirm an encrypted summary.
   Send an OTP or banking/UPI notification; confirm NO record appears.
6. Under **Models**, select a .litertlm file through My Files. The app will
   register the reference. Tap LOAD OFFLINE MODEL. It may need another ~0.6–4 GB
   to copy into app-private storage. Confirm an offline prompt works with Wi-Fi
   and mobile data disabled. Record model path, size, load time and any error.
7. In **Status**, tap MEASURE NOW. Note battery percentage, charging state,
   thermal state, RAM and queue count. Do not invent performance results.
8. In **Settings**, select en-IN, kn-IN and te-IN in turn; use Voice then Speak.
   A missing offline pack must show a failure, not fall back to online speech.
   Install Google speech/voice data in Galaxy Settings > General management >
   Language > Text-to-speech > Preferred engine > Speech Services by Google >
   gear > Install voice data. Repeat with network disabled.
9. Save a memory, correct it, delete another, restart the app and check results.
10. Keep the phone unplugged for a normal day, compare Samsung Settings > Battery >
    Battery usage and note whether Shiv is a significant source of battery drain.
11. Test notification permission denied, airplane mode, thermal throttling,
    low battery, reboot, duplicate signals, model cancellation and storage full.
12. Never assert recording other people's calls; Android call audio capture is
    outside scope.

## Record actual results
- Device / Android / One UI version: NOT TESTED
- CI commit SHA: RECORD FINAL SHA
- Model / runtime / load status: OWNER-VERIFY
- Kannada STT / TTS: OWNER-VERIFY
- Telugu STT / TTS: OWNER-VERIFY
- English STT / TTS: OWNER-VERIFY
- Battery additional drain: OWNER-VERIFY
- Local tokens/s and time to first token: OWNER-VERIFY
- Notification denylist: OWNER-VERIFY
- Accessibility/ADB/Shizuku: NOT IMPLEMENTED
- Final functional acceptance: NOT COMPLETE
