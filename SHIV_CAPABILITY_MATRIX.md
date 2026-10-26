# Shiv feature / test matrix

Legend: **ACTIVE** has an actual bounded Shiv code path; **CONNECT** requires third-party authorization; **PERMISSION** needs explicit system approval; **PENDING** is unimplemented.

| Family | ACTIVE in this development | CONNECT / PERMISSION / PENDING |
| --- | --- | --- |
| Agent | Safe parsing, bounded plans, manual action review and retry | Automatic execution verification and autonomous replanning |
| Intelligence | Deterministic local assistant; LiteRT-LM CPU on-device engine and SAF model staging (OWNER-VERIFY); existing OpenAI-compatible cloud/hybrid modes | Autonomous two-model routing, priority queue, model downloader, full provider catalog |
| Memory | Room + SQLCipher encryption with Keystore-wrapped key; explicit memory correction, provenance and encrypted facts (OWNER-VERIFY) | Full person/place graph, measured retention, encrypted backup/restore |
| Voice | On-device SpeechRecognizer API31+, selected en-IN/kn-IN/te-IN, Google TTS validation (OWNER-VERIFY) | Wake word, code-switching golden set, always-ready listening |
| Vision | Explicitly selected image, explicit transmission consent to cloud vision endpoint | Offline OCR and live screen capture |
| Communication | Call dialer, SMS/email compose, Telegram/WhatsApp share | Contacts-based disambiguation and verified end-to-end delivery |
| Productivity | Relative reminders with WorkManager, calendar/alarm/timer intents | Cross-account calendar reads and complex recurring routines |
| Device | Wi-Fi/Bluetooth/settings, music/video/camera launch | Direct privileged control, accessibility app automation, playback verification |
| Travel, food, shopping | Maps and browser handoffs | Uber/Ola, food/shopping booking/transaction APIs |
| Finance | UPI application opened after confirmation | Real transfers, bill split and financial account access |
| Smart home | Home controls web handoff | Authenticated device integrations |
| Social | Editable draft sharing and local campaign notes | OAuth publishing, scheduled posts, inbox/comments, analytics, reports |
| Habits / macros | Encrypted manual macro store, reviewable plan actions | Habit detection, unattended event/trigger execution |
| Notifications | Optional listener, encrypted title + app metadata | Intelligent auto-reply / semantic classification |
| Workspace | User-selected text import | Sandboxed persistent terminal, MCP server, privileged command execution |
| Privacy | Keystore-wrapped SQLCipher + legacy migration, denylist, privacy pause, guest mode and biometric gate (OWNER-VERIFY) | Hardening, full source policy audit and penetration testing |
| UI | Original Compose dashboard/chat/automation/memory/settings | Pixel/device/accessibility design QA |

## Verification stages

- Static manifest and branding tests (Python, no SDK needed).
- Domain unit tests (JUnit via Gradle).
- Android compilation and lint (CI).
- Required before release: permission-denied UI tests, device API 26–35 coverage, encryption recoverability tests, mock-cloud injection tests, malicious URI tests, integration contract tests with provider credentials, accessibility audit and manual UI acceptance.

**Unmet entries are not to be reported as complete.**
