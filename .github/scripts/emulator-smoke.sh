#!/usr/bin/env bash
set -eu

source "$(dirname "$0")/emulator-device-selection.sh"

ROOT="${GITHUB_WORKSPACE:-$(pwd)}"
cd "$ROOT"
REPORT="app/build/reports/emulator-smoke"
mkdir -p "$REPORT"
printf 'Smoke script started at %s\n' "$(date -u +%FT%TZ)" | tee "$REPORT/script-started.txt"
printf 'Working directory: %s\n' "$PWD" | tee -a "$REPORT/script-started.txt"

run_bounded() {
  local label="$1"
  local seconds="$2"
  shift 2
  local output="$REPORT/${label}.txt"
  if [ "$label" = "install" ]; then
    run_monitored_install "$seconds" "$@"
    return $?
  fi
  printf 'Running %s (timeout %ss):' "$label" "$seconds" | tee "$REPORT/${label}-command.txt"
  printf ' %q' "$@" | tee -a "$REPORT/${label}-command.txt"
  printf '\n' | tee -a "$REPORT/${label}-command.txt"
  if timeout "${seconds}s" "$@" >"$output" 2>&1; then
    printf '%s exit=0\n' "$label" | tee "$REPORT/${label}-status.txt"
    cat "$output"
  else
    status=$?
    if [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then
      printf '%s exit=%s timed_out_after=%ss\n' "$label" "$status" "$seconds" | tee "$REPORT/${label}-status.txt" >&2
    else
      printf '%s exit=%s (time_limit=%ss; command failed, not classified as timeout)\n' "$label" "$status" "$seconds" | tee "$REPORT/${label}-status.txt" >&2
    fi
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
  } >"$out" 2>&1
  echo "Captured $out"
}

# Capture bounded snapshots while package installation is active. Postmortem
# collection after emulator teardown cannot explain a live install stall.
snapshot_command() {
  local label="$1"; local seconds="$2"; shift 2
  local base="$REPORT/install-live-${snapshot_index}-${label}"
  local status
  printf '%s\n' "$*" > "${base}-command.txt"
  if timeout "${seconds}s" "$@" > "${base}.txt" 2>&1; then status=0; else status=$?; fi
  printf 'timestamp=%s exit=%s timeout=%ss\n' "$(timestamp)" "$status" "$seconds" > "${base}-status.txt"
}

capture_install_snapshot() {
  local snapshot_index="$1"
  printf 'Install live snapshot %s at %s\n' "$snapshot_index" "$(timestamp)" | tee "$REPORT/install-live-${snapshot_index}-timestamp.txt"
  snapshot_command adb-version 5 adb version
  snapshot_command adb-server-status 5 adb server-status
  snapshot_command adb-devices 5 adb devices -l
  snapshot_command adb-selected-state 5 adb -s "$DEVICE_SERIAL" get-state
  snapshot_command boot-properties 8 adb -s "$DEVICE_SERIAL" shell sh -c 'getprop sys.boot_completed; getprop init.svc.bootanim; getprop dev.bootcomplete'
  snapshot_command package-installer 12 adb -s "$DEVICE_SERIAL" shell dumpsys package installer
  snapshot_command guest-logcat 12 adb -s "$DEVICE_SERIAL" logcat -d -v threadtime -t 1200
  snapshot_command adb-server-processes 5 sh -c "ps -eo pid,ppid,stat,etime,%cpu,%mem,rss,args | grep -E '[a]db|[e]mulator|[q]emu'"
  snapshot_command adb-port-5037 5 sh -c 'ss -ltnp | grep ":5037"'
  snapshot_command host-memory 5 free -h
  snapshot_command host-disk 5 df -h /
  snapshot_command apk-size 5 stat --format="APK bytes: %s" app/build/outputs/apk/debug/app-debug.apk
  snapshot_command apk-sha256 8 sha256sum app/build/outputs/apk/debug/app-debug.apk
  snapshot_command guest-all-logcat 15 adb -s "$DEVICE_SERIAL" logcat -b all -d -v threadtime -t 2000
}

