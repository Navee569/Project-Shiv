# Architecture

## Current structure

The app remains one Gradle module. Most platform-facing classes, the ViewModel, Brain, Room entities/DAO, and workers are still in the `app.shiv` package. Pure memory and scheduling policy lives in `app.shiv.domain`. `BrainPort` and `ProactiveBrain` isolate model inference; `ProactiveNotifications` isolates Android notification delivery. `ProactiveRepository` is a Room-free domain port with a Room adapter, so the worker no longer depends on Room APIs. The ViewModel now depends on `ShivViewRepository` and `RoomShivViewRepository`; the contract still uses Room entity types, so separating domain records from persistence entities remains. Speech recognition and synthesis are expressed as platform-free capability ports implemented by the current Android adapters.

## Architecture decisions

| Decision | Rationale | State |
|---|---|---|
| Keep one Gradle module and introduce package boundaries incrementally. | Avoid build complexity while the app boundaries stabilize. | Active |
| Keep deterministic memory matching and proactive schedule policy in pure Kotlin domain packages. | These decisions need unit tests without UI, model inference, or Android services. | Started in `domain.memory` and `domain.scheduling` |
| Route all generative inference through `BrainPort`/`ProactiveBrain`; keep model runtime details in `Brain`. | UI and workers should depend on capabilities, not model classes. | Existing boundary |
| Define proactive persistence in `ProactiveRepository`; implement it with `RoomProactiveRepository`. | Worker orchestration should not depend on Room or SQLCipher APIs; Room transactions remain atomic behind the port. | Implemented; pure domain types |
| Define ViewModel persistence as `ShivViewRepository` and provide a Room adapter. | Presentation logic should use repository capabilities rather than a DAO. | Implemented; record types still carry Room annotations |
| Define speech input/output as platform-free capability interfaces. | UI depends on speech behavior rather than Android recognizer/TTS classes. | Implemented; Android adapters remain in use |
| Treat WorkManager, Room/SQLCipher, and Android APIs as adapters around domain decisions. | Platform and persistence changes must not redefine business policy. | Incremental; proactive worker uses a domain repository port, but its Android composition root remains |
| Preserve deterministic authorization outside Brain output. | Model decisions cannot grant themselves permissions or confirmation authority. | Existing policy; action-engine boundary remains future work |

## M1 remaining work

Extend repository coverage to remaining storage operations; separate UI/platform classes into packages; define stable domain contracts for action execution, inference queueing, integrations, permissions, and diagnostics. The proactive scheduling and persistence contract is independently tested through its Room adapter. The ViewModel repository contract has focused Room adapter coverage, and UI speech usage is typed to speech ports. M1 remains partial until domain records are separated from Room entities, remaining platform classes are packaged behind contracts, and action execution, inference queue, integration, permission-tier and diagnostics boundaries are complete and independently tested.
