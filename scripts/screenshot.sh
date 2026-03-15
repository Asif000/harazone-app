#!/bin/bash
# Take screenshot from connected Android device and save to Desktop.
# Safe for terminal — suppresses all binary output from adb pull.
OUT="${1:-$HOME/Desktop/screen.png}"
adb shell screencap -p /sdcard/screen.png && \
adb pull /sdcard/screen.png "$OUT" > /dev/null 2>&1 && \
adb shell rm /sdcard/screen.png && \
echo "Screenshot saved: $OUT"
