# Verification matrix

This repository now uses layered verification because the app spans credentials, workers, notifications, Glance widgets, and release artifacts.

## Unit and source-level checks

| Area | Coverage |
| --- | --- |
| Credential logging | `NetworkModuleTest` asserts sentinel access/refresh/client-secret values are not emitted by the debug metadata logger and that token clients have no logging interceptor. |
| Backup/data transfer | `BackupRulesTest` verifies DataStore credentials, token refresh state, monitoring session state, and widget display cache are excluded from legacy backup and Android 12+ extraction rules. |
| Secure storage | `SecurePreferencesImplementationTest` verifies DataStore plus Android Keystore value encryption and absence of `EncryptedSharedPreferences`. |
| Gemini secret removal | `GeminiClientSecretRemovalTest` fails if main source reintroduces Gemini `client_secret` handling. |
| Retry-After parsing | `RetryAfterTest` and `RetryInterceptorTest` cover malformed, negative, overflow, date, and capped retry behavior. |
| Token refresh races | `TokenRefreshRetryPolicyTest` covers provider/account-scoped retry state, terminal failure behavior, and account-fingerprint changes. |
| Presentation snapshot | `QuotaPresentationMapperTest` covers shared labels, bars, freshness, privacy, and extra usage mapping. |
| Pace/history | `QuotaPaceCalculatorTest` covers sparse samples, reserve, reset windows, and forecast states. |
| Monitoring session | `MonitoringSessionTest` covers explicit start/end and remaining-duration math. |

## Manual release smoke checklist

Before publishing a non-beta tag:

1. Run strict dependency verification.
2. Run debug unit tests.
3. Run Android lint.
4. Build debug and release APKs.
5. Build release AAB.
6. Generate release SBOM.
7. Confirm no debug APK is attached as the normal release asset.
8. Confirm release notes identify the exact git tag and commit.

## Visual/integration coverage still requiring devices

The repository includes generated/static widget previews and notification code paths, but full screenshot and instrumentation coverage still requires an Android device or emulator matrix:

- API 35 fallback notification and API 36 promoted Live Update eligibility.
- Light/dark/dynamic color.
- Font scale up to 200%.
- RTL layout.
- Phone, tablet, landscape, and launcher widget resize grids.
- TalkBack traversal for dashboard, settings, widget configuration, and monitoring controls.
