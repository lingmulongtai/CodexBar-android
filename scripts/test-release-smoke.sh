#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/app/build/outputs/apk/release"
touch "$tmp/app/build/outputs/apk/release/test.apk"
cat > "$tmp/bin/adb" <<'ADB'
#!/bin/sh
case "$1 $2 $3 $4" in
  'install --no-incremental '*) exit 0 ;;
  'install '*) exit 8 ;;
  'get-state '*)
    if [ "${FAKE_MODE:-}" = disconnected ]; then exit 1; fi
    if [ "${FAKE_MODE:-}" = hangs ]; then /bin/sleep 3; fi
    echo device ;;
  'shell true '*)
    if [ "${FAKE_MODE:-}" = stale ]; then exit 1; fi ;;
  'shell cmd package resolve-activity'*) echo com.codexbar.android/.MainActivity ;;
  'shell am start -W'*)
    if [ "${FAKE_MODE:-}" = failstart ]; then exit 1; fi
    if [ "${FAKE_MODE:-}" = badlaunch ]; then echo 'Status: timeout'; else echo 'Status: ok'; fi ;;
  'shell pidof '*)
    if [ "${FAKE_MODE:-}" != dead ]; then echo 123; fi ;;
  'logcat -b crash -d'*)
    if [ "${FAKE_MODE:-}" = crash ]; then echo 'Process: com.codexbar.android'; fi ;;
esac
ADB
cat > "$tmp/bin/sleep" <<'SLEEP'
#!/bin/sh
exit 0
SLEEP
chmod +x "$tmp/bin/adb" "$tmp/bin/sleep"
cd "$tmp"
export PATH="$tmp/bin:$PATH" ADB_TIMEOUT_SECONDS=1 ADB_INSTALL_TIMEOUT_SECONDS=3
sh "$repo/.github/scripts/smoke-test-release.sh" >/dev/null
for mode in disconnected stale hangs failstart badlaunch dead crash; do
  if FAKE_MODE="$mode" sh "$repo/.github/scripts/smoke-test-release.sh" >"$tmp/$mode.log" 2>&1; then
    echo "Unexpected success: $mode" >&2; exit 1
  fi
done
grep -q 'ADB device disconnected' "$tmp/disconnected.log"
grep -q 'ADB device disconnected' "$tmp/stale.log"
grep -q 'ADB device disconnected' "$tmp/hangs.log"
grep -q 'ADB could not start the release app' "$tmp/failstart.log"
grep -q 'Host memory and emulator process diagnostics' "$tmp/failstart.log"
grep -q 'did not report a successful launch' "$tmp/badlaunch.log"
grep -q 'process exited' "$tmp/dead.log"
grep -q 'produced a crash' "$tmp/crash.log"
echo 'PASS full install, disconnected, stale and hung ADB, failed launch, process exit, and crash detection'
