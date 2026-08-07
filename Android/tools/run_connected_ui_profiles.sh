#!/usr/bin/env bash
set -euo pipefail

ANDROID_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESULT_ROOT="$ANDROID_ROOT/app/build/outputs/androidTest-results/connected/debug"
REPORT_ROOT="$ANDROID_ROOT/app/build/reports/androidTests/connected/debug"
EVIDENCE_ROOT="$ANDROID_ROOT/build/quality/android-ui"
CURRENT_PROFILE="not_started"

restore_device() {
  set +e
  adb shell wm size reset
  adb shell wm density reset
  adb shell settings put system font_scale 1.0
}

finalize_run() {
  local exit_code=$?
  set +e
  local diagnostics="$EVIDENCE_ROOT/_diagnostics"
  mkdir -p "$diagnostics"
  printf 'exit_code=%s\nprofile=%s\n' "$exit_code" "$CURRENT_PROFILE" \
    > "$EVIDENCE_ROOT/run-status.txt"
  adb devices -l > "$diagnostics/adb-devices.txt" 2>&1
  if [[ -d "$RESULT_ROOT" ]]; then
    rm -rf "$diagnostics/raw-results"
    mkdir -p "$diagnostics/raw-results"
    cp -R "$RESULT_ROOT"/. "$diagnostics/raw-results"/
  fi
  if [[ -d "$REPORT_ROOT" ]]; then
    rm -rf "$diagnostics/report"
    mkdir -p "$diagnostics/report"
    cp -R "$REPORT_ROOT"/. "$diagnostics/report"/
  fi
  restore_device
  trap - EXIT
  exit "$exit_code"
}
trap finalize_run EXIT

configure_profile() {
  local profile="$1"
  adb shell wm size reset
  adb shell wm density reset
  adb shell settings put system font_scale 1.0
  case "$profile" in
    standard_api35)
      ;;
    compact_api35)
      adb shell wm size 700x1280
      adb shell wm density 320
      ;;
    large_text_api35)
      adb shell settings put system font_scale 1.3
      ;;
    *)
      echo "unknown deterministic UI profile: $profile" >&2
      return 1
      ;;
  esac
  adb shell am force-stop com.xjie.app
}

run_profile() {
  local profile="$1"
  local destination="$EVIDENCE_ROOT/$profile"
  CURRENT_PROFILE="$profile"
  rm -rf "$RESULT_ROOT" "$REPORT_ROOT"
  configure_profile "$profile"
  "$ANDROID_ROOT/gradlew" --no-daemon :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.xjie.ui.profile=$profile"
  local result_file
  result_file="$(find "$RESULT_ROOT" -name 'TEST-*.xml' -print -quit)"
  test -n "$result_file"
  rm -rf "$destination"
  mkdir -p "$destination"
  cp -R "$RESULT_ROOT"/. "$destination"/
}

rm -rf "$EVIDENCE_ROOT"
mkdir -p "$EVIDENCE_ROOT"
printf 'exit_code=pending\nprofile=not_started\n' > "$EVIDENCE_ROOT/run-status.txt"
run_profile standard_api35
run_profile compact_api35
run_profile large_text_api35

python3 "$ANDROID_ROOT/tools/verify_android_ui_test_inventory.py" \
  --result-set "standard_api35=$EVIDENCE_ROOT/standard_api35" \
  --result-set "compact_api35=$EVIDENCE_ROOT/compact_api35" \
  --result-set "large_text_api35=$EVIDENCE_ROOT/large_text_api35"
