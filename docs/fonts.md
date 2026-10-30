# Bundled fonts

The Liquid Glass design uses Inter for a neutral screen face. Analytics retain IBM Plex Mono; earlier captures used Figtree. The app bundles these font families under `app/src/main/res/font/`. Both are licensed under the SIL Open Font License 1.1 and are included unmodified. Japanese and other characters these fonts lack fall back to the system font, so no CJK font is bundled.

| Resource | Family | Source | SHA-256 |
| --- | --- | --- | --- |
| `inter_variable.ttf` | Inter (optical size and variable weight) | [google/fonts `ofl/inter/Inter[opsz,wght].ttf`](https://github.com/google/fonts/tree/main/ofl/inter) | `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031` |
| `figtree_variable.ttf` | Figtree (variable weight) | [google/fonts `ofl/figtree/Figtree[wght].ttf`](https://github.com/google/fonts/tree/main/ofl/figtree) | `26ad3db9b31ff7dde67a91ff515d022d2f495cd506590699cf264f0bfe6fb714` |
| `ibm_plex_mono_regular.ttf` | IBM Plex Mono Regular | [google/fonts `ofl/ibmplexmono/IBMPlexMono-Regular.ttf`](https://github.com/google/fonts/tree/main/ofl/ibmplexmono) | `6a3412f058c7d8dfd9170c41e85ade48e5156ecb89356110ca57a0a27734af46` |
| `ibm_plex_mono_medium.ttf` | IBM Plex Mono Medium | [google/fonts `ofl/ibmplexmono/IBMPlexMono-Medium.ttf`](https://github.com/google/fonts/tree/main/ofl/ibmplexmono) | `a9b4c49bb299e05b5f6c481e7fb5e78943d2793249a0c8874ab574a2d1ea6755` |
| `ibm_plex_mono_semibold.ttf` | IBM Plex Mono SemiBold | [google/fonts `ofl/ibmplexmono/IBMPlexMono-SemiBold.ttf`](https://github.com/google/fonts/tree/main/ofl/ibmplexmono) | `d3c38e55c78f5b0f28009fddba4834ec503278936a5986032424c9bd2d23aa46` |

License texts: [Figtree](fonts/OFL-Figtree.txt) · [IBM Plex Mono](fonts/OFL-IBMPlexMono.txt) (Reserved Font Name "Plex").

Inter license: [SIL OFL 1.1](fonts/OFL-Inter.txt). Apple SF fonts are not bundled. Japanese falls back to the device typeface; Liquid Glass uses increased line height and neutral letter spacing.
