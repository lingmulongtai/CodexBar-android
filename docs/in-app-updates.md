# In-app APK updates

The update action in both the startup prompt and Settings → About CodexBar downloads the APK inside CodexBar. The browser is no longer part of the normal download flow. Project and release-page links remain ordinary links.

The UI distinguishes waiting for a network, downloading (percentage and transferred bytes), checking the file, ready to install, cancelled and failed. Closing the prompt keeps the request running; reopen Settings to find it. Android may defer background work. WorkManager retains the request across process recreation; an interrupted transfer starts again from zero rather than trusting an unverified partial file. A verified completed file is reused, including after the app is reopened. Once its version is installed, the old update action disappears.

Press **Install [version]** to open Android's package installer. If Android has not yet allowed CodexBar to request installations, its per-app source setting opens first; granting it returns to the installer flow. The user still confirms the update in Android. CodexBar does not perform silent installation. See [Android's install-source permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

## Verification and boundaries

- Discovery requires an exact APK asset from this fork's versioned GitHub release, a bounded size and the API's SHA-256 digest. Missing/invalid verification metadata does not trigger a browser download. See [GitHub release asset metadata](https://docs.github.com/en/rest/releases/assets).
- A dedicated unauthenticated client streams the file privately. It does not carry GitHub account tokens or provider credentials. Every redirect must remain on HTTPS and an explicitly allowed GitHub asset host. At most five redirects are accepted.
- The total transfer has a three-minute deadline and a 30-second read timeout. The deadline becomes a visible retryable failure, rather than leaving the worker running indefinitely. Cancellation closes the socket and removes that request's partial file.
- Completion requires the expected byte count and SHA-256, the same package ID, the selected version name, a greater version code and the exact current installed signer set. This deliberately rejects a production APK over an incompatible development signer.
- Validation runs again immediately before installer handoff. A failed ready-file check offers download retry. A newly available version can replace an older saved download, and the Install button names the actual saved version.
- Each request uses a UUID-named file so an older cancelled job cannot delete a newer job's file. Only an atomic rename exposes the verified APK. A non-exported [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider) grants temporary read access to the installer; only `files/updates/` is exposed. APKs are excluded from cloud backup and device transfer. Expired update files are pruned on later downloads.
- One unique WorkManager root request uses REPLACE and no appended child chain. Both UI entrypoints observe it. A foreground data-sync notification provides progress and cancellation. No installer is launched automatically from background work. See [WorkManager foreground transfers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

The user's reported Vivaldi download displayed all bytes received but never completed. No live browser state/log was available, and no exact official Android/GitHub report was found. A separate Slack download-completion regression appears in the [Vivaldi Android 8.3 RC1 changelog](https://vivaldi.com/blog/android/android-8-3-4192-10/); it does not establish the cause here. This change removes the browser dependency without claiming a confirmed Vivaldi root cause.

## Development checks

Focused tests cover asset trust, size/digest metadata, package/version/signer identity, complete/truncated/oversized/wrong-hash streams, redirect escape/loops, cancellation, socket stalls, whole-transfer deadlines, installer revalidation, pre-verification progress and file-sharing/backup boundaries. Debug-only `UpdateProofActivity` and `UpdateScenarioWorker` provide explicitly opted-in, disposable-emulator fixtures and a real public-release HTTPS download check. They are excluded from production source sets.

Local checks completed 514 tests: 512 passed and two explicit opt-in Windows interoperability tests skipped. Debug lint and APK assembly passed. On a disposable API 36.1 emulator, the real v0.16.0 APK transferred all 6,744,190 bytes in approximately two seconds, matched GitHub's SHA-256 and parsed as the expected package/version. The production foreground worker also downloaded/verified it, then correctly rejected it as not newer than the installed version.

Separate controlled debug fixtures exercised progress, cancellation, visible network failure/retry, persisted READY state after a process restart, install-source permission and the actual Android installer. A debug-signed fixture labeled 0.17.0/code46 successfully replaced the test app and preserved a private test marker. That fixture is not a public v0.17.0 release or proof of an official signed release update. No physical-device verification is claimed.

Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; there were no upstream-only changes to incorporate or defer. Work started from released main `e189251` (v0.16.0). Existing accounts, connections, release signer and acquisition modes are preserved.