run_monitored_install() {
  local seconds="$1"; shift
  local install_log="$REPORT/install.txt"
  local install_pid
  local snapshot_index=0
  local process_state
  local status
  printf 'Running install (timeout %ss):' "$seconds" | tee "$REPORT/install-command.txt"
  printf ' %q' "$@" | tee -a "$REPORT/install-command.txt"
  printf '\n' | tee -a "$REPORT/install-command.txt"
  timeout "${seconds}s" "$@" >"$install_log" 2>&1 &
  install_pid=$!
  while :; do
    process_state=""
    if process_state="$(ps -o stat= -p "$install_pid" 2>/dev/null)"; then :; fi
    case "$process_state" in ""|Z*) break ;; esac
    sleep 20
    process_state=""
    if process_state="$(ps -o stat= -p "$install_pid" 2>/dev/null)"; then :; fi
    case "$process_state" in ""|Z*) break ;; esac
    snapshot_index=$((snapshot_index + 1))
    capture_install_snapshot "$snapshot_index"
  done
  if wait "$install_pid"; then
    printf 'install exit=0\n' | tee "$REPORT/install-status.txt"
    cat "$install_log"
  else
    status=$?
    printf 'install exit=%s timeout=%ss\n' "$status" "$seconds" | tee "$REPORT/install-status.txt" >&2
    cat "$install_log" >&2
    capture_install_snapshot failure
    return "$status"
  fi
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
DEVICE_SERIAL="$(resolve_online_emulator_serial "$(cat "$REPORT/adb-devices-before.txt")")" || {
  echo "Expected exactly one online emulator; refusing to continue." >&2
  cat "$REPORT/adb-devices-before.txt" >&2
  exit 1
}
printf 'Selected emulator serial: %s\n' "$DEVICE_SERIAL" | tee "$REPORT/selected-device.txt"
run_bounded boot-completed 30 adb -s "$DEVICE_SERIAL" shell getprop sys.boot_completed
grep -Fx '1' "$REPORT/boot-completed.txt" || {
  echo "ADB is online but Android boot is not complete." >&2
  exit 1
}
printf 'Android boot completed: sys.boot_completed=1\n' | tee "$REPORT/boot-ready.txt"

# Retain direct AVD configuration and guest-visible CPU evidence. The action's
# requested cores and config write are not proof of the effective guest count.
cpu_report="$REPORT/effective-cpu-evidence.txt"
{
  echo "timestamp=$(date -u +%FT%TZ)"
  echo "serial=$DEVICE_SERIAL"
  echo "requested_cores=${ANDROID_EMULATOR_CORES:-unknown}"
  echo "AVD config files:"
  avd_root="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
  found_config=0
  declare -A seen_configs=()
  for avd_config in "$avd_root/test.avd/config.ini" "$HOME/.android/avd/test.avd/config.ini"; do
    [ -f "$avd_config" ] || continue
    canonical_config="$(readlink -f "$avd_config" 2>/dev/null || printf '%s' "$avd_config")"
    [ -z "${seen_configs[$canonical_config]+x}" ] || continue
    seen_configs["$canonical_config"]=1
    found_config=1
    echo "--- $canonical_config"
    cat "$canonical_config"
  done
  echo "avd_config_found=$found_config"
  echo "Emulator/QEMU process command lines:"
  ps -eo pid,ppid,args | grep -E '[e]mulator|[q]emu' || true
  echo "Guest online CPU mask:"
  timeout 10s adb -s "$DEVICE_SERIAL" shell cat /sys/devices/system/cpu/online 2>&1 || true
  echo "Guest online CPU count:"
  timeout 10s adb -s "$DEVICE_SERIAL" shell getconf _NPROCESSORS_ONLN 2>&1 || true
  echo "Guest /proc/cpuinfo processor records:"
  timeout 10s adb -s "$DEVICE_SERIAL" shell cat /proc/cpuinfo 2>&1 || true
  echo "Guest CPU-related properties:"
  timeout 10s adb -s "$DEVICE_SERIAL" shell getprop 2>&1 | grep -Ei 'cpu|smp|abi' || true
} > "$cpu_report" 2>&1
cat "$cpu_report"
run_bounded cpu-online-mask 10 adb -s "$DEVICE_SERIAL" shell cat /sys/devices/system/cpu/online
run_bounded cpu-online-count 10 adb -s "$DEVICE_SERIAL" shell getconf _NPROCESSORS_ONLN
run_bounded cpu-processors 10 adb -s "$DEVICE_SERIAL" shell cat /proc/cpuinfo
python3 - "$REPORT" <<'PY'
import pathlib, re, sys
report = pathlib.Path(sys.argv[1])
evidence = (report / "effective-cpu-evidence.txt").read_text()
configs = re.findall(r"^--- (.+)$", evidence, re.M)
assert len(configs) == 1, f"Expected one canonical AVD config, got {configs}"
assert re.search(r"^requested_cores=4$", evidence, re.M)
assert re.search(r"^hw.cpu.ncore=4\s*$", evidence, re.M)
assert (report / "cpu-online-mask.txt").read_text().strip() == "0-3"
assert (report / "cpu-online-count.txt").read_text().strip() == "4"
processors = re.findall(r"^processor\s*:\s*(\d+)\s*$", (report / "cpu-processors.txt").read_text(), re.M)
assert processors == ["0", "1", "2", "3"], processors
with (report / "effective-cpu-evidence.txt").open("a") as out:
    out.write("\nCPU_ASSERTIONS=PASS requested=4 configured=4 mask=0-3 online=4 processors=0,1,2,3\n")
