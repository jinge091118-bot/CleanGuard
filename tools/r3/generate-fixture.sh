#!/usr/bin/env bash
# CleanGuard R3 synthetic fixture generator.
#
# Creates non-private synthetic media rows for R3 physical-device performance
# validation, then triggers a MediaStore index pass so the rows become visible
# to the CleanGuard scanner.
#
# Only files inside the dedicated fixture root are created. Nothing outside
# that directory is read, written, or deleted.
#
# Usage:
#   tools/r3/generate-fixture.sh <adb-serial> <row-count>
#
# Example:
#   tools/r3/generate-fixture.sh 2023081000029 10000
#
# Cleanup:
#   tools/r3/remove-fixture.sh <adb-serial>

set -euo pipefail

# Git Bash rewrites arguments that look like absolute POSIX paths. Device-side
# paths such as /sdcard/... must reach adb unchanged.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

ADB_BIN="${ADB_BIN:-I:/Android/Sdk/platform-tools/adb.exe}"
FIXTURE_ROOT="/sdcard/CleanGuardR3"

SERIAL="${1:-}"
TOTAL_ROWS="${2:-10000}"

if [ -z "$SERIAL" ]; then
  echo "usage: generate-fixture.sh <adb-serial> <row-count>" >&2
  exit 2
fi

if ! [ "$TOTAL_ROWS" -ge 1 ] 2>/dev/null; then
  echo "row-count must be a positive integer" >&2
  exit 2
fi

adb() { "$ADB_BIN" -s "$SERIAL" "$@"; }

# Split the row budget across images, videos, and audio so the scanner
# exercises every MediaStore collection the app reads.
IMAGE_ROWS=$(( TOTAL_ROWS * 70 / 100 ))
VIDEO_ROWS=$(( TOTAL_ROWS * 20 / 100 ))
AUDIO_ROWS=$(( TOTAL_ROWS - IMAGE_ROWS - VIDEO_ROWS ))

echo "fixture root : $FIXTURE_ROOT"
echo "rows         : images=$IMAGE_ROWS videos=$VIDEO_ROWS audio=$AUDIO_ROWS total=$TOTAL_ROWS"

adb shell "rm -rf $FIXTURE_ROOT && mkdir -p $FIXTURE_ROOT/images $FIXTURE_ROOT/videos $FIXTURE_ROOT/audio"

write_rows() {
  local dir="$1"
  local count="$2"
  local ext="$3"
  adb shell "cd $dir && for i in \$(seq 1 $count); do printf 'CleanGuardR3Fixture%08d' \$i > r3-\$i.$ext; done"
}

write_rows "$FIXTURE_ROOT/images" "$IMAGE_ROWS" "jpg"
write_rows "$FIXTURE_ROOT/videos" "$VIDEO_ROWS" "mp4"
write_rows "$FIXTURE_ROOT/audio"  "$AUDIO_ROWS"  "mp3"

echo "files written; triggering MediaStore index"
adb shell "content call --uri content://media/external --method scan_volume --arg external_primary"

# The indexer runs asynchronously. Poll until the expected row count shows up
# or the budget expires, so a partial index is never accepted as a fixture.
# The three MediaStore collections are counted separately and summed, because
# the fixture is split across images, videos, and audio.
EXPECTED=$TOTAL_ROWS
DEADLINE=$(( $(date +%s) + 900 ))
ACTUAL=0

count_rows() {
  local total=0
  local collection
  for collection in images video audio; do
    local rows
    rows=$(adb shell "content query --uri content://media/external/$collection/media --projection _id 2>/dev/null | wc -l" | tr -d '\r')
    total=$(( total + rows ))
  done
  echo "$total"
}

while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  ACTUAL=$(count_rows)
  if [ "$ACTUAL" -ge "$EXPECTED" ]; then
    break
  fi
  sleep 10
done

echo "media rows indexed: $ACTUAL (expected >= $EXPECTED)"
if [ "$ACTUAL" -lt "$EXPECTED" ]; then
  echo "fixture indexing did not reach the expected row count" >&2
  exit 1
fi
