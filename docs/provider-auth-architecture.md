# Provider account-linking architecture

This document records the shipped account-linking decision for each provider. The goal is to avoid copying reusable CLI credentials when a provider offers a supported device/browser flow, and to fail closed when it does not.

## Capability matrix

| Provider | Default linking path | Mobile support decision | Stored identity | Secret handling | Endpoint status |
| --- | --- | --- | --- | --- | --- |
| Codex / ChatGPT | Device-code authorization through OpenAI/ChatGPT endpoints | Enabled | Account ID when available; token generation is fingerprinted for refresh safety | Access/refresh tokens are value-encrypted with Android Keystore-backed DataStore | Quota endpoint remains treated as provider-specific and refresh failures are isolated per account fingerprint |
| GitHub Copilot | GitHub device flow | Enabled | GitHub token subject is represented by a token fingerprint; raw token is never displayed | OAuth token is value-encrypted and never logged | Uses GitHub Copilot user quota endpoint |
| Gemini | No new direct account-linking path | Disabled / fail closed | Legacy encrypted credentials from older releases are retained only until explicit disconnect | The app neither requests a new Gemini OAuth credential nor imports Gemini CLI tokens or client secrets | Google `cloudcode-pa` is an internal Gemini CLI service and is not treated as a supported third-party quota API |
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
- Gemini direct linking is disabled. Google's device authorization endpoint accepts only **TVs and Limited Input devices** OAuth clients, while normal Android or desktop client IDs fail with HTTP 401 `invalid_client`. That device flow also limits its allowed scopes and does not permit the `cloud-platform` scope the previous connector requested.
- Google's supported Android authorization SDK provides short-lived client-side access tokens. Offline refresh-token access requires a backend, and it does not establish permission for a third-party app to call Gemini CLI's internal `cloudcode-pa` service. Gemini CLI's terms explicitly disallow third-party direct access to the services powering the CLI.
- Gemini mobile monitoring can be reconsidered only when Google exposes a documented public quota API or a compliant companion-sync design is implemented. Until then, the Settings card points users to the official Gemini CLI `/stats model` command. Legacy encrypted credentials are never deleted automatically and remain removable through Disconnect.

References: [Google limited-input device flow](https://developers.google.com/identity/protocols/oauth2/limited-input-device), [Android authorization guidance](https://developer.android.com/identity/authorization), and [Gemini CLI terms and privacy notice](https://github.com/google-gemini/gemini-cli/blob/main/docs/resources/tos-privacy.md).
