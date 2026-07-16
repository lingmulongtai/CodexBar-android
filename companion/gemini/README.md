# CodexBar Gemini companion

This companion keeps Google authentication inside the official Gemini CLI and serves only a sanitized quota snapshot to CodexBar for Android over the local network.

Requirements:

- Node.js 20 or newer
- The current official `@google/gemini-cli`, already signed in
- The computer and Android phone on the same trusted Wi-Fi network

If needed, install and sign in to the official CLI first:

```shell
npm install -g @google/gemini-cli@latest
gemini
```

Extract the release archive. On Windows, double-click `start-windows.cmd`. On macOS or Linux, run:

```shell
./start-macos-linux.sh
```

The launchers install the exact dependencies from `package-lock.json` on first use. The equivalent manual commands are:

```shell
npm ci --omit=dev
npm start
```

The companion opens a dedicated official Gemini CLI terminal in screen-reader mode, runs `/stats`, parses only model label, used percentage, reset duration, and tier, then discards the raw terminal output. It never serves Google tokens, prompts, files, email addresses, or session content.

Scan the displayed QR code with the system camera. CodexBar opens the Gemini settings card with the pairing value hidden; tap **Pair & verify companion** to make the first authenticated request.

The server binds to one numeric private address, accepts a bounded JSON request, authenticates it with HMAC-SHA256, rejects replayed nonces, rate-limits clients, and returns an AES-256-GCM encrypted snapshot. The persistent pairing identity is stored in `~/.codexbar/gemini-companion.json` with user-only permissions where the platform supports POSIX modes.

Options:

```text
--address 192.168.1.20
--port 43821
--interval-minutes 5
--gemini-command /path/to/gemini
--cli-version 0.50.0
```

If Windows Defender Firewall asks, allow access only on private networks. Do not expose this port through router forwarding, a public IP, or a public tunnel.
