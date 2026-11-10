# Project Shiv — purpose, scope, and evidence of open-source activity

_Reviewed 10 November 2026. Describes the repository as inspected; not a claim of verified deployment or user adoption._

## The gap

Many mobile assistants route personal queries and contextual information through remote services. Shiv explores how an Android personal assistant can keep important personal records encrypted locally, make offline operation practical, and reserve cloud inference for explicit owner opt-in. It also documents the difficult trade-offs around Android permissions, local neural models, energy, privacy, and owner-controlled actions.

## Public engineering contribution

- A public, original Kotlin/Jetpack Compose implementation, not a relabeled binary of the architecture study's upstream app.
- Source and documentation for local/cloud/hybrid routing, Android Keystore-backed encrypted records, LiteRT-LM integration, resource-aware inference controls, reviewed handoffs, and narrowly permissioned contextual features.
- An audit-first development approach: [STATUS](../STATUS.md), [TESTING](../TESTING.md), [DEFECT LOG](../DEFECT_LOG.md), [ARCHITECTURE](../ARCHITECTURE.md), and [CAPABILITY MATRIX](../SHIV_CAPABILITY_MATRIX.md) distinguish implemented code from verified behavior.
- GitHub pull requests [#1–#10](https://github.com/Navee569/Project-Shiv/pulls?q=is%3Apr) document development and reviews through the current experimental phase. Pull-request count is **not** an external-contributor count.
- An open-source contribution path through the [MIT license](../LICENSE), [contributing guide](../CONTRIBUTING.md), and [security reporting policy](../SECURITY.md).

## Intended audience and present limitations

The materials may be useful to Android developers investigating permission boundaries, private local memory, on-device inference, and accountable automation design. **No verified monthly downloads, external dependent packages/repositories, production users, or downstream organizations have been established** as of this review. We do not claim others rely on Shiv today.

CI artifacts are **debug builds**, not signed production releases. Features still require acceptance testing on actual Android devices, including model compatibility, resource consumption, encryption migration, accessibility limitations, and end-to-end reliability. The project is not ready for security-critical or financial automation.

## How impact will be measured

Once publicly distributed, report metrics from authoritative sources and date-stamp them: GitHub Insights (unique external contributors, PRs, issues, forks), release-download counts, opt-in anonymized feedback (never covert telemetry), downstream references, and independently reproduced builds/tests. Do not treat stars, commits, tests, or feature presence as proof of downstream adoption.

Project: https://github.com/Navee569/Project-Shiv
