# Testing Project Shiv

## Environment

The project targets Android SDK 35 and uses JDK 17 / Gradle Wrapper 8.9 on the inspected working branch. Confirm the actual installed Java and SDK versions before interpreting failures.

## Baseline commands

From the repository root on Windows:

```powershell
java -version
.`gradlew.bat --version
.`gradlew.bat projects
.`gradlew.bat :app:tasks --all
.`gradlew.bat :app:assembleDebug
.`gradlew.bat :app:testDebugUnitTest
.`gradlew.bat :app:lintDebug
```

On macOS/Linux:

```sh
java -version
./gradlew --version
./gradlew projects
./gradlew :app:tasks --all
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Run commands independently when diagnosing failures and retain complete failure output. Fix the earliest meaningful error before chasing downstream errors. Do not claim a task passed unless its exit status is successful.

## CI

The Android CI workflow runs the debug build, unit-test task, and debug lint on a hosted Linux runner using JDK 17 and Android SDK setup. CI verifies a build environment; it does not replace physical-device testing.

## Device smoke test

When an authorised device is connected:

1. Run `adb devices` and confirm the intended device is listed as `device`.
2. Install the freshly built debug APK.
3. Launch Shiv from its launcher icon.
4. Check cold start and logcat for fatal exceptions.
5. Background and reopen the app.
6. Exercise permission-denied paths without granting permissions automatically.
7. Verify database initialisation and persistence across a process restart.
8. Test airplane mode for features claimed to work offline.
9. Record Android version, device model, source revision, exact commands, results, and relevant log excerpts.

If a device is unavailable, mark device checks NOT TESTED or BLOCKED; do not infer success from compilation.

## Security-sensitive tests required before beta

- SQLCipher database cannot be opened as ordinary plaintext SQLite.
- Database key creation, wrapping, reopening, invalidation, and failure paths behave safely.
- No plaintext fallback occurs if encryption fails.
- Local model inference succeeds with network disabled.
- Malformed model output is rejected and cannot bypass action authorisation.
- Sensitive user data does not leak through logs, exported files, notifications, or crash diagnostics.
