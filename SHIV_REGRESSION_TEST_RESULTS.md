# Shiv regression test results — 2026-10-25

**Status: NOT release-cleared.** A separate source archive `Shiv-Regression-Fixed.zip` contains the fix, tests and CI static job. The source archive is **not yet merged into GitHub**, where `main` currently contains no Android source code.

## Confirmed fix
- Android manifest incorrectly enabled `android:allowBackup="true"` while its extraction rules documented backups disabled. Source archive sets `android:allowBackup="false"`.
- Existing credential-store test extended to assert backup disabled.
- Added `verify_shiv_static.py` plus a GitHub Actions job to validate identifiers, backup rules, source presence, XML structure, exported services and providers.

## Executed
- Offline static regression: PASS (326 Kotlin files, XML parsing, security declarations).
- Negative-control mutation: PASS; changing backup setting to unsafe `true` caused expected test failure.
- ZIP archive integrity: PASS.

## Blocked / not run
- Gradle `testDebugUnitTest assembleDebug lintDebug`: BLOCKED before tasks start, `java.net.UnknownHostException: services.gradle.org` while fetching Gradle 9.7.0.
- Android SDK/adb missing; emulator and instrumentation tests NOT RUN.
- Dependency vulnerability scanning, release build, device E2E and full feature regressions NOT RUN.

## Further required gates
- Restore and compare actual original Shiv source, particularly SQLCipher/reminders, not just README.
- Run Android CI and instrumentation across supported SDKs; review resulting reports and resolve regressions.
- Audit requested permissions (SMS, Accessibility, contacts, background location, microphone, notification listener, all-packages) and app-ID migration.

**This document does not assert that there are no bugs.**