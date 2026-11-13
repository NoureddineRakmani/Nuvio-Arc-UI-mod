#!/usr/bin/env bash
# Records Arc's own "opening a details page" timings on the TV box (no titles, IDs or links are logged).
set -uo pipefail
BOX="${NUVIO_BOX:-192.168.1.10:5555}"
APP="com.nuvio.tv.test.dev"
OUT="$HOME/nuvio-details-trace.txt"
adb connect "$BOX" >/dev/null 2>&1 || true
adb -s "$BOX" shell setprop log.tag.NuvioDetails VERBOSE
adb -s "$BOX" shell am force-stop "$APP"
adb -s "$BOX" logcat -c
adb -s "$BOX" shell monkey -p "$APP" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1
echo
echo "Nuvio Mod Test is opening on the TV."
echo "On Home: move to a poster, wait until the big background shows that title, press OK."
echo "When the details page has appeared, press Back. Do this 3 times with different titles."
read -r -p "Then press Enter here... " _
adb -s "$BOX" logcat -d -v time -s NuvioDetails:V > "$OUT"
adb -s "$BOX" shell setprop log.tag.NuvioDetails ""
echo
echo "Saved: $OUT ($(grep -c 'stage=' "$OUT") steps recorded). Send this file to Claude."
