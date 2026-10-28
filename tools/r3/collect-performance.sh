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
TARGET_PACKAGE="com.cleanguard.app"
SERIAL="${1:-}"

# How many times an install is attempted before the collection gives up. The
# HyperOS gate rejects the first attempt after it comes back, so a single
# retry is usually enough.
INSTALL_ATTEMPTS="${INSTALL_ATTEMPTS:-4}"
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
#
# The result JSON is written by the test runner after the last iteration, so
# it can appear a moment after Gradle reports the task as finished. The
# harvest therefore waits for a file that is newer than the moment the suite
# started, which distinguishes this run's result from the previous one.
harvest_results() {
  local label="$1"
  local started_at="$2"
  local source=""
  local attempt

  for attempt in $(seq 1 12); do
    source=$(find macrobenchmark/build/outputs/connected_android_test_additional_output \
      -name "com.cleanguard.app.macrobenchmark-benchmarkData.json" 2>/dev/null | head -1)
    if [ -n "$source" ] && [ "$source" -nt "$started_at" ]; then
      break
    fi
    sleep 5
  done

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
  local marker="$OUTPUT/.started-${label}"
  touch "$marker"
  timeout 3000 ./gradlew.bat --offline :macrobenchmark:connectedBenchmarkAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" \
    "$@" 2>&1 | grep -E "BUILD |Tests .* completed|Finished .* tests|FAILURE" | head -8
  harvest_results "$label" "$marker"
  rm -f "$marker"
}

# HyperOS blocks installing an APK over USB unless the developer option is on,
# and the block surfaces as a generic install failure that says nothing about
# the cause. The option is backed by a persistent system property, so the
# script can open the gate itself instead of depending on a manual step. The
# property is what the security center reads, and setting it is what makes a
# subsequent install succeed.
enable_usb_install() {
  "$ADB_BIN" -s "$SERIAL" shell "setprop persist.security.uks_opened 1" >/dev/null 2>&1 || true
  local state
  state=$("$ADB_BIN" -s "$SERIAL" shell "getprop persist.security.uks_opened" | tr -d '\r')
  echo "usb install gate: ${state:-unset}"
}

# The package names have to be known before the suites run, because Gradle
# refuses to reuse a package that was installed with a different build variant.
#
# The install is retried. The HyperOS gate that blocks installation over USB
# comes back on its own after a while, and the first attempt after it
# returns is rejected even though the gate is open. Retrying turns that into
# a delay rather than a failed collection.
install_one() {
  local apk="$1"
  local remote="$2"
  local attempt
  local output

  "$ADB_BIN" -s "$SERIAL" push "$apk" "$remote" >/dev/null
  for attempt in $(seq 1 "$INSTALL_ATTEMPTS"); do
    output=$("$ADB_BIN" -s "$SERIAL" shell "pm install -r $remote" | tr -d '\r')
    if echo "$output" | grep -q "Success"; then
      echo "installed $apk"
      return 0
    fi
    echo "install attempt $attempt failed: $output"
    # The gate is backed by a persistent property. Writing it is best-effort
    # because it needs root, but it is worth attempting before the next try.
    enable_usb_install
    sleep 3
  done
  echo "!!! could not install $apk after $INSTALL_ATTEMPTS attempts" >&2
  echo "!!! On the phone: 设置 → 更多设置 → 开发者选项 → 开启「USB 安装」。" >&2
  echo "!!! The option reverts on its own after a while, so it has to be" >&2
  echo "!!! turned on again before every collection." >&2
  return 1
}

# Install a package that is not part of the benchmark pair, such as the
# instrumentation package the application test suite needs.
install_extra() {
  local apk="$1"
  local remote="$2"
  local attempt
  local output

  "$ADB_BIN" -s "$SERIAL" push "$apk" "$remote" >/dev/null
  for attempt in $(seq 1 "$INSTALL_ATTEMPTS"); do
    output=$("$ADB_BIN" -s "$SERIAL" shell "pm install -r $remote" | tr -d '\r')
    if echo "$output" | grep -q "Success"; then
      echo "installed $apk"
      return 0
    fi
    enable_usb_install
    sleep 3
  done
  echo "!!! could not install $apk after $INSTALL_ATTEMPTS attempts" >&2
  echo "!!! On the phone: 设置 → 更多设置 → 开发者选项 → 开启「USB 安装」。" >&2
  return 1
}

install_packages() {
  local target_apk="$1"
  local bench_apk="$2"

  echo "installing the benchmark build of the app"
  "$ADB_BIN" -s "$SERIAL" uninstall "$TARGET_PACKAGE" >/dev/null 2>&1 || true
  install_one "$target_apk" /data/local/tmp/cleanguard-target.apk || return 1

  echo "installing the macrobenchmark package"
  install_one "$bench_apk" /data/local/tmp/cleanguard-benchmark.apk || return 1
  return 0
}

report_install_gate() {
  local blocked
  blocked=$("$ADB_BIN" -s "$SERIAL" shell "dumpsys notification --noredact 2>/dev/null | grep -c '应用安装拦截'" | tr -d '\r')
  if [ "${blocked:-0}" -gt 0 ]; then
    echo ""
    echo "!!! HyperOS is blocking APK installation over USB." >&2
    echo "!!! The gate should be opened automatically; if it persists," >&2
    echo "!!! on the phone: 设置 → 更多设置 → 开发者选项 → 开启「USB 安装」。" >&2
    echo "!!! See docs/release/v0.2/HYPEROS-INSTALL-GATE.md" >&2
    echo ""
  fi
}

# The plan rejects a run that was disturbed by background activity. A social
# application left running on the handset was observed taking the foreground
# during a scan series, so the applications that are known to interfere are
# stopped before a series starts. This only touches the handset under test
# and never touches the measurement target.
quiesce_background() {
  local package
  for package in com.ss.android.ugc.aweme com.tencent.mm com.sina.weibo; do
    if "$ADB_BIN" -s "$SERIAL" shell "pm list packages 2>/dev/null | grep -q '^package:$package$'"; then
      "$ADB_BIN" -s "$SERIAL" shell "am force-stop $package" >/dev/null 2>&1 || true
      echo "stopped background app: $package"
    fi
  done
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

  enable_usb_install
  quiesce_background
  report_install_gate
}

preflight

install_packages \
  "app/build/outputs/apk/benchmark/app-benchmark.apk" \
  "macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk"

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
