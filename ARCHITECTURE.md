# Architecture

## Current structure

The app remains one Gradle module with `ui`, `domain`, `data`, and `platform` packages. Compose screens and the ViewModel are in `app.shiv.ui`; Android services, receivers, workers, Brain runtime, and integration adapters are in `app.shiv.platform`; Room entities, DAO, and database are in `app.shiv.data.local`. Domain records and capability contracts have no Android or Room dependencies. Room adapters map between persistence and domain records; repositories expose the operations used by presentation and proactive scheduling. Chat and proactive inference call domain Brain contracts, while the Android Brain owns LiteRT-LM and Android model import. Model-proposed mutations and final confirmations use the domain action executor and audit port. Calendar sync, reminder scheduling, notification delivery, permission state, diagnostics, speech recognition, synthesis, and inference submission have explicit ports.

## Architecture decisions

| Decision | Rationale | State |
|---|---|---|
| Keep one Gradle module and introduce package boundaries incrementally. | Avoid build complexity while the app boundaries stabilize. | Active |
| Keep deterministic memory matching and proactive schedule policy in pure Kotlin domain packages. | These decisions need unit tests without UI, model inference, or Android services. | Started in `domain.memory` and `domain.scheduling` |
| Route all generative inference through `BrainPort`/`ProactiveBrain`; keep model runtime details in `Brain`. | UI and workers should depend on capabilities, not model classes. | Existing boundary |
| Define proactive persistence in `ProactiveRepository`; implement it with `RoomProactiveRepository`. | Worker orchestration should not depend on Room or SQLCipher APIs; Room transactions remain atomic behind the port. | Implemented; pure domain types |
| Define ViewModel persistence as `ShivViewRepository` and provide a Room adapter. | Presentation logic should use repository capabilities rather than a DAO. | Implemented; domain records and Room entities are separate types |
| Persist incoming integration signals before Brain processing through `CaptureQueueRepository`. | Process death, retry, and reboot must not lose notification or listening input; identity leases make completion and retry safe. | Implemented for listening transcripts and notification summaries; call, calendar, and screen sources await their integration milestones |
| Serialize capture work through one priority-ordered WorkManager queue. | A single consumer claims the highest-priority due row; SQLCipher state is rediscovered at app start and reboot. | Implemented; retry and lease recovery have Room-backed unit tests |
| Verify Room reads, writes, corrections, and deletes through in-memory Room; verify encryption and schema upgrades on Android. | Test repository behavior independently while exercising Keystore and SQLCipher on their actual runtime. | CRUD/queue unit coverage plus Android instrumentation for encrypted reopen, wrong/lost keys, and migration |
| Define speech input/output as platform-free capability interfaces. | UI depends on speech behavior rather than Android recognizer/TTS classes. | Implemented; Android adapters remain in use |
| Keep Room entities, DAO, and encrypted database in `data.local`. | Room annotations and SQLCipher construction must not leak into domain records or UI. | Implemented; domain records map through repository adapters |
| Define chat and proactive behavior through a domain `BrainPort`. | ViewModels and workers should depend on Brain capabilities while LiteRT-LM stays in the Android adapter. | Implemented; Brain runtime and model import remain platform-owned |
| Define inference submission behind a platform-free `InferenceQueue` contract. | Brain should request prioritized work without owning queue mechanics; queued cancellation and stale fallbacks must be testable without Android or a model. | Implemented; priority, stale fallback and waiting-caller cancellation have JVM tests; queue remains unbounded |
| Define typed domain action authorization requests and decisions. | Action type, exact supported argument shape and values, effective tier, permission state, and confirmation state must be decided independently of model output. | Implemented; current policy validates each proposal and reevaluates confirmed requests at dispatch time |
| Put action execution behind a domain executor and audit port. | Persist the validated request before dispatch and its result/undo details after dispatch, so persistence failure cannot silently authorize an unrecorded side effect. | Implemented for model-proposed mutations and confirmation paths, plus direct event rescheduling; full request and completion details persist through Room and encrypted backup |
| Keep Android integration, permission, and diagnostics behavior behind domain ports. | Domain and policy code should not depend on Android permission checks, AlarmManager, calendar resolvers, notification APIs, or log APIs. | Implemented for calendar sync, reminder scheduling, notification delivery, action permissions, and sanitized diagnostics |
| Treat WorkManager, Room/SQLCipher, and Android APIs as adapters around domain decisions. | Platform and persistence changes must not redefine business policy. | Incremental; proactive worker uses a domain repository port, but its Android composition root remains |

## M1 acceptance and M3 status

M1 architecture acceptance is complete. M3 adds schema version 6, an explicit version 5 migration, a durable encrypted capture queue, and an Android instrumentation path through the production Keystore key wrapper and SQLCipher factory. Exact-commit CI and independent M3 review remain pending; this does not claim later milestones or release acceptance complete.
