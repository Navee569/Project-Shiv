#!/usr/bin/env bash
set -euo pipefail

resolve_online_emulator_serial() {
  local device_output="$1"
  local -a serials=()
  mapfile -t serials < <(
    printf '%s\n' "$device_output" |
      awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1 }'
  )
  [[ "${#serials[@]}" -eq 1 ]] || return 1
  printf '%s\n' "${serials[0]}"
}
