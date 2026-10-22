#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/emulator-device-selection.sh"

zero_devices=$'List of devices attached\n'
single_device=$'List of devices attached\nemulator-5554 device product:sdk model:test\n'
multiple_devices=$'List of devices attached\nemulator-5554 device product:sdk model:test\nemulator-5556 device product:sdk model:test\n'

if resolve_online_emulator_serial "$zero_devices" >/dev/null; then
  echo "Zero-device case unexpectedly selected an emulator." >&2
  exit 1
fi
selected="$(resolve_online_emulator_serial "$single_device")"
[[ "$selected" == "emulator-5554" ]]
if resolve_online_emulator_serial "$multiple_devices" >/dev/null; then
  echo "Multiple-device case unexpectedly selected one emulator." >&2
  exit 1
fi
echo "PASS: emulator serial selection rejects zero and multiple online devices and selects one exact serial."
