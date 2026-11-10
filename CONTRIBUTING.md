# Contributing to Project Shiv

Thanks for your interest in Shiv, an experimental privacy-first native Android assistant. Contributions are welcome, including bug fixes, reproducible tests, documentation, accessibility improvements, and review of privacy boundaries.

## Getting started

1. Read [README.md](README.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), and [ARCHITECTURE.md](ARCHITECTURE.md) before changing behavior.
2. Use JDK 21, Android SDK 36, and Gradle 9.7.0 for the documented development configuration.
3. Run `python3 scripts/verify_shiv.py` and, where the Android SDK is available, `gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
4. Submit a focused pull request explaining behavior, security/privacy impact, tests run, and tests **not** run. Open an issue before proposing large architectural changes.

## Technical and safety expectations

- Respect explicit opt-in and owner review for cloud requests, Android data access, and external actions.
- Do not add silent messaging, payment execution, unreviewed privileged commands, or covert collection of personal information.
- Treat locally encrypted personal information as sensitive; never include credentials, model files with restricted licenses, personal data, APK signing keys, or actual user records in commits or test fixtures.
- Keep capability labels and documentation honest. A compiled APK, mocked test, or CI success does **not** prove the feature works on physical devices.
- Prefer small, testable changes with deterministic tests. For Android-device-dependent paths, describe manual verification needed instead of asserting acceptance.
- Include attribution for third-party code and verify compatibility with upstream licenses. Project Shiv's MIT license covers original project material, not separately licensed third-party dependencies.

## Community workflow

Search existing issues and pull requests before creating new ones. Bug reports should include Android version, device type, expected versus actual behavior, reproducible steps with sensitive details removed, and relevant redacted logs. Feature proposals should describe the use case and permission/privacy implications.

For security vulnerabilities, follow [SECURITY.md](SECURITY.md) rather than publishing exploit details in a public issue.

Participation is governed by respectful, technically constructive discussion. Maintainers may decline changes that expand unsafe capabilities or weaken privacy safeguards.
