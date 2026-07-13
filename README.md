> **⚠️ Known Issue:** Claude's refresh token is consistently failing to renew. Investigating workarounds.

# CodexBar for Android

> Android port of [**CodexBar**](https://github.com/steipete/CodexBar) by [@steipete](https://github.com/steipete) — the macOS menu bar app for monitoring AI service quotas.

Monitor your AI service quotas from your Android device. Track remaining usage for Claude, Codex (ChatGPT), and Gemini in one place.

<p align="center">
  <img src="docs/Screenshot_20260305_025201_CodexBar.jpg" width="320" alt="Dashboard" />
  &nbsp;&nbsp;
  <img src="docs/Screenshot_20260305_025207_CodexBar.jpg" width="320" alt="Settings" />
</p>

## Features

- Real-time quota monitoring for Claude, Codex, and Gemini (more services coming soon)
- Animated gauge bars showing remaining usage percentage
- Quick Settings tile for at-a-glance status
- Background refresh with configurable intervals
- Persistent notification with per-service breakdown
- Push alert when quota resets (fully replenished)
- Encrypted credential storage
- Material 3 with Dynamic Color

## Download

Pre-built APKs are available on the [Releases](https://github.com/hyunnnchoi/codexbar-android/releases) page. 

I do **system software** for a living — I'm not smart enough to sneak viruses into an Android app. 

No backend server — all tokens are processed and stored strictly on-device. 

## Security & Backup

Provider credentials are stored in `codexbar_secure_prefs`, an encrypted on-device preferences file. That file is explicitly excluded from Android cloud backup and device-to-device transfer. After restoring or moving to a new device, providers must be linked again instead of reusing undecryptable credential ciphertext from the old device.


## Setup

1. Install [OpenJDK 17](https://formulae.brew.sh/formula/openjdk@17) (or any JDK 17+)
2. Clone and open in Android Studio
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
- WorkManager (background sync), EncryptedSharedPreferences (security)
- KSP, kotlinx.serialization

## Acknowledgments

Based on [CodexBar](https://github.com/steipete/CodexBar) by Peter Steinberger.

## License

[MIT](LICENSE)
