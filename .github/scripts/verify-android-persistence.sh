#!/usr/bin/env bash
set -uo pipefail

./gradlew :app:connectedDebugAndroidTest --no-daemon
result=$?
if [ "$result" -eq 0 ]; then
  exit 0
fi

diagnostics_dir=app/build/ci-diagnostics
mkdir -p "$diagnostics_dir"
timeout 20 adb devices -l > "$diagnostics_dir/adb-devices.txt" 2>&1 || true
timeout 20 adb shell df -h /data > "$diagnostics_dir/data-space.txt" 2>&1 || true
timeout 30 adb logcat -d -b all -v threadtime > "$diagnostics_dir/android-test-logcat.txt" 2>&1 || true
timeout 120 adb install -r app/build/outputs/apk/debug/app-debug.apk > "$diagnostics_dir/manual-install.txt" 2>&1 || true
cat "$diagnostics_dir/manual-install.txt"
exit "$result"
