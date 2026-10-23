#!/usr/bin/env bash
# CleanGuard R3 synthetic fixture cleanup.
#
# Removes ONLY the dedicated R3 fixture root and re-indexes MediaStore so the
# synthetic rows disappear from the device. No other path is touched.
#
# Usage:
#   tools/r3/remove-fixture.sh <adb-serial>

set -euo pipefail

# Git Bash rewrites arguments that look like absolute POSIX paths. Device-side
# paths such as /sdcard/... must reach adb unchanged.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

ADB_BIN="${ADB_BIN:-I:/Android/Sdk/platform-tools/adb.exe}"
FIXTURE_ROOT="/sdcard/CleanGuardR3"

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
  echo "usage: remove-fixture.sh <adb-serial>" >&2
  exit 2
fi

adb() { "$ADB_BIN" -s "$SERIAL" "$@"; }

echo "removing $FIXTURE_ROOT"
adb shell "rm -rf $FIXTURE_ROOT"
adb shell "content call --uri content://media/external --method scan_volume --arg external_primary"

sleep 10
REMAINING=$(adb shell "content query --uri content://media/external/images/media --projection _id 2>/dev/null | wc -l" | tr -d '\r')
echo "remaining image rows on device: $REMAINING"
