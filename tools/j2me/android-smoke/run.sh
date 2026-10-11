#!/usr/bin/env bash
set -euo pipefail
apk=tools/j2me/android-smoke/app/build/outputs/apk/debug/app-debug.apk
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n io.github.h3nb.d8j2me.smoke/.SmokeActivity
for attempt in $(seq 1 40); do
  adb logcat -d -s D8_J2ME_SMOKE:I '*:S' > "$RUNNER_TEMP/d8-j2me-android.log"
  if grep -q 'D8_J2ME_SMOKE.*PASS' "$RUNNER_TEMP/d8-j2me-android.log"; then
    cat "$RUNNER_TEMP/d8-j2me-android.log"
    exit 0
  fi
  if grep -q 'D8_J2ME_SMOKE.*FAIL' "$RUNNER_TEMP/d8-j2me-android.log"; then
    adb logcat -d -t 250
    exit 1
  fi
  sleep 3
done
adb logcat -d -t 250
exit 1
