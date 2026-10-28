#!/bin/bash
# Captures UI screenshots on a running emulator. Usage: scripts/shots.sh <apk>
set -x
mkdir -p shots
adb install -r "$1"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
shot() { n=$1; w=$2; shift 2; adb shell am start -S -W -n com.ye.webview/.MainActivity "$@"; sleep "$w"; adb exec-out screencap -p > "shots/$n.png"; }
shot 1_home 8
shot 2_page 20 --es shot page
shot 3_more 22 --es shot more
shot 4_tabs 30 --es shot tabs
adb shell cmd uimode night yes; sleep 3
shot 5_home_dark 8
shot 6_page_dark 20 --es shot page
shot 7_more_dark 22 --es shot more
ls -la shots
exit 0
