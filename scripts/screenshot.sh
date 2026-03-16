#!/bin/bash
# Take screenshot from connected device (Android or iOS simulator).
# Uses adb pull (not exec-out pipe) to avoid PNG corruption.
# Validates output before reporting success.

set -euo pipefail

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DIR="${1:-/Users/asifchauhan/Desktop/dev-screenshots}"
OUT="$DIR/screenshot_$TIMESTAMP.png"
DEVICE_TMP="/sdcard/screenshot_tmp.png"

# PNG magic bytes: 89 50 4e 47
validate_png() {
    local file="$1"
    if [ ! -f "$file" ] || [ ! -s "$file" ]; then
        echo "ERROR: Screenshot file missing or empty"
        return 1
    fi
    local magic
    magic=$(xxd -p -l 4 "$file")
    if [ "$magic" != "89504e47" ]; then
        echo "ERROR: Corrupted PNG (bad magic bytes: $magic)"
        rm -f "$file"
        return 1
    fi
    return 0
}

# Try Android first
if adb get-state >/dev/null 2>&1; then
    echo "Android device detected..."
    adb shell screencap -p "$DEVICE_TMP" 2>/dev/null
    adb pull "$DEVICE_TMP" "$OUT" >/dev/null 2>&1
    adb shell rm -f "$DEVICE_TMP" 2>/dev/null

    if validate_png "$OUT"; then
        echo "Screenshot saved: $OUT"
        exit 0
    else
        echo "Android screenshot failed, retrying..."
        rm -f "$OUT"
    fi
fi

# Fallback to iOS simulator
if xcrun simctl list devices booted 2>/dev/null | grep -q "Booted"; then
    echo "iOS simulator detected..."
    xcrun simctl io booted screenshot "$OUT" 2>/dev/null

    if validate_png "$OUT"; then
        echo "Screenshot saved: $OUT"
        exit 0
    fi
fi

echo "ERROR: No device found or screenshot failed"
exit 1
