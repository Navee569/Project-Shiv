#!/usr/bin/env bash
set -eu

ROOT="${GITHUB_WORKSPACE:-$(pwd)}"
cd "$ROOT"
REPORT="app/build/reports/emulator-smoke"
mkdir -p "$REPORT"
printf 'Smoke script started at %s\n' "$(date -u +%FT%TZ)" | tee "$REPORT/script-started.txt"
printf 'Working directory: %s\n' "$PWD" | tee -a "$REPORT/script-started.txt"

run_bounded() {
  label="$1"
  seconds="$2"
  shift 2
  output="$REPORT/${label}.txt"
  printf 'Running %s (timeout %ss):' "$label" "$seconds" | tee "$REPORT/${label}-command.txt"
  printf ' %q' "$@" | tee -a "$REPORT/${label}-command.txt"
  printf '\n' | tee -a "$REPORT/${label}-command.txt"
  if timeout "${seconds}s" "$@" >"$output" 2>&1; then
    printf '%s exit=0\n' "$label" | tee "$REPORT/${label}-status.txt"
    cat "$output"
  else
    status=$?
    printf '%s exit=%s timeout=%ss\n' "$label" "$status" "$seconds" | tee "$REPORT/${label}-status.txt" >&2
    cat "$output" >&2 || true
    return "$status"
  fi
}

# Collect startup evidence before any recovery attempt. The emulator-runner owns
# emulator lifecycle; recovery is limited to the ADB transport and never kills QEMU.
timestamp() { date -u +%FT%TZ; }
capture_diagnostics() {
  phase="$1"
  out="$REPORT/adb-diagnostics-${phase}.txt"
  {
    echo "timestamp=$(timestamp)"
    echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
    echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-unset}"
    echo "ADB=$(command -v adb || true)"
    echo "ADB_SERVER_SOCKET=${ADB_SERVER_SOCKET:-unset}"
    echo "ADB_VENDOR_KEYS=${ADB_VENDOR_KEYS:-unset}"
    echo "ADB version:"
    timeout 8s adb version 2>&1 || true
    echo "ADB server status:"
    timeout 8s adb server-status 2>&1 || true
    echo "ADB devices:"
    timeout 8s adb devices -l 2>&1 || true
    echo "Emulator processes:"
    ps -eo pid,ppid,stat,etime,args | grep -E '[e]mulator|[q]emu|[a]db' || true
    echo "Disk:"
    df -h / || true
    echo "Memory:"
    free -h || true
    echo "Boot properties (if transport permits):"
    timeout 8s adb -s emulator-5554 shell getprop sys.boot_completed 2>&1 || true
    timeout 8s adb -s emulator-5554 shell getprop dev.bootcomplete 2>&1 || true
    timeout 8s adb -s emulator-5554 shell getprop ro.boot.qemu 2>&1 || true
  } >"$out" 2>&1
  echo "Captured $out"
}

capture_diagnostics initial
# One global deadline and at most three reconnect attempts. This prevents the
# runner from repeatedly polling a stable offline transport for the full boot timeout.
deadline=$((SECONDS + 180))
attempt=0
while :; do
  if timeout 10s adb devices -l >"$REPORT/adb-devices-state.txt" 2>&1 &&
     grep -E 'emulator-[0-9]+[[:space:]]+device([[:space:]]|$)' "$REPORT/adb-devices-state.txt" >/dev/null; then
    echo "ADB state=online timestamp=$(timestamp)" | tee "$REPORT/adb-state-timeline.txt"
    break
  fi
  state="$(cat "$REPORT/adb-devices-state.txt" 2>/dev/null || true)"
  echo "timestamp=$(timestamp) attempt=$attempt state=$(printf '%s' "$state" | tr '\n' ';')" | tee -a "$REPORT/adb-state-timeline.txt"
  if [ "$attempt" -ge 3 ] || [ "$SECONDS" -ge "$deadline" ]; then
    capture_diagnostics exhausted
    echo "ADB did not transition to online within bounded recovery window (attempts=$attempt)." >&2
    cat "$REPORT/adb-devices-state.txt" >&2 || true
    exit 1
  fi
  attempt=$((attempt + 1))
  {
    echo "timestamp=$(timestamp) action=adb-reconnect-offline attempt=$attempt"
    timeout 10s adb reconnect offline
    echo "reconnect_exit=$?"
  } >"$REPORT/adb-recovery-$attempt.txt" 2>&1 || true
  sleep 5
done

run_bounded adb-devices-before 10 adb devices -l
if ! grep -E 'emulator-[0-9]+[[:space:]]+device([[:space:]]|$)' "$REPORT/adb-devices-before.txt"; then
  echo "No online emulator device found; refusing to install or launch." >&2
  exit 1
fi
run_bounded boot-completed 30 adb shell getprop sys.boot_completed
grep -Fx '1' "$REPORT/boot-completed.txt" || {
  echo "ADB is online but Android boot is not complete." >&2
  exit 1
}
printf 'Android boot completed: sys.boot_completed=1\n' | tee "$REPORT/boot-ready.txt"

run_bounded install 180 adb install -r app/build/outputs/apk/debug/app-debug.apk
grep -F "Success" "$REPORT/install.txt"
run_bounded pm-clear 30 adb shell pm clear app.shiv
grep -F "Success" "$REPORT/pm-clear.txt"
run_bounded force-stop 30 adb shell am force-stop app.shiv
run_bounded logcat-clear 20 adb logcat -c
run_bounded launch 60 adb shell am start -W -n app.shiv/.MainActivity
grep -E "Status: ok" "$REPORT/launch.txt"
sleep 5
run_bounded process 20 adb shell pidof app.shiv
test -s "$REPORT/process.txt"
if ! grep -Eq '[0-9]+' "$REPORT/process.txt"; then
  echo "Application PID was empty." >&2
  exit 1
fi
run_bounded activities 30 adb shell dumpsys activity activities
grep -F "app.shiv/.MainActivity" "$REPORT/activities.txt"
run_bounded logcat 20 adb logcat -d -t 6000
run_bounded crash-buffer 20 adb logcat -b crash -d
if grep -E "FATAL EXCEPTION|Process: app\\.shiv, PID:|ANR in app\\.shiv" "$REPORT/logcat.txt" "$REPORT/crash-buffer.txt"; then
  echo "Fatal app crash or ANR detected in fresh emulator logs." >&2
  exit 1
fi
printf 'PASS: install, clear-data, cold-launch, PID, activity, crash and ANR assertions completed.\n' | tee "$REPORT/smoke-summary.txt"
