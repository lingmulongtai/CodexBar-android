# v0.4.0 bounded Android runtime security review

Date: 2026-07-16

## Scope and method

This was a short, single-pass review of Android runtime code under `app/src/main`, prioritizing issues that could plausibly be Critical or High. The reviewed surfaces were authentication/OAuth, API clients, token and secret storage, WebView use, exported components, Intent/deep-link handling, file access, backup rules, and logging.

Excluded as requested: `build`, `.gradle`, generated output, tests as vulnerability targets, external libraries, binaries, and general documentation. This is not a claim of complete file-by-file coverage or a third-party dependency audit.

## Result

No confirmed Critical or High vulnerability was found in the reviewed runtime paths.

The release closes the highest-risk Gemini design from earlier builds: Android no longer stores a Gemini CLI access token, refresh token, OAuth client secret, or calls Google's internal `cloudcode-pa` quota service. Gemini uses an authenticated and encrypted local snapshot protocol around the official CLI instead.

## Controls verified

- Credential and device-code HTTP clients have no logging interceptor, disable redirects, and use bounded response bodies. Normal API logging is `BASIC` metadata only with authorization, cookie, and API-key headers redacted.
- All provider/auth/update URLs found in runtime Kotlin are HTTPS. The manifest explicitly sets `android:usesCleartextTraffic="false"`; the Gemini local TCP response remains protected by AES-256-GCM and authenticated request/response metadata rather than relying on HTTP.
- Credential values and the Gemini pairing key are encrypted with an Android Keystore-backed 256-bit AES-GCM key. The secure DataStore is excluded from cloud backup and device-to-device transfer.
- Gemini pairing accepts only an exact field set, numeric local/private address, bounded port, UUID companion ID, and a 256-bit base64url key. It rejects duplicate/unknown fields, stale responses, nonce mismatches, oversized responses, stale snapshots, invalid percentages, and implausible reset horizons.
- The scanned Gemini URI only fills a masked field; the user must explicitly verify before the pairing is saved. The Activity clears the secret-bearing URI from its retained Intent after import.
- Codex uses a fixed `auth.openai.com` verification URL. GitHub-provided verification URLs are accepted only when HTTPS, host-exact `github.com`, and path-exact `/login/device`.
- No WebView, JavaScript bridge, runtime file read/write API, `Log`, `Timber`, body/header HTTP logging, or cleartext provider URL was found in the bounded search.
- Exported receivers either require a platform binding permission or validate the protected action. Notification action receivers and the widget receiver are not exported; all notification `PendingIntent` values are immutable.

## Deferred and residual items

- **Custom URI ownership (Low):** `codexbar://gemini-pair` is an unverified custom scheme. A malicious installed app that registers the same scheme and is selected by the user could see the local pairing key and retrieve sanitized quota snapshots. It cannot derive or use a Google credential. A future in-app QR scanner or verified HTTPS App Link would remove this residual interception path.
- **Android 17 local-network permission (Deferred):** v0.4.0 targets API 36, where `INTERNET` still permits local TCP. Before targeting API 37, add and test the new runtime local-network permission flow.
- **Device/OEM validation (Deferred):** lock-screen redaction, promoted Live Update/One UI placement, widget launcher behavior, VPN/client-isolation behavior, and process restoration still require physical-device or broader emulator testing.
- **External behavior (Deferred/excluded):** provider-side endpoint changes, official Gemini CLI output changes, npm/Gradle package internals, and binary/toolchain analysis were outside this bounded source review. CI separately runs strict Gradle verification and `npm audit` for the pinned companion lockfile.

## Verification evidence

- Android: 149 unit tests across 57 suites, 0 failures; Android Lint completed with 0 errors; debug APK, minified release APK, release AAB, and CycloneDX SBOM generated under strict dependency verification.
- Gemini companion: clean `npm ci`, syntax validation, 7 tests including authenticated socket encryption and nonce replay rejection, `npm audit --omit=dev` with 0 vulnerabilities, and CycloneDX SBOM generation.
- The signed public APK is still gated by production-certificate verification and an API 36 emulator cold-start in the tag release workflow.
