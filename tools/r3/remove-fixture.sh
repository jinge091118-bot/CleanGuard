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

# MediaStore reports `relative_path` relative to the volume root, so the
# fixture directory appears as `CleanGuardR3/` and not as `/sdcard/CleanGuardR3`.
# Matching against the absolute device path matches nothing, which would report
# the fixture as never removed.
FIXTURE_MARKER="CleanGuardR3"

SERIAL="${1:-}"
if [ -z "$SERIAL" ]; then
  echo "usage: remove-fixture.sh <adb-serial>" >&2
  exit 2
fi

adb() { "$ADB_BIN" -s "$SERIAL" "$@"; }

count_fixture_rows() {
  local total=0
  local collection
  for collection in images video audio; do
    local rows
    rows=$(adb shell "content query --uri content://media/external/$collection/media --projection relative_path 2>/dev/null | grep -c $FIXTURE_MARKER" | tr -d '\r' || true)
    total=$(( total + ${rows:-0} ))
  done
  echo "$total"
}

echo "removing $FIXTURE_ROOT"
adb shell "rm -rf $FIXTURE_ROOT"

# Deleting the files is not enough on its own. MediaStore keeps the index row
# for a file that no longer exists until a scan pass notices the file is
# gone, so the rows have to be re-indexed before the collection count returns
# to the pre-fixture value.
adb shell "content call --uri content://media/external --method scan_volume --arg external_primary"

DEADLINE=$(( $(date +%s) + 600 ))
REMAINING=0
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  REMAINING=$(count_fixture_rows)
  if [ "$REMAINING" -eq 0 ]; then
    break
  fi
  sleep 10
done

echo "fixture rows still indexed: $REMAINING"
if [ "$REMAINING" -ne 0 ]; then
  echo "stale fixture rows remain; a re-index did not clear them" >&2
  exit 1
fi
echo "fixture removed"
