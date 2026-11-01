#!/bin/sh
set -eu

release_apk=''
release_apk_count=0
for candidate in app/build/outputs/apk/release/*.apk; do
  [ -f "$candidate" ] || continue
  release_apk="$candidate"
  release_apk_count=$((release_apk_count + 1))
done
if [ "$release_apk_count" -ne 1 ]; then
  printf '::error::Expected exactly one release APK for startup smoke testing.\n'
  exit 1
fi

package_name='com.codexbar.android'
# Bound every ADB call so a disconnected emulator cannot stall diagnostics forever.
adb_bounded() { timeout "${ADB_TIMEOUT_SECONDS:-30}" adb "$@"; }
report_host_diagnostics() {
  printf '%s\n' 'Host memory and emulator process diagnostics:'
  free -m 2>/dev/null || true
  ps -eo pid,stat,rss,comm 2>/dev/null | awk 'NR == 1 || $4 ~ /qemu|emulator/' || true
  if [ -r /sys/fs/cgroup/memory.events ]; then cat /sys/fs/cgroup/memory.events; fi
}
assert_device() {
  state="$(adb_bounded get-state 2>/dev/null || true)"
  if [ "$state" != 'device' ] || ! adb_bounded shell true >/dev/null 2>&1; then
    printf '::error::ADB device disconnected or did not respond during release smoke test.\n'
    report_host_diagnostics
    exit 1
  fi
}
check_launch() {
  if ! output="$(adb_bounded shell am start -W "$@")"; then
    printf '::error::ADB could not start the release app.\n'
    exit 1
  fi
  printf '%s\n' "$output"
  if ! printf '%s\n' "$output" | tr -d '\r' | grep -Fxq 'Status: ok'; then
    printf '::error::Release activity did not report a successful launch.\n'
    exit 1
  fi
}
check_running() {
  assert_device
  app_pid="$(adb_bounded shell pidof "$package_name" || true)"
  app_pid="$(printf '%s' "$app_pid" | tr -d '\r')"
  if [ -z "$app_pid" ]; then
    assert_device
    printf '::error::Release app process exited during %s.\n' "$1"
    report_host_diagnostics
    adb_bounded logcat -b crash -d || true
    exit 1
  fi
  if ! crash_output="$(adb_bounded logcat -b crash -d)"; then
    printf '::error::Could not read the release app crash log.\n'
    exit 1
  fi
  if printf '%s\n' "$crash_output" | grep -Fq "Process: $package_name"; then
    printf '::error::Release app produced a crash during %s.\n%s\n' "$1" "$crash_output"
    exit 1
  fi
}

# Install the entire signed APK before launch; do not depend on incremental serving.
timeout "${ADB_INSTALL_TIMEOUT_SECONDS:-180}" adb install --no-incremental "$release_apk"
assert_device
adb_bounded logcat -b crash -c
adb_bounded shell am force-stop "$package_name"
launcher_component="$(adb_bounded shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  "$package_name" | tr -d '\r' | tail -n 1)"
case "$launcher_component" in
  "$package_name/"*) ;;
  *) printf '::error::No launcher component for %s.\n' "$package_name"; exit 1 ;;
esac
check_launch -n "$launcher_component"
sleep 5
check_running startup

adb_bounded logcat -b crash -c
check_launch -a android.intent.action.VIEW -d 'codexbar://connections' "$package_name"
sleep 5
check_running 'opening Connections'
