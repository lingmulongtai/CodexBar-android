# Provider account-linking architecture

This document records the shipped account-linking decision for each provider. The goal is to avoid copying reusable CLI credentials when a provider offers a supported device/browser flow, and to fail closed when it does not.

## Capability matrix

| Provider | Default linking path | Mobile support decision | Stored identity | Secret handling | Endpoint status |
| --- | --- | --- | --- | --- | --- |
| Codex / ChatGPT | Device-code authorization through OpenAI/ChatGPT endpoints | Enabled | Account ID when available; token generation is fingerprinted for refresh safety | Access/refresh tokens are value-encrypted with Android Keystore-backed DataStore | Quota endpoint remains treated as provider-specific and refresh failures are isolated per account fingerprint |
| GitHub Copilot | GitHub device flow | Enabled | GitHub token subject is represented by a token fingerprint; raw token is never displayed | OAuth token is value-encrypted and never logged | Uses GitHub Copilot user quota endpoint |
| Gemini | Google OAuth device authorization using user-supplied public/native OAuth client ID | Experimental | OAuth client ID plus token fingerprint; no client secret exists in app state | Client secrets are rejected, not stored, and not transmitted | Google `cloudcode-pa` quota path remains internal/experimental; failures surface as provider errors |
| Claude | Local-only setup-token import | No supported third-party Android/device flow currently shipped | Token fingerprint and optional scopes/tier only; raw token is never identity | Manual token is value-encrypted; UI uses secret keyboard semantics and validate-before-save | Quota/limits are accessed through Anthropic-compatible endpoints; unsupported web sign-in fails closed |

## Cross-provider requirements

- Token exchange clients use logging-disabled OkHttp clients.
- Normal API clients use metadata-only logging with credential headers redacted.
- Credentials are stored as versioned encrypted values in DataStore and excluded from cloud backup/device transfer.
- Draft credential edits stay in memory until validation succeeds.
- Disconnect purges credential, quota history, widget cache, and refresh state for the selected provider.
- Refresh retry state is provider/account scoped so one provider outage cannot force repeated refreshes for unrelated providers.
- Live monitoring is user-started, time-bounded, and uses a standard notification style.

## Unsupported or experimental flows

- Claude browser/device authorization is not enabled until Anthropic exposes a supported third-party native/mobile flow for this use case.
- Gemini quota retrieval is marked experimental because the quota endpoint is provider-internal. The app no longer accepts a copied CLI `client_secret`; if the public/native client cannot access the endpoint, the provider must surface a recoverable provider error instead of silently falling back to secret copying.