PY

capture_launch_diagnostics() {
  local phase="$1"
  local prefix="$REPORT/launch-diagnostics-$phase"
  {
    echo "timestamp=$(timestamp)"
    echo "serial=$DEVICE_SERIAL"
    echo "launch output:"
    cat "$REPORT/launch.txt" 2>/dev/null || true
    echo "app PID:"
    timeout 12s adb -s "$DEVICE_SERIAL" shell pidof app.shiv 2>&1 || true
    echo "process list:"
    timeout 12s adb -s "$DEVICE_SERIAL" shell ps -A 2>&1 || true
    echo "activity state:"
    timeout 15s adb -s "$DEVICE_SERIAL" shell dumpsys activity activities 2>&1 || true
    echo "top activity:"
    timeout 15s adb -s "$DEVICE_SERIAL" shell dumpsys activity top 2>&1 || true
    echo "window state:"
    timeout 12s adb -s "$DEVICE_SERIAL" shell dumpsys window windows 2>&1 || true
    echo "app memory:"
    timeout 12s adb -s "$DEVICE_SERIAL" shell dumpsys meminfo app.shiv 2>&1 || true
  } >"$prefix-state.txt" 2>&1
  timeout 15s adb -s "$DEVICE_SERIAL" logcat -b all -d -v threadtime -t 6000 >"$prefix-logcat.txt" 2>&1 || true
  timeout 12s adb -s "$DEVICE_SERIAL" logcat -b crash -d -v threadtime >"$prefix-crash.txt" 2>&1 || true
  echo "Captured live launch diagnostics: $prefix-*"
}

run_bounded install 180 adb -s "$DEVICE_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
grep -F "Success" "$REPORT/install.txt"
run_bounded pm-clear 30 adb -s "$DEVICE_SERIAL" shell pm clear app.shiv
grep -F "Success" "$REPORT/pm-clear.txt"
run_bounded force-stop 30 adb -s "$DEVICE_SERIAL" shell am force-stop app.shiv
run_bounded logcat-clear 20 adb -s "$DEVICE_SERIAL" logcat -c

# Preserve both the command's exit status and the exact ActivityManager result.
# A PID or visible activity never overrides Status: timeout.
launch_command_status=0
if run_bounded launch 60 adb -s "$DEVICE_SERIAL" shell am start -W -n app.shiv/.MainActivity; then
  launch_command_status=0
else
  launch_command_status=$?
fi
if [ "$launch_command_status" -ne 0 ] || ! grep -Fxq 'Status: ok' "$REPORT/launch.txt"; then
  capture_launch_diagnostics failure
  echo "Cold launch failed: am start must exit zero and report exactly 'Status: ok'. Status: timeout is a failure." >&2
  exit 1
fi

sleep 5
run_bounded process 20 adb -s "$DEVICE_SERIAL" shell pidof app.shiv
test -s "$REPORT/process.txt"
grep -Eq '[0-9]+' "$REPORT/process.txt" || {
  echo "Application PID was empty." >&2
  exit 1
}
# Capture the crash/ANR evidence before the larger dumpsys snapshots, which
# can add load to a marginal hosted emulator. Collection remains mandatory:
# any failed ADB log capture fails the smoke and is preserved in its status file.
run_bounded logcat 20 adb -s "$DEVICE_SERIAL" logcat -b all -d -v threadtime -t 6000
run_bounded crash-buffer 20 adb -s "$DEVICE_SERIAL" logcat -b crash -d -v threadtime
run_bounded activities 30 adb -s "$DEVICE_SERIAL" shell dumpsys activity activities
grep -F "app.shiv/.MainActivity" "$REPORT/activities.txt"
run_bounded top-activity 30 adb -s "$DEVICE_SERIAL" shell dumpsys activity top
grep -F "app.shiv/.MainActivity" "$REPORT/top-activity.txt"
source ".github/scripts/emulator-crash-detection.sh"
if has_emulator_crash_marker "$REPORT/logcat.txt" "$REPORT/crash-buffer.txt"; then
  echo "Fatal app crash or ANR detected in fresh emulator logs." >&2
  exit 1
fi
printf 'PASS: install, clear-data, strict cold-launch, PID, activity, crash and ANR assertions completed.\n' | tee "$REPORT/smoke-summary.txt"
