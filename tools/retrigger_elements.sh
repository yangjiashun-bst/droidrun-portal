#!/bin/bash

# Script to retrigger UI elements in the Droidrun Portal service, resetting their weights to 1.0

echo "🔄 Droidrun Portal - Refreshing UI elements..."
adb shell am broadcast -a com.droidrun.portal.RETRIGGER_ELEMENTS

echo "⏳ Waiting for response..."
sleep 1

echo "📊 Running logcat to show response (press Ctrl+C to exit)..."
adb logcat -c  # Clear existing logs
adb logcat | grep -E "DROIDRUN_RETRIGGER|DROIDRUN_RECEIVER|DROIDRUN_PORTAL" 