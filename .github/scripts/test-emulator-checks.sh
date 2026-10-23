#!/usr/bin/env bash
set -euo pipefail
wrapper="$(cd "$(dirname "$0")" && pwd)/run-emulator-checks.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/.github/scripts"
printf '#!/usr/bin/env bash\necho smoke-ran >> calls.txt\nexit "$SMOKE_EXIT"\n' > "$fixture/.github/scripts/emulator-smoke.sh"
printf '#!/usr/bin/env bash\necho restore-ran >> calls.txt\nexit "$RESTORE_EXIT"\n' > "$fixture/gradlew"
chmod +x "$fixture/gradlew"
for smoke in 0 7; do
  for restore in 0 9; do
    rm -f "$fixture/calls.txt"
    status=0
    GITHUB_WORKSPACE="$fixture" SMOKE_EXIT="$smoke" RESTORE_EXIT="$restore" bash "$wrapper" || status=$?
    if [ "$smoke" -eq 0 ] && [ "$restore" -eq 0 ]; then
      test "$status" -eq 0
    else
      test "$status" -eq 1
    fi
    test "$(cat "$fixture/calls.txt")" = $'smoke-ran\nrestore-ran'
    grep -Fx "smoke_exit=$smoke" "$fixture/app/build/reports/emulator-smoke/combined-check-status.txt"
    grep -Fx "restore_exit=$restore" "$fixture/app/build/reports/emulator-smoke/combined-check-status.txt"
  done
done
echo 'PASS: both checks run for every success/failure combination and preserve exit codes.'
