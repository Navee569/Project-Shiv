# Local model candidates and reality checks

The owner listed two `.litertlm` files as initial candidates:
- `gemma3-1b-it-int4.litertlm` (about 584 MB): fast router role.
- `gemma-3n-E2B-it-int4.litertlm` (about 3.66 GB): conversation role.

These filenames and sizes come from the owner's requirement, not measured builds. The development source uses LiteRT-LM Android 0.18.0 documented Engine/Conversation API.

### Source-backed candidate comparison (27 October 2026)

| Role | Candidate | Format and license evidence | Kannada and device evidence |
| --- | --- | --- | --- |
| Router | Gemma 3 1B IT | Google's [base model card](https://huggingface.co/google/gemma-3-1b-it) identifies the Gemma terms. A [third-party LiteRT conversion](https://huggingface.co/cuesetac/gemma-3-1b-it-litertlm/blob/main/README.md) lists a 584,417,280-byte `.litertlm` and SHA-256, but it is not the exact owner-named file. | The base card reports aggregate multilingual results; no Kannada intent or Galaxy S23 measurement is supplied for this conversion. |
| Conversation | [Google Gemma 3n E2B IT LiteRT-LM](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm) | Official LiteRT-LM model card; access requires acceptance of Gemma terms. | Published S24 Ultra CPU/GPU numbers are comparison context only. No S23 or Shiv Kannada result is published there. |

The exact imported bytes are the unit of compatibility: compare the app's recorded
SHA-256 and byte count with the chosen distributor, then confirm load and reply
on the target runtime. Do not infer compatibility from a similar filename.

### Repeatable Galaxy S23 comparison

Use the same ten English and ten Kannada owner-approved prompts for each candidate.
Include simple router choices, personal-context questions, and each supported
structured action. For every model/backend record imported filename, SHA-256,
LiteRT version, Android build, free RAM before load, load success/error, load ms,
runtime TTFT, decode token count and tokens/s, total reply ms, invalid JSON count,
Kannada intent correctness, and battery/thermal state. Run three warm and three
cold replies per prompt. The AI Status screen reports runtime token counts rather
than estimating from characters. A failed load or missing benchmark field is a
recorded failure/unavailable value, never a zero. Keep model terms and source URL
with the result. This comparison has not been run on the owner's phone.

### What still needs verification

- That those *exact* files are compatible with LiteRT-LM 0.18.0 and licensed for this private use.
- Actual RAM required on Samsung Galaxy S23, initialization time, prefill/TTFT/tokens per second, battery/thermal cost.
- Whether GPU/NPU delegates work and improve real-phone performance. Default backend is conservative CPU.
- Kannada/Telugu instruction quality, mixed-language intent accuracy, malformed structured tool outputs.
- Copy-to-app-private free-space burden. ModelImporter records SHA-256 and confirms byte count but does not certify that arbitrary model bytes are safe: LiteRT load must also succeed.

The app does **not** download model weights, package weights, or claim desktop benchmarks represent the phone. It reports measured fields only when populated.
