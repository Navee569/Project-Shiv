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
   Under Tasks, add an event tomorrow at 17:00, restart and ask "What's on tomorrow?".
   Verify the answer matches the actual encrypted agenda; test deleting the event.
   With Guest Mode active, verify Chat, Memory, Settings and Tasks are concealed.
10. In Chat, use Offline OCR to select a harmless printed English/Latin-script
    note. Verify a temporary preview appears with mobile data and Wi-Fi OFF;
    use CLEAR PREVIEW. Restart Shiv and verify the text was NOT persisted.
    Verify images containing OTP/password information are blocked. Do not
    use this recognizer for Kannada/Telugu scripts; unsupported.
11. Keep the phone unplugged for a normal day, compare Samsung Settings > Battery >
    Battery usage and note whether Shiv is a significant source of battery drain.
12. Test notification permission denied, airplane mode, thermal throttling,
    low battery, reboot, duplicate signals, model cancellation and storage full.
13. Never assert recording other people's calls; Android call audio capture is
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

## OWNER-VERIFY: reminder authority
- Open Shiv > Settings > Reminder Authority. Choose ASK_FIRST: request a reminder and confirm review dialog.
- Choose ACT_THEN_TELL: say "remind me in 10 minutes to drink water". Check automatic scheduling message.
- Tap Undo latest reminder; verify cancellation in the device scheduler. A notification already delivered cannot be recalled.
- Approve five ASK_FIRST reminders: a trust-ladder suggestion may appear, but the tier must not change without tapping its radio button.
- Payment, messaging and permanent deletion must still require explicit confirmation; model text may not change policy.

## OWNER-VERIFY: two-role routing and daily local compute cap (PR #3)
1. Install the matching **successful CI** APK on the S23. Turn Wi-Fi and
   mobile data off so local routing cannot be confused with cloud responses.
2. In Models, import the owner's small `.litertlm` file; select SET ROUTER.
   Import the separate conversation `.litertlm` file; select SET CHAT.
   These selections authorize app-private staging, which can consume additional GB.
3. In Status select 10m as the daily limit and press MEASURE NOW. Record battery,
   charging, thermal, free RAM, selected model names and measured model seconds.
4. In Chat, ask a harmless open question. Observe which model loads. If charging
   and adequate RAM, conversation model may be chosen; on a hot/low-RAM phone,
   router should be chosen or model inference safely refused. Do not purposely
   overheat the phone to force an edge case.
5. In Status set the daily budget to 1m. Check that subsequent local inference
   respects measured consumed time. The cap is approximate and not a battery
   percentage; a one-time in-flight native cancellation may be delayed.
6. Cancel a long-running local request, then ask a second question. Verify the
   new request is not stuck and priority queue recovers.
7. After using a selected CHAT model, leave Shiv idle for over two minutes;
   confirm in Models that it unloads (or record a failure). Unload a model
   before REMOVE REFERENCE. Confirm the original document still
   exists in My Files and free storage reflects release of the Shiv staged copy.
8. Confirm CLOUD/HYBRID settings and existing explicit consent path still work
   as originally configured; do not enter credentials into shared logs.
9. Record pass/fail and exact observed timings here; NONE of these tests has
   been run on the S23 from CI.

- Small router model loaded on S23: NOT TESTED
- Conversation model loaded on S23: NOT TESTED
- Resource-based automatic swap/fallback: NOT TESTED
- Native cancellation/timeout on S23: NOT TESTED
- Measured battery/thermal/real-world power use: NOT TESTED
