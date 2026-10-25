# Local model candidates and reality checks

The owner listed two `.litertlm` files as initial candidates:
- `gemma3-1b-it-int4.litertlm` (about 584 MB): fast router role.
- `gemma-3n-E2B-it-int4.litertlm` (about 3.66 GB): conversation role.

These filenames and sizes come from the owner's requirement, not measured builds. The development source uses LiteRT-LM Android 0.18.0 documented Engine/Conversation API.

### What still needs verification

- That those *exact* files are compatible with LiteRT-LM 0.18.0 and licensed for this private use.
- Actual RAM required on Samsung Galaxy S23, initialization time, prefill/TTFT/tokens per second, battery/thermal cost.
- Whether GPU/NPU delegates work and improve real-phone performance. Default backend is conservative CPU.
- Kannada/Telugu instruction quality, mixed-language intent accuracy, malformed structured tool outputs.
- Copy-to-app-private free-space burden. ModelImporter records SHA-256 and confirms byte count but does not certify that arbitrary model bytes are safe: LiteRT load must also succeed.

The app does **not** download model weights, package weights, or claim desktop benchmarks represent the phone. It reports measured fields only when populated.
