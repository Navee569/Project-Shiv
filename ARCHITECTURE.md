# Architecture

## Current structure

The app remains one Gradle module. Most platform-facing classes, the ViewModel, Brain, Room entities/DAO, and workers are still in the `app.shiv` package. Pure memory and scheduling policy lives in `app.shiv.domain`. `BrainPort` and `ProactiveBrain` isolate model inference; `ProactiveNotifications` isolates Android notification delivery. `ProactiveRepository` is a domain port with a Room adapter, so the worker no longer depends on Room APIs. `ShivRepository` still exposes the DAO directly to the ViewModel.

## Architecture decisions

| Decision | Rationale | State |
|---|---|---|
| Keep one Gradle module and introduce package boundaries incrementally. | Avoid build complexity while the app boundaries stabilize. | Active |
| Keep deterministic memory matching and proactive schedule policy in pure Kotlin domain packages. | These decisions need unit tests without UI, model inference, or Android services. | Started in `domain.memory` and `domain.scheduling` |
| Route all generative inference through `BrainPort`/`ProactiveBrain`; keep model runtime details in `Brain`. | UI and workers should depend on capabilities, not model classes. | Existing boundary |
| Define proactive persistence in `ProactiveRepository`; implement it with `RoomProactiveRepository`. | Worker orchestration should not depend on Room or SQLCipher APIs; Room transactions remain atomic behind the port. | Implemented for proactive scheduling |
| Treat WorkManager, Room/SQLCipher, and Android APIs as adapters around domain decisions. | Platform and persistence changes must not redefine business policy. | Incremental; proactive worker uses a domain repository port, but its Android composition root and ViewModel storage boundary remain |
| Preserve deterministic authorization outside Brain output. | Model decisions cannot grant themselves permissions or confirmation authority. | Existing policy; action-engine boundary remains future work |

## M1 remaining work

Extract a repository interface for the ViewModel and remaining storage operations; separate UI/platform classes into packages; define stable domain contracts for action execution, inference queueing, speech/TTS, integrations, and diagnostics. The proactive scheduling and persistence contract is independently tested through the Room adapter, but M1 remains partial until the remaining boundaries are in place and independently tested.
