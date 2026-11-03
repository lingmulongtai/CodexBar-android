# Stable launcher icon audit — 2026-11-04

Checked against the actual published `CodexBar-Android-v0.16.1.apk`, package `com.codexbar.android`, versionCode 46. This audit found no missing icon asset and no stable/preview resource overlay discrepancy.

- Approved source PNG SHA-256: `5de9ab034c06cfe6390cc3728e370c6023239483a85eb950b12a32fa0e4d4241`.
- The published resource table maps `drawable/codexbar_icon_art` to optimized `res/2Q.png`. Release resource optimization changes PNG compression and path names, so APK filenames/byte hashes alone are not an artwork comparison.
- Decoding both images gives 1254 × 1254 RGBA images with exactly equal pixels. The source PNG also matches the owner-supplied approved icon byte-for-byte.
- The application and default/round/Liquid Glass/retired launcher aliases resolve to the approved shared artwork. No debug/release icon override is present. An unused Instrument adaptive resource is not reachable from an installed launcher and does not explain the report.
- A fresh production-signed v0.16.1 install on the disposable API 36.1 emulator displayed the blue icon in Android App info. Before first launch the component was LauncherExpressive; after leaving MainActivity it was LauncherLiquidGlass. Both referenced the same approved artwork.
- A new XML-graph regression test checks every manifest launcher icon/roundIcon, the adaptive background/monochrome resources, and the bitmap source. Existing legacy alias names were retained; no component was removed or renamed.

The physical phone was not connected for this audit. An older installed version, a launcher cache or a custom/themed launcher icon remains a possible explanation, not an established root cause. No cache deletion, app-data deletion or unapproved icon redesign was performed on the owner's phone.

Read-only upstream main was fetched and checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`, with no upstream-only changes to port. Prior continuous-pager commits are recorded in `continuous-pager-2026-11-03.md`. v0.16.2 uses versionCode 47, the existing release signer and the existing release workflow.
