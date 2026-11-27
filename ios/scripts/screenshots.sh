#!/usr/bin/env bash
# Installs the simulator build, opens each screen with the sample days and saves a picture of it.
# Usage: scripts/screenshots.sh <path to Jejak.app> <output folder>
set -euo pipefail
APP="$1"
OUT="$2"
mkdir -p "$OUT"
DEVICE=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
d = json.load(sys.stdin)["devices"]
phones = [x for rt, xs in d.items() if "iOS" in rt for x in xs if x["name"].startswith("iPhone") and "Pro" in x["name"] and "Max" not in x["name"]]
phones = phones or [x for rt, xs in d.items() if "iOS" in rt for x in xs if x["name"].startswith("iPhone")]
print(phones[-1]["udid"])')
echo "Using simulator $DEVICE"
xcrun simctl boot "$DEVICE" 2>/dev/null || true
xcrun simctl bootstatus "$DEVICE" -b
xcrun simctl install "$DEVICE" "$APP"
xcrun simctl status_bar "$DEVICE" override --time "9:41" --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4 || true

shot() {
  xcrun simctl terminate "$DEVICE" com.r4xn.jejak 2>/dev/null || true
  # the sample days as they would be at 9:30 in the evening, whatever time the build runs
  xcrun simctl launch "$DEVICE" com.r4xn.jejak -sample -sampleTime 21:30 -screen "$1" >/dev/null
  sleep "${3:-8}"
  xcrun simctl io "$DEVICE" screenshot "$OUT/$2.png" >/dev/null
  echo "saved $2"
}

# Yesterday is a whole day (a weekday or a weekend, depending on when this runs).
shot timeline 01-timeline 12
shot timeline:-1 02-yesterday 12
shot item:2 03-stop
shot item:1 04-trip
shot points 05-every-reading
shot live 06-live
shot places 07-places
shot settings 08-settings
