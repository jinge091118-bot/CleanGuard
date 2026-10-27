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
# The requested row count is the total the scanner will see, not the number of
# files this script creates. A physical handset normally already holds rows
# outside the fixture root, because the camera roll and chat archives are
# indexed as well. The script measures that baseline first and creates only
# the difference, so the total lands on the requested tier exactly.
#
# Usage:
#   tools/r3/generate-fixture.sh <adb-serial> <row-count>
#
# Example:
#   tools/r3/generate-fixture.sh be250aeb 10000
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

# MediaStore reports `relative_path` relative to the volume root, so a file at
# /sdcard/CleanGuardR3/images/x.jpg is reported as `CleanGuardR3/images/`.
# Matching against the absolute device path therefore matches nothing, which
# silently turns a filtered count into a total count. The pattern has to be
# the directory name on its own.
FIXTURE_MARKER="CleanGuardR3"

# The emulated storage layer creates one inode per file at roughly a tenth of
# a second, so writing thousands of files in a single shell loop takes many
# minutes. Splitting the work into concurrent batches overlaps that latency.
# The concurrency is bounded because each branch is a separate shell process
# on the device, and too many of them starve the storage layer instead of
# speeding it up.
BATCH_SIZE="${BATCH_SIZE:-250}"
MAX_PARALLEL="${MAX_PARALLEL:-8}"

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

# Count every row in one MediaStore collection.
count_total_rows() {
  local collection="$1"
  local rows
  rows=$(adb shell "content query --uri content://media/external/$collection/media --projection _id 2>/dev/null | wc -l" | tr -d '\r' || true)
  echo "${rows:-0}"
}

# Count the rows that belong to the fixture root.
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

# Count the rows the device already carries outside the fixture root. The
# baseline is the total minus the fixture rows rather than a reversed grep,
# because a reversed pattern match and a forward one do not always partition
# the output exactly, and an off-by-one here misplaces the tier.
count_non_fixture_rows() {
  local total=0
  local collection
  for collection in images video audio; do
    total=$(( total + $(count_total_rows "$collection") ))
  done
  echo $(( total - $(count_fixture_rows) ))
}

# Remove the previous fixture. The files are deleted together with their
# directory, and the index is refreshed so the rows that pointed at them
# disappear as well.
adb shell "rm -rf $FIXTURE_ROOT && mkdir -p $FIXTURE_ROOT/images $FIXTURE_ROOT/videos $FIXTURE_ROOT/audio"
adb shell "content call --uri content://media/external --method scan_volume --arg external_primary"
sleep 10

BASELINE=$(count_non_fixture_rows)
FIXTURE_ROWS=$(( TOTAL_ROWS - BASELINE ))

if [ "$FIXTURE_ROWS" -lt 1 ]; then
  echo "the device already holds $BASELINE rows, which meets or exceeds the requested tier of $TOTAL_ROWS" >&2
  echo "remove the existing fixture first, or request a higher tier" >&2
  exit 2
fi

# Split the row budget across images, videos, and audio so the scanner
# exercises every MediaStore collection the app reads.
IMAGE_ROWS=$(( FIXTURE_ROWS * 70 / 100 ))
VIDEO_ROWS=$(( FIXTURE_ROWS * 20 / 100 ))
AUDIO_ROWS=$(( FIXTURE_ROWS - IMAGE_ROWS - VIDEO_ROWS ))

echo "fixture root  : $FIXTURE_ROOT"
echo "requested tier: $TOTAL_ROWS total rows"
echo "pre-existing  : $BASELINE rows already on the device"
echo "fixture rows  : images=$IMAGE_ROWS videos=$VIDEO_ROWS audio=$AUDIO_ROWS total=$FIXTURE_ROWS"

# Write one batch of files. Each batch is a single device-side shell command
# so there is no per-file transport cost, and several batches run at once.
# The parallel launch happens inside one device-side command, because starting
# the branches from the host costs one transport round trip per batch.
write_collection() {
  local dir="$1"
  local ext="$2"
  local count="$3"

  if [ "$count" -le 0 ]; then
    return 0
  fi

  local ranges=""
  local start=1
  local batch=0
  while [ "$start" -le "$count" ]; do
    local end=$(( start + BATCH_SIZE - 1 ))
    if [ "$end" -gt "$count" ]; then
      end=$count
    fi
    ranges="$ranges $start:$end"
    start=$(( end + 1 ))
    batch=$(( batch + 1 ))
  done

  # The ranges are handed to the device in groups, so at most MAX_PARALLEL
  # branches run at once.
  local group=""
  local index=0
  for range in $ranges; do
    group="$group $range"
    index=$(( index + 1 ))
    if [ "$index" -ge "$MAX_PARALLEL" ]; then
      adb shell "cd $dir && for r in$group; do s=\${r%%:*}; e=\${r##*:}; (for i in \$(seq \$s \$e); do printf '$FIXTURE_MARKER%08d' \$i > r3-\$i.$ext; done) & done; wait"
      group=""
      index=0
    fi
  done
  if [ -n "$group" ]; then
    adb shell "cd $dir && for r in$group; do s=\${r%%:*}; e=\${r##*:}; (for i in \$(seq \$s \$e); do printf '$FIXTURE_MARKER%08d' \$i > r3-\$i.$ext; done) & done; wait"
  fi
}

write_collection "$FIXTURE_ROOT/images" "jpg" "$IMAGE_ROWS"
write_collection "$FIXTURE_ROOT/videos" "mp4" "$VIDEO_ROWS"
write_collection "$FIXTURE_ROOT/audio"  "mp3" "$AUDIO_ROWS"

echo "files written; triggering MediaStore index"
adb shell "content call --uri content://media/external --method scan_volume --arg external_primary"

# The indexer runs asynchronously. Poll until the fixture rows show up or the
# budget expires, so a partial index is never accepted. The fixture rows are
# counted by path so the pre-existing rows on the device cannot satisfy the
# condition on their own.
DEADLINE=$(( $(date +%s) + 1800 ))
ACTUAL=0

while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  ACTUAL=$(count_fixture_rows)
  if [ "$ACTUAL" -ge "$FIXTURE_ROWS" ]; then
    break
  fi
  sleep 10
done

echo "fixture rows indexed: $ACTUAL (expected $FIXTURE_ROWS)"
echo "total rows on device: $(( ACTUAL + BASELINE )) (requested tier $TOTAL_ROWS)"
if [ "$ACTUAL" -lt "$FIXTURE_ROWS" ]; then
  echo "fixture indexing did not reach the expected row count" >&2
  exit 1
fi
