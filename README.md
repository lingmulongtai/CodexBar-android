# CodexBar for Android

> Android port of [**CodexBar**](https://github.com/steipete/CodexBar) by [@steipete](https://github.com/steipete) — the macOS menu bar app for monitoring AI service quotas.

Monitor your AI service quotas from your Android device. Track remaining usage for Claude, Codex (ChatGPT), and Gemini in one place.

<p align="center">
  <img src="docs/Screenshot_20260305_025201_CodexBar.jpg" width="320" alt="Dashboard" />
  &nbsp;&nbsp;
  <img src="docs/Screenshot_20260305_025207_CodexBar.jpg" width="320" alt="Settings" />
</p>

## Features

- Real-time quota monitoring for Claude, Codex, Gemini, and GitHub Copilot
- Animated gauge bars showing remaining usage percentage
- Quick Settings tile for at-a-glance status
- Configurable Android home screen widgets with quota, reset, freshness, and pace controls
- Background refresh with configurable intervals
- Persistent notification with per-service breakdown and a time-bound live monitoring mode
- Dashboard detail sheets with quota windows, reset times, freshness, and pace forecast
- Push alert when quota resets (fully replenished)
- DataStore + Android Keystore-backed credential storage
- Material 3 with Dynamic Color

## Download

Signed APKs are available on this fork's [Releases](https://github.com/lingmulongtai/CodexBar-android/releases/latest) page.

For the shortest install path, download the latest [app-release.apk](https://github.com/lingmulongtai/CodexBar-android/releases/latest/download/app-release.apk), transfer it to your Android device, and install it after allowing installs from your browser or file manager.

No backend server is used. Provider tokens are processed and stored strictly on-device.

## Security & Backup

Provider credentials are stored in `codexbar_secure_prefs`, an on-device DataStore whose values are encrypted with Android Keystore-backed AES-GCM keys. That DataStore is explicitly excluded from Android cloud backup and device-to-device transfer. After restoring or moving to a new device, providers must be linked again instead of reusing undecryptable credential ciphertext from the old device.

## Architecture & Verification

- [Provider account-linking architecture](docs/provider-auth-architecture.md)
- [Android widget feature parity](docs/widget-feature-parity.md)
- [Verification matrix](docs/verification-matrix.md)

## Setup

For normal use, install the signed APK from the latest release and open **Settings** to connect accounts or enter fallback tokens.

For local development:

1. Install [OpenJDK 17](https://formulae.brew.sh/formula/openjdk@17) (or any JDK 17+)
2. Clone and open the project in Android Studio
3. Build and install the debug APK
4. Open the app and go to **Settings** to connect accounts or enter fallback tokens

## Connecting Accounts

### Claude (Anthropic)

Claude does not expose a supported Android device-code flow for third-party apps. Use Claude Code's long-lived setup token instead:

```bash
claude setup-token
```

Paste the generated OAuth token into the Claude **Access Token** field in Settings. Avoid copying raw Keychain exports into logs, notes, or issue reports.

### Codex (OpenAI / ChatGPT)

Use **Connect account** in Settings. The app uses Codex's device-code login: it shows a one-time code, opens the OpenAI sign-in page, polls for completion, and saves the returned tokens only after validation.

Manual fallback, if needed:

```bash
# Access token
cat ~/.codex/auth.json | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['tokens']['access_token'])"

# Refresh token
cat ~/.codex/auth.json | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['tokens']['refresh_token'])"
```

Do not extract bearer tokens from browser DevTools unless you are debugging locally and understand the exposure risk.

### Gemini (Google)

Use **Connect account** in Settings after entering a Google OAuth Client ID for a public/native client. The app uses Google's device authorization grant with the `https://www.googleapis.com/auth/cloud-platform` scope, then stores the access token, refresh token, and client ID encrypted on-device. Client secrets are not accepted, stored, or sent because native Android apps cannot keep them confidential.

Manual fallback, if needed:

```bash
# 1. Access token
python3 -c "import json; print(json.load(open('$HOME/.gemini/oauth_creds.json'))['access_token'])"

# 2. Refresh token
python3 -c "import json; print(json.load(open('$HOME/.gemini/oauth_creds.json'))['refresh_token'])"
```

Paste the access token, refresh token, and OAuth Client ID into Settings only when you cannot use the in-app account link flow. Do not paste a Google OAuth client secret; this app rejects that pattern.

### GitHub Copilot

Use **Connect account** in Settings. The app uses GitHub's device flow and then fetches Copilot quota data from GitHub's Copilot user endpoint.

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Recommended local checks before opening a PR:

```bash
./gradlew --dependency-verification=strict testDebugUnitTest lint assembleDebug
```

## Dependency Verification

Gradle Wrapper distribution checksums and dependency verification metadata are committed. When intentionally changing Gradle, plugins, or libraries, regenerate verification metadata with:

```bash
./gradlew --write-verification-metadata sha256 lint testDebugUnitTest assembleDebug
```

Review and commit `gradle/verification-metadata.xml` together with the dependency change. CI runs with `--dependency-verification=strict` and validates the Gradle Wrapper before compilation.

## Release Builds

Public releases are built only from `v*` Git tags by the protected release workflow. Signing material must stay in GitHub environment secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release workflow publishes signed APK/AAB artifacts, `SHA256SUMS`, a CycloneDX dependency SBOM, a build provenance JSON file, and GitHub artifact attestations. Debug APKs from CI are short-lived test artifacts only.

## Tech Stack

- Kotlin 2.1.0, Jetpack Compose, Material 3
- Hilt (DI), Retrofit2 + OkHttp (networking)
- WorkManager (background sync), DataStore + Android Keystore-backed encryption
- Glance AppWidget, Quick Settings tile, Android notification/live monitoring APIs
- KSP, kotlinx.serialization

## Acknowledgments

Based on [CodexBar](https://github.com/steipete/CodexBar) by Peter Steinberger.

## License

[MIT](LICENSE)
