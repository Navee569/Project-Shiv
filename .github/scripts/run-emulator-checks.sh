#!/usr/bin/env bash
# One action script entry invokes one shell; both commands always run and their
# exit codes survive independently of the emulator action's script splitting.
set -uo pipefail
cd "${GITHUB_WORKSPACE:-$(pwd)}"
report="app/build/reports/emulator-smoke"
mkdir -p "$report"
smoke_status=0
ANDROID_EMULATOR_CORES=4 bash .github/scripts/emulator-smoke.sh || smoke_status=$?
restore_status=0
./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain 2>&1 | tee "$report/connected-tests.log" || restore_status=$?
printf 'smoke_exit=%s\nrestore_exit=%s\n' "$smoke_status" "$restore_status" | tee "$report/combined-check-status.txt"
if [ "$smoke_status" -ne 0 ] || [ "$restore_status" -ne 0 ]; then
  exit 1
fi
