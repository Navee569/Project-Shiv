# Security policy

Project Shiv is **experimental and not production-cleared**. It processes potentially sensitive local information and includes optional access to Android permissions and cloud models. Do not rely on it as a production financial assistant, identity tool, or security-critical service.

## Reporting a vulnerability

If the repository offers GitHub's **Report a vulnerability** / private security advisory feature, use it to report the issue privately. If that option is unavailable, open a public issue **only to request a private contact method**, without including exploit instructions, private data, credentials, or reproduction artifacts.

A useful private report includes the affected commit, Android version/device, a concise impact description, minimal reproduction steps, and suggested mitigation if known. Do not test against other people's devices or data.

There is no guaranteed response or patch timeline for this experimental project. Avoid assuming vulnerability fixes are complete without reviewing the specific change and its verification evidence.

## Security boundaries

- Cloud/hybrid mode must remain opt-in; third-party API terms still apply.
- Permission and accessibility paths need physical-device verification and are not blanket grants for automation.
- Encrypted database and key management code require real-device acceptance testing.
- The project license does not grant rights to third-party model weights, SDKs, or other separately licensed components.

See [TESTING.md](TESTING.md), [DEFECT_LOG.md](DEFECT_LOG.md), and [STATUS.md](STATUS.md) for documented validation gaps.
