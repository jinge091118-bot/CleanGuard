#!/usr/bin/env bash
# CleanGuard R3 performance collection on a physical device.
#
# Runs the whole Macrobenchmark suite in one pass and exports the result JSON
# and instrumentation XML into a single output directory, so an interrupted
# session does not leave half a measurement behind.
#
# The signed Release build has to be uninstalled first. The benchmark package
# is signed with the debug key, and Android refuses to install it over a
# build signed with a different key. The caller is responsible for restoring
# the Release build afterwards.
#
# Usage:
#   tools/r3/collect-performance.sh <adb-serial> <output-dir> [suite ...]
#
# Suites: startup | scan | scroll | all (default: all)

set -uo pipefail

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

ADB_BIN="${ADB_BIN:-I:/Android/Sdk/platform-tools/adb.exe}"
SERIAL="${1:-}"
OUTPUT="${2:-}"
SUITES="${3:-all}"

if [ -z "$SERIAL" ] || [ -z "$OUTPUT" ]; then
  echo "usage: collect-performance.sh <adb-serial> <output-dir> [startup|scan|scroll|all]" >&2
  exit 2
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PROJECT_ROOT/.gradle-home}"
export ANDROID_SERIAL="$SERIAL"

mkdir -p "$OUTPUT"

# The device reports the model in the result path, so the JSON lands in a
# directory that carries spaces. Copy it into the output directory under a
# stable name to keep the result readable.
harvest_results() {
  local label="$1"
  local source
  source=$(find macrobenchmark/build/outputs/connected_android_test_additional_output \
    -name "com.cleanguard.app.macrobenchmark-benchmarkData.json" 2>/dev/null | head -1)
  if [ -z "$source" ]; then
    echo "no benchmark JSON found for $label" >&2
    return 1
  fi
  cp "$source" "$OUTPUT/benchmark-$label.json"
  echo "saved $OUTPUT/benchmark-$label.json"

  local xml
  xml=$(find macrobenchmark/build/outputs/androidTest-results/connected/benchmark \
    -name "*.xml" 2>/dev/null | head -1)
  if [ -n "$xml" ]; then
    cp "$xml" "$OUTPUT/results-$label.xml"
    echo "saved $OUTPUT/results-$label.xml"
  fi
  return 0
}

run_suite() {
  local label="$1"
  local class="$2"
  shift 2
  echo "=== running $label ==="
  timeout 3000 ./gradlew.bat --offline :macrobenchmark:connectedBenchmarkAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" \
    "$@" 2>&1 | tail -8
  harvest_results "$label"
}

# HyperOS blocks installing an APK over USB unless the developer option is on,
# and the block surfaces as a generic install failure that says nothing about
# the cause. Checking the notification that reports the block turns a silent
# failure into an actionable message.
report_install_gate() {
  local blocked
  blocked=$("$ADB_BIN" -s "$SERIAL" shell "dumpsys notification --noredact 2>/dev/null | grep -c '应用安装拦截'" | tr -d '\r')
  if [ "${blocked:-0}" -gt 0 ]; then
    echo ""
    echo "!!! HyperOS is blocking APK installation over USB." >&2
    echo "!!! On the phone: 设置 → 更多设置 → 开发者选项 → 开启「USB 安装」。" >&2
    echo "!!! See docs/release/v0.2/HYPEROS-INSTALL-GATE.md" >&2
    echo ""
  fi
}

preflight() {
  echo "=== preflight ==="
  local model
  model=$("$ADB_BIN" -s "$SERIAL" shell "getprop ro.product.model" | tr -d '\r')
  echo "device        : $model"

  local rows=0
  local collection
  for collection in images video audio; do
    local count
    count=$("$ADB_BIN" -s "$SERIAL" shell "content query --uri content://media/external/$collection/media --projection _id 2>/dev/null | wc -l" | tr -d '\r')
    rows=$(( rows + ${count:-0} ))
  done
  echo "media rows    : $rows"

  local battery
  battery=$("$ADB_BIN" -s "$SERIAL" shell "dumpsys battery | grep -E 'level'" | tr -d '\r')
  echo "battery       : $battery"

  local thermal
  thermal=$("$ADB_BIN" -s "$SERIAL" shell "dumpsys thermalservice | grep -m1 'Thermal Status'" | tr -d '\r')
  echo "thermal       : $thermal"

  report_install_gate
}

preflight

case "$SUITES" in
  startup)
    run_suite startup "com.cleanguard.app.macrobenchmark.StartupBenchmark" \
      -Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none
    ;;
  scan)
    run_suite scan "com.cleanguard.app.macrobenchmark.ScanBenchmark" \
      -Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none
    ;;
  scroll)
    run_suite scroll "com.cleanguard.app.macrobenchmark.HomeScrollBenchmark" \
      -Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none
    ;;
  all)
    run_suite startup "com.cleanguard.app.macrobenchmark.StartupBenchmark" \
      -Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none
    run_suite scan "com.cleanguard.app.macrobenchmark.ScanBenchmark" \
      -Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none
    run_suite scroll "com.cleanguard.app.macrobenchmark.HomeScrollBenchmark" \
      -Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none
    ;;
  *)
    echo "unknown suite: $SUITES" >&2
    exit 2
    ;;
esac

echo "=== collection finished, results in $OUTPUT ==="
ls -la "$OUTPUT"
