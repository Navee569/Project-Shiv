#!/usr/bin/env bash
# Shared production predicate for fatal exceptions, app process crashes, and ANRs.
# Returns 0 when any supplied log contains a fatal marker; 1 for clean logs.
has_emulator_crash_marker() {
  grep -E 'FATAL EXCEPTION|Process: app\.shiv, PID:|ANR in app\.shiv' "$@"
}
