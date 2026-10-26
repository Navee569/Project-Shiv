# Shiv feature / test matrix

Legend: **ACTIVE** has an actual bounded Shiv code path; **CONNECT** requires third-party authorization; **PERMISSION** needs explicit system approval; **PENDING** is unimplemented.

| Family | ACTIVE in this development | CONNECT / PERMISSION / PENDING |
| --- | --- | --- |
| Agent | Safe parsing, bounded plans, manual action review and retry | Automatic execution verification and autonomous replanning |
| Intelligence | Deterministic local assistant; LiteRT-LM CPU on-device engine and SAF model staging (OWNER-VERIFY); existing OpenAI-compatible cloud/hybrid modes; bounded priority queue | Autonomous two-model routing, model downloader, full provider catalog |
| Memory | Room + SQLCipher encryption with Keystore-wrapped key; explicit memory correction, provenance and encrypted facts (OWNER-VERIFY) | Full person/place graph and measured retention; encrypted backup/restore built (OWNER-VERIFY) |
| Voice | On-device SpeechRecognizer API31+, selected en-IN/kn-IN/te-IN, Google TTS validation (OWNER-VERIFY) | Wake word, code-switching golden set, always-ready listening |
| Vision | User-selected cloud image description with explicit consent; bundled offline Latin OCR temporary preview (not stored, OWNER-VERIFY) | Kannada/Telugu OCR, live screen capture, multimodal local image model |
| Communication | Call dialer, SMS/email compose, Telegram/WhatsApp share | Contacts-based disambiguation and verified end-to-end delivery |
| Productivity | Local encrypted Events/Tasks CRUD and tomorrow schedule answers; relative reminders with WorkManager, calendar/alarm/timer intents | Cross-account calendar reads and complex recurring routines |
| Device | Wi-Fi/Bluetooth/settings, music/video/camera launch | Direct privileged control, accessibility app automation, playback verification |
| Travel, food, shopping | Maps and browser handoffs | Uber/Ola, food/shopping booking/transaction APIs |
| Finance | UPI application opened after confirmation | Real transfers, bill split and financial account access |
| Smart home | Home controls web handoff | Authenticated device integrations |
| Social | Editable draft sharing and local campaign notes | OAuth publishing, scheduled posts, inbox/comments, analytics, reports |
| Habits / macros | Encrypted manual macro store, reviewable plan actions | Habit detection, unattended event/trigger execution |
| Notifications | Optional listener, encrypted title + app metadata | Intelligent auto-reply / semantic classification |
| Workspace | User-selected text import | Sandboxed persistent terminal, MCP server, privileged command execution |
| Privacy | Keystore-wrapped SQLCipher + legacy migration, denylist, privacy pause, guest mode and biometric gate (OWNER-VERIFY) | Hardening, full source policy audit and penetration testing |
| UI | Original Compose dashboard/chat/Tasks/Memory/Models/Status/Privacy/Grants/Settings | Pixel/device/accessibility design QA |

## Verification stages

- Static manifest and branding tests (Python, no SDK needed).
- Domain unit tests (JUnit via Gradle).
- Android compilation and lint (CI).
- Required before release: permission-denied UI tests, device API 26–35 coverage, encryption recoverability tests, mock-cloud injection tests, malicious URI tests, integration contract tests with provider credentials, accessibility audit and manual UI acceptance.

**Unmet entries are not to be reported as complete.**


## Additional bounded feature entrypoints — 2026-10-26

The earlier matrix is a **historical baseline** for commit 3b6c4e4; these are
newly implemented code paths and their actual remaining verification status.

| Feature | New code path | Verification / limitation |
| --- | --- | --- |
| Proactive agenda | Owner opt-in hourly WorkManager trigger; dedup, quiet hours, capped notices | Full timing, battery and reboot OWNER-VERIFY |
| Routine evidence | Completed agenda events, 3 independent dates, charging-only consolidation | Not broad passive phone learning; not confirmed owner facts |
| Feedback | Encrypted owner reactions, weekly seven-day report, bounded discretionary caps | Real-world reactions must be entered by owner |
| Calendar | Permissioned two-week preview and review-before-import, deduped | Android Calendar OEM OWNER-VERIFY |
| Contacts | Permissioned on-demand matching names, no numbers stored | Not a full social graph or dialer address book |
| App Usage | Manual special Usage Access, per-app denylist, transient 24-hour package totals | No screen contents or background usage harvesting |
| Accessibility | Explicit service enable + exact allowlist + 15-second arm; facts only | Potential FLAG_SECURE limitations, no continuous privileged automation |
| Calls | Generic opt-in call-ended prompt without caller identity or call audio | OEM broadcast behavior OWNER-VERIFY |
| SQLCipher instrumentation | Disposable on-phone plaintext/wrong-key regression test APK | Compiled by CI, not executed on a Galaxy S23 |

A feature with a working entrypoint is still not automatically a completed Phase
1/Phase 2 milestone. Major missing work remains as listed in STATUS.md.


- **Offline OCR to agenda (code path):** ISO due-date extraction from temporary
  Latin OCR preview, explicit owner time and title confirmation. Not a fully
  multilingual receipt or bill understanding feature; OWNER-VERIFY.
