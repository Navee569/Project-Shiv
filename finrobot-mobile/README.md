# FinResearch Mobile — Android prototype (NOT the official FinRobot app)

This is an independent Android MVP inspired by FinRobot's financial research workflows.
It is **not** a full port of FinRobot V0/V2, and is not affiliated with the AI4Finance Foundation.

Version 0.2.1. Features: manual ticker lookup (best-effort Yahoo Finance chart endpoint), locally computed
SMA20/SMA50/simple RSI14 and historical-close change, timestamped quotes, offline keyword-based sentiment from pasted text, a five-year user-input DCF valuation,
and opt-in NVIDIA AI analysis for six independently configurable analyst roles.
AI calls require the user's own NVIDIA key and internet access. API credentials are encrypted
using Android Keystore AES-GCM. No trading, brokerage integrations, live social feeds, or
guaranteed financial statements. Market-data availability is external and may be blocked.

## Build
JDK 17, Android SDK 35, Gradle 8.9:
```
gradle -p finrobot-mobile :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
The CI workflow builds a **debug signed** APK artifact. Install it on Android with sideload
permissions enabled. Debug signature is not an audited production signing key.

## Testing
Unit tests exercise deterministic indicators, keyword sentiment and DCF edge cases.
CI builds/tests/lints and separately runs emulator installation, activity launch, and instrumented UI plus offline-feature assertions on Android API 33. A successful build is not proof of an emulator pass; check the run result.
No physical Samsung S23 testing or live NVIDIA credentials are used in CI.

## Security caveats
Keys are stored in Android Keystore-protected preferences with app backup disabled.
The model request is sent directly from the handset over HTTPS to NVIDIA.
Pasted headlines are transmitted only when the user taps AI Analyze.
Research output can be inaccurate; verify independently before investing.
