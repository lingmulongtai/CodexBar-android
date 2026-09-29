# CodexBar Windows Control Center

One Windows tray app displays four PC-linked sources and keeps their private Android bridges running:

| Source | Windows display | Android bridge |
| --- | --- | --- |
| Claude Code | Plan 5-hour and 7-day usage from `/usage` | `43823` |
| Antigravity | Measured app model-group weekly/5-hour quotas (or older IDE model quotas) | `43824` |
| Gemini CLI | Model quota windows from `/model` | `43821` |
| Codex | Aggregate local session tokens and context, not subscription limits | `43822` |

Install from the repository on the Windows PC with Node.js 20+ and the relevant official CLIs signed in:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File companion/windows-control-center/scripts/windows-control-center.ps1 -Action Install -Address YOUR_PC_PRIVATE_IPV4
```

For different networks, use the PC's Tailscale `100.x` address and keep both devices connected to the same tailnet. The installer copies pinned companion packages under `%LOCALAPPDATA%\CodexBar\ControlCenter`, verifies its child-process job, preserves all `.codexbar` pairing identities, and replaces the separate Claude and Antigravity tray residents after the new package is ready. It installs one current-user startup task with a one-minute watchdog; a Startup-folder shortcut is used if Task Scheduler is unavailable. The tray icon opens the dashboard, restarts or pauses the four bridges, and can remove auto-start without deleting identities.

Use **Pair phone QR** on a provider card. Claude and Antigravity QR codes must be scanned inside **CodexBar → Connections**; Gemini and Codex use the Android pairing flow. The QR contains that provider's private pairing key, appears only on demand, and is never saved as an image or written to logs. Existing Claude and Antigravity phone pairings keep their same keys and ports. Codex and Gemini get identities only when their Windows workers first start.

The four sources continue to have separate authentication. The Control Center reads only each companion's encrypted, authenticated local snapshot and displays bounded usage fields. Phone snapshots retain the existing HMAC, nonce, AES-GCM and replay protections. No prompts, conversations, OAuth credentials, account email, or raw CLI screen contents are displayed or forwarded. A missing source is shown as unavailable; it is never displayed as 0% used. Antigravity must be open for app quotas. Gemini CLI must already be signed in from `~/.codexbar/gemini-workspace` and able to show model limits under `/model`; this app does not automate login or folder trust.

Run `npm ci`, `npm test`, and `npm run check` in this directory. `-Action Check` validates the Windows UI and job-object prerequisites. To stop auto-start while preserving identities, run the script with `-Action Uninstall`.
