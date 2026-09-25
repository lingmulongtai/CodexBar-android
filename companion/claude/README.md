# CodexBar Claude companion

This companion keeps Anthropic authentication inside the official Claude Code CLI and serves only a sanitized plan-usage snapshot to CodexBar for Android over the local network.

## Requirements

- Node.js 20 or newer
- The current official Claude Code CLI, already signed in
- The computer and Android phone on the same trusted Wi-Fi network, or connected through Tailscale

Claude Pro/Max usage is shared across Claude Desktop, claude.ai, and Claude Code. You can continue using the desktop app; the companion only needs the official CLI signed into the same subscription. Desktop sign-in alone does not sign the separate CLI in.

Install Claude Code using Anthropic's current instructions. On Windows, open PowerShell and run:

```powershell
claude auth login --claudeai
$workspaceDir = Join-Path $env:USERPROFILE '.codexbar\claude-workspace'
New-Item -ItemType Directory -Path $workspaceDir -Force | Out-Null
Set-Location -LiteralPath $workspaceDir
claude
```

On macOS or Linux, use the same dedicated workspace:

```shell
claude auth login --claudeai
mkdir -p "$HOME/.codexbar/claude-workspace"
cd "$HOME/.codexbar/claude-workspace"
claude
```

Complete browser sign-in, the first-run prompts, and the trust prompt for this dedicated workspace. Confirm that `/usage` shows **Current session**, then enter `/exit` before starting the companion. The companion reuses this stable, initially empty directory on every launch. Do not use the home directory itself: Claude can ask for trust again on every launch there, leaving unattended collection waiting at that prompt.

Do not use `claude setup-token` for this app. Anthropic documents that token for inference automation; it does not include the `user:profile` permission required to read plan usage.

## Start and pair

Extract the release archive. On Windows, double-click `start-windows.cmd`. On macOS or Linux, run:

```shell
chmod +x start-macos-linux.sh
./start-macos-linux.sh
```

The launchers install the exact dependencies from `package-lock.json` on first use. The equivalent manual commands are:

```shell
npm ci --omit=dev
npm start
```

The companion opens one dedicated official Claude Code terminal with tools disabled and reuses it for every refresh. It runs `/usage`, waits for loading to finish and the complete view to stabilize, parses only quota labels, percentages, reset text, an allowlisted plan name, and freshness, then discards the raw terminal output. It never serves Anthropic tokens, prompts, responses, files, email addresses, or session text.

On Android, open CodexBar → **Connections** → **Claude**, tap **Scan QR securely in CodexBar**, and scan the displayed QR. Do not use the system camera or another scanner: the QR contains the local pairing password and is intentionally not a web/app link. If Google Play services cannot open the in-app scanner, paste the displayed `CBCLAUDE1...` code into the hidden pairing field. Tap **Pair & verify Claude companion** and keep the companion window running while current usage is needed.

### Connect across networks with Tailscale

Keep Tailscale connected on both devices. Find the computer's Tailscale IPv4 address with `tailscale ip -4`, then pass that address explicitly when starting the companion. For example, on Windows, replace this example address with your computer's:

```text
start-windows.cmd --address 100.90.0.10
```

Scan the newly displayed QR in CodexBar and verify the connection again, even if a previous QR used the same pairing key. This saves the Tailscale address instead of the old LAN address. CodexBar supports numeric Tailscale IPv4 addresses in `100.64.0.0/10`; MagicDNS names and IPv6 are not supported. Automatic LAN discovery does not search Tailscale peers.

Using this computer as an exit node is compatible with this connection, but does not automatically change the companion's listening address. Ensure your tailnet access policy allows the phone to reach the computer on TCP port `43823`, and include CodexBar if the phone uses Tailscale app-based split tunneling. No router forwarding or public port is required. See Tailscale's [device connection guide](https://tailscale.com/docs/how-to/connect-to-devices).

## Troubleshooting

- If `/usage` says **Showing last-known usage**, the companion waits for a fresh reading instead of presenting cached quota as newly collected. Retry after the provider rate limit clears.
- If no snapshot is available, run `claude` from `~/.codexbar/claude-workspace` in a terminal, finish sign-in/trust prompts, enter `/usage`, then restart the companion. If upgrading from an earlier companion, confirm trust once for this new dedicated workspace; your Claude login and CodexBar pairing key stay unchanged.
- If Windows installed the npm launcher instead of the native CLI, run `start-windows.cmd --claude-command claude.cmd`.
- On macOS, the companion verifies and restores the executable bit on the pinned `node-pty` helper before every native PTY launch. If that fixed helper is missing, reinstall the companion dependencies instead of weakening system security settings.
- If the computer's address changes, CodexBar looks for the companion again on the phone's current subnet and re-pairs itself once the stored key authenticates the snapshot. Scanning a new QR is only needed when the pairing identity itself changed.
- The companion prefers the default-route Wi-Fi/Ethernet address and prints the selected interface plus alternatives. If the phone cannot connect, restart with `--address 192.168.x.x` using the listed Wi-Fi address, allow Node.js only on private networks in the computer firewall, and confirm the Wi-Fi does not use client isolation.
- Keep date and time automatic on both devices; requests outside the two-minute clock-skew window are rejected.

Options:

```text
--address 192.168.1.20
--port 43823
--interval-minutes 5
--claude-command /path/to/claude
--cli-version 2.1.123
```

The server binds to one numeric private address, authenticates bounded requests with HMAC-SHA256, rejects replayed nonces, rate-limits clients, and returns an AES-256-GCM encrypted snapshot. The pairing identity is stored in `~/.codexbar/claude-companion.json`. Do not expose the port through router forwarding, a public IP, or a public tunnel.
