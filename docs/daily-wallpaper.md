# Daily Liquid Glass photography

Liquid Glass uses one daily background shared by the visible screen and the background-only glass source. Material 3 Expressive keeps its own surfaces. Photos never include account information, usage data or credentials.

The default is a daily rotation through six selected Unsplash photographs. It requires no API key and is **not a live Editorial feed**. Selection follows the device's local calendar day. A successful selection and its image remain on the device, so navigation, a restart or an unavailable network does not clear the photo. Before any photo has loaded, the existing gradient remains available.

While Liquid Glass is visible, the app checks at local midnight and on foreground entry. The inexpensive daily guard prevents another network request on a successful day. Failed requests retry at most every 30 minutes while the app is foregrounded, or through the manual Settings action. There is no wallpaper background service or periodic worker. The HTTP cache is limited to 20 MiB; the selected image is capped at 8 MiB and decoded to a longest edge of at most 2048 pixels.

Settings → Daily photo provides enable/disable, current photo/date/status, manual update, attribution and optional personal Access Key controls. Keys are encrypted using the existing Android Keystore-backed preferences, excluded from backup and removed by Delete all credentials. The stored key is never displayed again, included in saved UI state, bundled in an APK, or put into a URL/log. Secret Keys are not requested. API requests use a dedicated, logging-free client with `no-store`; CDN image requests carry no API authorization. Changing settings cancels collector requests; independent manual requests re-check the current settings before tracking and publishing their result.

With an optional personal Access Key, the app selects a landscape-oriented item from `GET https://api.unsplash.com/photos?per_page=30&page=1`, the current Editorial endpoint. It preserves returned image query parameters, requests the original Unsplash CDN directly, and calls the returned download-location endpoint when applying a photo. The current Unsplash feed can also contain illustrations; the app does not claim a photo-only filter for this endpoint. A production-wide shared API integration would require a server-held key or an approved registration flow; no shared key is distributed here. [API documentation](https://unsplash.com/documentation) · [API guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines) · [API changelog](https://unsplash.com/documentation/changelog)

The visible attribution links the photographer's profile and Unsplash; all outgoing credit links include `utm_source=codexbar_android&utm_medium=referral`. Photos are cached only on the device and are not rehosted by this project.

## Curated photographs

Each source page was checked for the Unsplash License and Featured in Photos status on 2026-10-31; each CDN URL returned a decodable landscape JPEG. The images themselves are fetched from Unsplash rather than bundled in the APK. [Unsplash License](https://unsplash.com/license)

| Photograph | Photographer | CDN photo identifier |
| --- | --- | --- |
| [Boathouse on a mountain lake](https://unsplash.com/photos/VowIFDxogG4) | [Luca Bravo](https://unsplash.com/@lucabravo) | `photo-1470770903676-69b98201ea1c` |
| [Trees with sun rays](https://unsplash.com/photos/sp-p7uuT0tw) | [Sebastian Unrau](https://unsplash.com/@sebastian_unrau) | `photo-1448375240586-882707db888b` |
| [Mist covering mountain and forest](https://unsplash.com/photos/_v-EHHKKW3w) | [Adrian Infernus](https://unsplash.com/@adrian_infernus) | `photo-1543871645-b3be1624a0fc` |
| [After the storm](https://unsplash.com/photos/qfcBVgqJ_1E) | [Ricardo Gomez Angel](https://unsplash.com/@rgaleriacom) | `photo-1527749561912-77dfac6274db` |
| [Desert dunes](https://unsplash.com/photos/zp_iEPfwvw0) | [Wolfgang Hasselmann](https://unsplash.com/@wolfgang_hasselmann) | `photo-1716492444249-e5a6981daf58` |
| [Forest landscape](https://unsplash.com/photos/dJVU4jXV1Q8) | [Sebastian Unrau](https://unsplash.com/@sebastian_unrau) | `photo-1440847899694-90043f91c7f9` |

API parsing/host validation is covered by unit fixtures. A successful live Editorial request with a real personal key requires separate verification; no key was supplied during this change.
