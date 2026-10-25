# Shiv native integration review

Status: **NOT MERGED into `main`**. This branch is reserved for reviewed source integration.

The connected repository's `main` tree contained only `README.md` during inspection. It does not contain the Kotlin sources for the original Shiv README's SQLCipher database, reminder extraction or WorkManager scheduling. These cannot be verified or preserved from documentation alone.

A separate local candidate was prepared from the uploaded OpenDroid archive with an Android application ID of `com.shiv.assistant`, Kotlin namespace `com.shiv.ai`, renamed app/service/database identifiers, and preserved source modules, tests, assets and upstream licensing. It has **not** been committed to this repository and it has **not** been compiled or device-tested.

Release blockers:
1. Obtain and compare original Shiv application source; integrate its encryption/reminder implementation additively.
2. Run Gradle tests, lint, build and instrumentation tests with Android SDK 36 and JDK 21.
3. Review SMS, microphone, location, contacts, Accessibility and notification permissions against feature necessity and policy.
4. Audit rebrand and reflection/Room-migration compatibility; the new application ID cannot update old installs in-place.
5. Review upstream licenses and user-facing assets/domains before release.

**Do not merge this audit-only branch as the finished source integration.**