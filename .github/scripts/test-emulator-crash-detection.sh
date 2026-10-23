#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/emulator-crash-detection.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

assert_detected() {
  local name="$1"
  local marker="$2"
  local file="$tmp_dir/$name.log"
  printf '%s\n' "$marker" > "$file"
  if has_emulator_crash_marker "$file" >/dev/null; then
    printf 'PASS: detected %s marker\n' "$name"
  else
    printf 'FAIL: did not detect %s marker\n' "$name" >&2
    exit 1
  fi
}

assert_detected anr 'ANR in app.shiv'
assert_detected process-crash 'Process: app.shiv, PID: 123'
assert_detected fatal-exception 'FATAL EXCEPTION'

printf '%s\n' 'Clean startup completed with no fatal errors.' > "$tmp_dir/clean.log"
if has_emulator_crash_marker "$tmp_dir/clean.log" >/dev/null; then
  echo 'FAIL: clean log incorrectly triggered crash detection.' >&2
  exit 1
fi
echo 'PASS: clean log does not trigger crash detection.'
