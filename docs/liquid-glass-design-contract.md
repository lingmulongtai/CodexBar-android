# Liquid Glass design contract

This document records the owner's direction for the Android interface. Liquid Glass is the only selectable app design; legacy saved theme names and launcher aliases remain compatible. The native implementation is based on [Glisse](https://glisse.kelvinzhang.com/) and adapts its rendering to Android.

## Materials and layers

- Use the daily photo as the background and keep its photographer/Unsplash attribution at the end of each scrollable page, never fixed above the navigation or covering content.
- Main panels are frosted glass. Nested fields, actions and panels must not resample a clearer copy of the original wallpaper through an already frosted parent: use a restrained flat tonal surface inside it. Controls remain clearly actionable and keep their accessibility, focus, disabled and error states.
- Keep labels and icons sharp and at their normal size when a lens grows. Do not stretch, duplicate or magnify foreground text and icons. Switches and slider thumbs retain Glisse's intentionally elongated pressed lens.
- Preserve the header's established position, dimensions, corner radii and wording. The top is black, transitioning into clear glass over the actual scrolling content. It uses Glisse as the reference (bend 0.45, dispersion 0.4, frost 0 px, rim 0.13). Do not add Siri voice indicators, animated glow, or an artificial blue tint.
- The header is fixed above content on Usage, Trends, Connections and Settings. Scrolling cards pass behind and appear through its glass. The History/Spend selector stays attached beneath the Trends header and content passes behind it.

## Motion and controls

- Bottom tabs switch whole screens with directional horizontal slides, preserving RTL and Back direction; do not use the default crossfade. The bottom navigation remains fixed. History/Spend transitions also slide horizontally without duplicating or flashing the selector.
- Tab and segmented selections support tap and drag, including a held drag, and cancellation must not commit. System reduced-motion settings must be respected.
- Follow Glisse proportions and pressed states for switches, sliders and the bottom tab bar. Slider movement is smooth while values snap to their specified step; persist on release where appropriate.
- Refresh is a plain action in the bottom bar; refresh progress belongs in the header. Retain scroll position when navigating away and back.

## Content and validation

- Avoid redundant dark category banners such as “Connect AI providers” or “App settings”; keep actual settings and connection functions.
- Service details should use the same glass vocabulary as the rest of the app, including its outer surface and nested panels. Keep swipe-to-dismiss, Back, focus, text readability and actions intact.
- Preserve connected accounts, phone/Windows acquisition modes, saved connections, widget settings and launcher compatibility when changing presentation.
- Validate initial spacing, scroll-under-header and readable controls on all four tabs, light and dark, as well as horizontal navigation and a service detail sheet. Confirm idle rendering does not continuously invalidate itself. A debug preview with synthetic data is not a production release or a substitute for the owner's physical-device review.

Reference checked: Glisse commit `46be5ba07b60eb507e887ff338ded7840576a79f` (MIT). Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only changes to incorporate or defer.
