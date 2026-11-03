# Liquid Glass design contract

This document records the owner's direction for the Android interface. Liquid Glass is the only selectable app design; legacy saved theme names and launcher aliases remain compatible. The native implementation is based on [Glisse](https://glisse.kelvinzhang.com/) and adapts its rendering to Android.

## Materials and layers

- Use the daily photo as the background and keep its photographer/Unsplash attribution at the end of each scrollable page, never fixed above the navigation or covering content.
- Main panels are frosted glass. Nested fields, actions and panels must not resample a clearer copy of the original wallpaper through an already frosted parent: use a restrained flat tonal surface inside it. Controls remain clearly actionable and keep their accessibility, focus, disabled and error states.
- Keep labels and icons sharp and at their normal size when a lens grows. Do not stretch, duplicate or magnify foreground text and icons. Switches and slider thumbs retain Glisse's intentionally elongated pressed lens.
- Preserve the header's established position, dimensions, corner radii and wording. The top is black, transitioning into clear glass over the actual scrolling content. It uses Glisse as the reference (bend 0.45, dispersion 0.4, frost 0 px, rim 0.13). Do not add Siri voice indicators, animated glow, or an artificial blue tint.
- The header is fixed above content on Usage, Trends, Connections and Settings. Scrolling cards pass behind and appear through its glass. The History/Spend selector stays attached beneath the Trends header and content passes behind it.

## Motion and controls

- Usage, Trends, Connections and GitHub are adjacent pages on one continuous horizontal track. A body swipe moves both neighboring pages with the finger, supports reversal/cancellation and settles naturally; tab taps scroll that same track through intermediate pages without pre-jumping. Keep a fixed full-height viewport and each page’s own vertical content origin, so header-height changes cannot produce diagonal movement. Keep the header and bottom bar pinned; header titles switch immediately and the settings action stays visible without an entry fade. The pinned glass header samples all moving page bodies. Preserve RTL and the app-owned Dev animation speed. History/Spend uses an independent continuous track selected by the pinned control, with separate saved scroll positions.
- Tab and segmented selections support tap and drag, including a held drag, and cancellation must not commit. Reorder mode enters and exits with a gentle transition; service order and drag behavior remain unchanged. App-authored motion uses the saved Dev speed independently of Android animation-duration settings.
- Follow Glisse proportions and pressed states for switches, sliders and the bottom tab bar. Slider movement is smooth while values snap to their specified step; persist on release where appropriate.
- Settings and Dev settings are separate panes above the retained tab track. Opening Settings never replaces a tab or its saved state. Back and selecting the originating tab return to that tab; Back during an in-progress pane transition must not exit the app. Offscreen pages cannot intercept Back, show dialogs or apply Dev previews. Each page owns its swipe lock.
- Usage, Trends and Connections have independent saved provider orders. Trends History and Spend share one order. Reuse Usage’s header Reorder/Done action, long-press dragging, accessible move actions and reset; retain filtered/disconnected provider positions. Editing disables body tab swipes, and Back finishes editing first.
- Settings → Haptic feedback (default on) controls app haptics and persists across restarts. Tab/segmented presses and switch/slider lens lifts emit one light [Android SegmentTick](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType); holding, dragging, release and programmatic selection do not repeat it. Coalesce overlapping pulses within 80 ms. Disabling it also silences existing reorder feedback, and no pulse occurs before preferences have loaded. Respect Android’s system haptic settings and hardware support.
- Refresh is a plain action in the bottom bar; refresh progress belongs in the header. Retain scroll position when navigating away and back.

## Developer tuning

- Settings → Dev settings provides an app-owned animation speed (0.25–3×, default 1×), independent of Android animator scale, including Android scale 0.
- Four material groups (cards, header, tab bar, controls) can override frost, bend strength, dispersion, rim opacity, edge shine, saturation, tint opacity and bend reach. Inherited values preserve the existing component defaults exactly; reset restores inheritance.
- Keep shapes, positions, corner radii and dimensions unchanged. Multiply bend/reach so pressed-only and zero-bend surfaces retain their behavior. Keep nested panels on their existing translucent fallback instead of sampling sharper wallpaper through a frosted parent.
- Preview is immediate, saves happen on gesture completion, and leaving cancels only an uncommitted draft. Delayed saves must not overwrite newer previews.

## Content and validation

- Avoid redundant dark category banners such as “Connect AI providers” or “App settings”; keep actual settings and connection functions. The connection-count summary is translucent blue glass. The Usage services label is a centered, legible glass badge, while the Unsplash attribution retains its existing dark style.
- Default buttons, including service refresh/connection management and safe QR scanning, use neutral Glisse-style glass with legible labels. Keep explicit semantic colors where requested.
- Service details should use the same glass vocabulary as the rest of the app, including its outer surface and nested panels. Keep swipe-to-dismiss, Back, focus, text readability and actions intact.
- Preserve connected accounts, phone/Windows acquisition modes, saved connections, widget settings and launcher compatibility when changing presentation. Settings includes the installed version, project links and a manual update check which distinguishes up-to-date, unavailable and non-comparable preview versions. Settings sections share the same neutral glass treatment. Daily wallpaper favors portrait photography, retains attribution and keeps the previous image if a new selection cannot be downloaded.
- Validate initial spacing, scroll-under-header and readable controls on all four tabs, light and dark, as well as horizontal navigation and a service detail sheet. Confirm idle rendering does not continuously invalidate itself. A debug preview with synthetic data is not a production release or a substitute for the owner's physical-device review.

Reference checked: Glisse commit `46be5ba07b60eb507e887ff338ded7840576a79f` (MIT). Read-only upstream main checked at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; no upstream-only changes to incorporate or defer.
