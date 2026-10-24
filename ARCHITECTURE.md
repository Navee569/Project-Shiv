# Architecture

## Current structure

The app remains one Gradle module. Platform-facing classes, the ViewModel, Brain, Room entities/DAO, and workers are currently in the `app.shiv` package. `BrainPort` and `ProactiveBrain` isolate model inference; `ProactiveNotifications` isolates Android notification delivery. `ShivRepository` currently exposes the DAO directly, so the UI/worker-to-storage boundary is not yet complete.

## Architecture decisions

| Decision | Rationale | State |
|---|---|---|
| Keep one Gradle module and introduce package boundaries incrementally. | Avoid build complexity while the app boundaries stabilize. | Active |
| Keep deterministic memory matching and proactive schedule policy in pure Kotlin domain packages. | These decisions need unit tests without UI, model inference, or Android services. | Started in `domain.memory` and `domain.scheduling` |
| Route all generative inference through `BrainPort`/`ProactiveBrain`; keep model runtime details in `Brain`. | UI and workers should depend on capabilities, not model classes. | Existing boundary |
| Treat WorkManager, Room/SQLCipher, and Android APIs as adapters around domain decisions. | Platform and persistence changes must not redefine business policy. | Incremental; workers and repository still need separation |
| Preserve deterministic authorization outside Brain output. | Model decisions cannot grant themselves permissions or confirmation authority. | Existing policy; action-engine boundary remains future work |

## M1 remaining work

Extract repository interfaces and implementations, separate worker orchestration from persistence transactions, and define stable domain contracts for event scheduling, memory retrieval, action execution, inference queueing, and diagnostics. Do not label M1 complete until those boundaries are in place and independently tested.
