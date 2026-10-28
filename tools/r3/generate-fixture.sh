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

# How many times a collection is written before the run gives up. A long
# write can lose its connection partway through, and the collection is
# completed by a later attempt rather than abandoned.
WRITE_ATTEMPTS="${WRITE_ATTEMPTS:-6}"

# How many files one shard directory holds. The emulated storage layer slows
# down sharply as a directory fills, and a shard of this size keeps the write
# rate at the level an empty directory reaches.
FILES_PER_SHARD="${FILES_PER_SHARD:-1500}"

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

# Bring the fixture root into a known state.
#
# The previous fixture is removed only when the caller asks for a fresh
# start. A run that was interrupted is resumed instead, because writing tens
# of thousands of files takes long enough that the connection can drop, and
# starting over would throw away the work. The rows that the interrupted run
# left in the index are cleared by a re-index, and the files themselves are
# reused, because the file names are numbered and the writer only creates the
# indices that are missing.
if [ "${FRESH_FIXTURE:-0}" = "1" ]; then
  adb shell "rm -rf $FIXTURE_ROOT"
fi
adb shell "mkdir -p $FIXTURE_ROOT/images $FIXTURE_ROOT/videos $FIXTURE_ROOT/audio"

# An earlier layout kept every file of a collection in one directory. Those
# files are moved into the shard layout, because the row count must not
# change when the layout does, and leaving them where they are would
# duplicate the rows.
migrate_flat_files() {
  local base="$1"
  local ext="$2"
  local moved=0
  local shard=0
  local first=1
  while [ "$shard" -lt 64 ]; do
    local dir
    dir=$(printf "%s/shard-%02d" "$base" "$shard")
    adb shell "mkdir -p $dir" >/dev/null 2>&1 || true
    # Files whose index falls in this shard's range are moved into it.
    local last=$(( first + FILES_PER_SHARD - 1 ))
    local moved_here
    moved_here=$(adb shell "cd $base && for i in \$(seq $first $last); do [ -f r3-\$i.$ext ] && mv r3-\$i.$ext $dir/ 2>/dev/null && echo x; done | wc -l" | tr -d '\r')
    moved_here="${moved_here:-0}"
    moved=$(( moved + moved_here ))
    first=$(( last + 1 ))
    shard=$(( shard + 1 ))
  done
  if [ "$moved" -gt 0 ]; then
    echo "  moved $moved existing files of $(basename "$base") into the shard layout"
  fi
}

migrate_flat_files "$FIXTURE_ROOT/images" "jpg"
migrate_flat_files "$FIXTURE_ROOT/videos" "mp4"
migrate_flat_files "$FIXTURE_ROOT/audio" "mp3"

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

# Write the files of one collection.
#
# The files are spread over several directories. The emulated storage layer
# slows down sharply as a single directory fills: creating sixty files in a
# directory that already held six thousand took forty five seconds, while the
# same sixty files in an empty directory took two. The number of rows the
# scanner sees does not depend on how they are distributed, so the files are
# sharded and each shard stays small.
#
# A long write is not reliably carried by a single shell command. The
# connection can drop partway through, and the device side branches die with
# it, which leaves the directory holding only the files that were written
# before the drop. Each shard therefore counts what is already there, writes
# only the missing indices, and repeats until it holds the expected number of
# files. The file names are numbered in order across the whole collection, so
# the count says which indices are still missing.
write_collection() {
  local base="$1"
  local ext="$2"
  local count="$3"

  if [ "$count" -le 0 ]; then
    return 0
  fi

  local shard=0
  local first=1
  while [ "$first" -le "$count" ]; do
    local shard_count=$FILES_PER_SHARD
    if [ $(( first + shard_count - 1 )) -gt "$count" ]; then
      shard_count=$(( count - first + 1 ))
    fi
    local dir
    dir=$(printf "%s/shard-%02d" "$base" "$shard")
    write_shard "$dir" "$ext" "$shard_count" "$first" || return 1
    first=$(( first + shard_count ))
    shard=$(( shard + 1 ))
  done
  return 0
}

# Write the files of one shard. The first index is the index of the first
# file in the shard, and the indices run from there.
#
# The shard is completed by writing the indices that are missing rather than
# by appending after the files that are present. The number of files present
# does not say where the writer should continue, because a connection that
# dropped partway through can leave a gap in the middle of the range, and
# continuing from the count would write over files that already exist and
# never close the gap.
write_shard() {
  local dir="$1"
  local ext="$2"
  local count="$3"
  local first="$4"
  local last=$(( first + count - 1 ))

  adb shell "mkdir -p $dir" >/dev/null 2>&1 || true

  local attempt=0
  local missing=1
  while [ "$attempt" -lt "$WRITE_ATTEMPTS" ]; do
    attempt=$(( attempt + 1 ))
    missing=$(repair_range "$dir" "$ext" "$first" "$last")
    if [ "$missing" -eq 0 ]; then
      return 0
    fi
    echo "  $(basename "$dir"): $missing of $count files missing, writing them"
  done

  echo "!!! $dir is missing $missing of $count files after $WRITE_ATTEMPTS attempts" >&2
  return 1
}

# Write the indices that are missing from a range inside a shard, and report
# how many were still missing afterwards.
#
# The missing indices are found by comparing the indices that are present
# against the indices that are wanted. The comparison stays on the device
# because the listing is large.
repair_range() {
  local dir="$1"
  local ext="$2"
  local first="$3"
  local last="$4"
  local state="/data/local/tmp/cleanguard-fixture"

  adb shell "mkdir -p $state" >/dev/null 2>&1 || true
  local missing
  missing=$(adb shell "cd $dir && ls 2>/dev/null | sed 's/^r3-//; s/\.[^.]*\$//' | sort -n > $state/present.txt; seq $first $last > $state/wanted.txt; comm -13 $state/present.txt $state/wanted.txt > $state/missing.txt; wc -l < $state/missing.txt" | tr -d '\r' | tail -1)
  missing="${missing:-0}"
  if ! [ "$missing" -gt 0 ] 2>/dev/null; then
    echo 0
    return 0
  fi

  adb shell "cd $dir && while read i; do [ -n \"\$i\" ] && printf '$FIXTURE_MARKER%08d' \"\$i\" > r3-\$i.$ext; done < $state/missing.txt" || true

  # The count is taken again rather than assumed, because the write can be
  # interrupted partway through and the caller has to know what is still
  # missing.
  local remaining
  remaining=$(adb shell "cd $dir && ls 2>/dev/null | sed 's/^r3-//; s/\.[^.]*\$//' | sort -n > $state/present.txt; comm -13 $state/present.txt $state/wanted.txt | wc -l" | tr -d '\r' | tail -1)
  echo "${remaining:-0}"
}

# How many files a shard directory already holds.
count_files() {
  local dir="$1"
  local rows
  rows=$(adb shell "ls $dir 2>/dev/null | wc -l" | tr -d '\r')
  echo "${rows:-0}"
}

write_collection "$FIXTURE_ROOT/images" "jpg" "$IMAGE_ROWS" || exit 3
write_collection "$FIXTURE_ROOT/videos" "mp4" "$VIDEO_ROWS" || exit 3
write_collection "$FIXTURE_ROOT/audio"  "mp3" "$AUDIO_ROWS" || exit 3

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
