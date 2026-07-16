# Provider account-linking architecture

This document records the shipped account-linking decision for each provider. The goal is to avoid copying reusable CLI credentials when a provider offers a supported device/browser flow, and to fail closed when it does not.

## Capability matrix

| Provider | Default linking path | Mobile support decision | Stored identity | Secret handling | Endpoint status |
| --- | --- | --- | --- | --- | --- |
| Codex / ChatGPT | Device-code authorization through OpenAI/ChatGPT endpoints | Enabled | Account ID when available; token generation is fingerprinted for refresh safety | Access/refresh tokens are value-encrypted with Android Keystore-backed DataStore | Quota endpoint remains treated as provider-specific and refresh failures are isolated per account fingerprint |
| GitHub Copilot | GitHub device flow | Enabled | GitHub token subject is represented by a token fingerprint; raw token is never displayed | OAuth token is value-encrypted and never logged | Uses GitHub Copilot user quota endpoint |
| Gemini | Private companion around the official Gemini CLI `/stats` view | Enabled without Google credentials on Android; direct OAuth remains fail closed | Companion ID, numeric local address/port, and a random 256-bit pairing key | Google tokens stay inside the official CLI; Android stores only the Keystore-encrypted pairing details | Android calls only the paired local companion; neither side calls `cloudcode-pa` as a third-party client |
| Claude | Local-only setup-token import | No supported third-party Android/device flow currently shipped | Token fingerprint and optional scopes/tier only; raw token is never identity | Manual token is value-encrypted; UI uses secret keyboard semantics and validate-before-save | Quota/limits are accessed through Anthropic-compatible endpoints; unsupported web sign-in fails closed |
| Cursor | Manually supplied first-party `cursor.com` Cookie header | No supported mobile device-code flow; desktop browser/app stores are not imported on Android | Cookie is represented only by a provider-scoped fingerprint outside encrypted storage | Header is syntax-checked, validated before save, value-encrypted, never logged, and never sent across redirects | Reads `cursor.com/api/usage-summary`, with best-effort first-party identity and legacy request-quota calls matching the original CodexBar mapping |
| ZenMux | User-created Management API key | Enabled through ZenMux's documented Management API | The key is represented only by a provider-scoped fingerprint outside encrypted storage | Key is value-encrypted, never logged, validated before save, and never sent across redirects | Reads rolling 5-hour and 7-day quotas from `zenmux.ai/api/v1/management/subscription/detail`; normal inference keys are rejected |

## Cross-provider requirements

- Token exchange clients use logging-disabled OkHttp clients.
- Normal API clients use metadata-only logging with credential headers redacted.
- Credentials are stored as versioned encrypted values in DataStore and excluded from cloud backup/device transfer.
- Draft credential edits stay in memory until validation succeeds.
- Disconnect purges credential, quota history, widget cache, and refresh state for the selected provider.
- Refresh retry state is provider/account scoped so one provider outage cannot force repeated refreshes for unrelated providers.
- Live monitoring is user-started, time-bounded, and uses a standard notification style.
- Gemini companion requests use HMAC-SHA256 authentication, bounded clock skew, nonce replay rejection, per-client rate limiting, and AES-256-GCM snapshot encryption.
- The companion binds one numeric private IPv4 address, discards raw terminal output after parsing, and exposes only validated quota fields with a freshness timestamp.

## Unsupported or experimental flows

- Claude browser/device authorization is not enabled until Anthropic exposes a supported third-party native/mobile flow for this use case.
- Gemini direct Android OAuth remains disabled. Google's device authorization endpoint accepts only **TVs and Limited Input devices** OAuth clients, while normal Android or desktop client IDs fail with HTTP 401 `invalid_client`. That device flow also limits its allowed scopes and does not permit the `cloud-platform` scope the previous connector requested.
- Google's supported Android authorization SDK provides short-lived client-side access tokens. Offline refresh-token access requires a backend, and it does not establish permission for a third-party app to call Gemini CLI's internal `cloudcode-pa` service. Gemini CLI's terms explicitly disallow third-party direct access to the services powering the CLI.
- The shipped companion avoids that unsupported path: it launches the official CLI, requests its documented `/stats` screen, sanitizes the displayed quota fields, and serves the snapshot locally. Companion code never reads or exports the CLI credential files and makes no Google API request; only the official CLI performs its normal authenticated work.
- Legacy Gemini OAuth/client-secret records from pre-v0.4.0 are purged during secure-store migration and cannot be used by the current runtime. Disconnect removes the companion pairing plus Gemini history and widget cache.

References: [Google limited-input device flow](https://developers.google.com/identity/protocols/oauth2/limited-input-device), [Android authorization guidance](https://developer.android.com/identity/authorization), [Gemini CLI quota guidance](https://github.com/google-gemini/gemini-cli/blob/main/docs/resources/quota-and-pricing.md), and [Gemini CLI terms and privacy notice](https://github.com/google-gemini/gemini-cli/blob/main/docs/resources/tos-privacy.md).
