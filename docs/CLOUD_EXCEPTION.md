# Cloud AI API — owner-authorized exception

The owner explicitly allowed configurable cloud AI as an exception to the otherwise local-only design. Existing `ShivIntelligence` performs HTTP only when:
1. mode is CLOUD or HYBRID (LOCAL never contacts an endpoint);
2. the owner separately enables `allowCloudTransmission`;
3. a valid owner-provided HTTPS URL (or explicit loopback for a local service) is used.

API secret is saved only inside the SQLCipher vault. Chat text is not automatically combined with contacts/calendar/notification history. Selected image use has a separate confirmation.

**Security caveats:** a custom HTTPS endpoint can still be malicious; do not enter credentials you do not trust. No remote telemetry, analytics, remote model download or silent personal-data sync is implemented. Do not enable cloud for private message or screen content.
