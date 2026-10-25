# Reference study: OpenDroid capability inventory → Shiv architecture

Reviewed source: uploaded \`opendroid-main.zip\`, upstream revision \`9f7c0a40a64c37a4f8175545f1293b09106d59de\`.

Capabilities observed: multi-step agent/plan/re-evaluation, model providers and local inference manager, memory layers/graph, voice and wake word, image/screen interpretation, SMS/calls/WhatsApp/Telegram/email, device settings and media, calendar/reminders/routines, shopping/food/transport/finance/smart home, notifications/auto-reply, social account/content/calendar/inbox/analytics/campaign functions, file workspace, privileged command/MCP integration, security and UI.

Design decisions in Shiv:
1. Rebuild native UI and user journeys, not upstream app identifiers or assets.
2. Use original, testable command planning contracts. Never execute unreviewed external actions.
3. Distinguish deterministic offline intelligence from **actual** on-device neural inference, which requires a separate runtime and model delivery.
4. Cloud transmission is opt-in and model/provider endpoint configurable; credentials encrypted locally.
5. Add only Android permissions actually needed by implemented capabilities.
6. Treat third-party account integrations as explicit unmet requirements until OAuth, API validation and actual posting/receiving are implemented.
7. Preserve original Shiv README as a specification because working original source was not committed.

No OpenDroid Kotlin application modules are copied to Shiv native source. The old CI workflow that fetched an upstream app at build time has been archived and removed from the active Actions directory.
