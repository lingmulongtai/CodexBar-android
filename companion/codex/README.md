# CodexBar Codex telemetry companion

This optional companion adds local Codex CLI context and token insights to CodexBar for Android. Codex subscription quota still comes directly from OpenAI; this tool only supplies information that exists on your computer in `~/.codex/sessions`.

Requirements:

- Node.js 20 or newer
- A current Codex CLI or Codex desktop installation
- The computer and Android phone on the same trusted Wi-Fi network

Extract the release archive. On Windows, double-click `start-windows.cmd`. On macOS or Linux, run:

```shell
./start-macos-linux.sh
```

The launchers install the exact dependency from `package-lock.json` on first use. The equivalent manual commands are:

```shell
npm ci --omit=dev
npm start
```

Scan the displayed QR code with the system camera. CodexBar opens the Codex connection card with the pairing value hidden; tap **Pair & verify telemetry companion**.

The companion reads only `token_count` and model metadata from recent session JSONL files. It returns aggregate input, cached-input, output, and reasoning-token counts; daily/model totals; and the newest context size. It never returns prompts, responses, file paths, working directories, session IDs, account tokens, or source files.

The server binds to one numeric private address, accepts a bounded JSON request, authenticates it with HMAC-SHA256, rejects replayed nonces, rate-limits clients, and returns an AES-256-GCM encrypted snapshot. The persistent pairing identity is stored in `~/.codexbar/codex-telemetry-companion.json` with user-only permissions where the platform supports POSIX modes.

Options:

```text
--address 192.168.1.20
--port 43822
--interval-minutes 2
--codex-home /path/to/.codex
```

If Windows Defender Firewall asks, allow access only on private networks. Do not expose this port through router forwarding, a public IP, or a public tunnel.
