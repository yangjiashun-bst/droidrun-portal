#!/bin/bash

# Script to fetch UI element data from the Droidrun Portal service

# Parse command line arguments
INTERACTIVE=false
while getopts "i" opt; do
    case $opt in
        i)
            INTERACTIVE=true
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            exit 1
            ;;
    esac
done

echo "🔍 Droidrun Portal - Fetching UI element data..."
if [ "$INTERACTIVE" = true ]; then
    echo "Mode: Interactive elements only"
    adb shell am broadcast -a com.droidrun.portal.GET_INTERACTIVE_ELEMENTS
else
    echo "Mode: All elements"
    adb shell am broadcast -a com.droidrun.portal.GET_ELEMENTS
fi

echo "⏳ Waiting for response..."
sleep 2  # Give time for the service to process

echo "📥 Retrieving element data from files..."
PACKAGE_NAME="com.droidrun.portal"
APP_FILES_DIR="/storage/emulated/0/Android/data/$PACKAGE_NAME/files"

# Create output directory if it doesn't exist
mkdir -p ./output

# Pull the files from external storage
echo "Pulling data files..."
adb pull "$APP_FILES_DIR/element_data_compact.json" ./output/element_data_compact.json
if [ $? -eq 0 ]; then
    echo "✅ Successfully retrieved compact JSON data"
    
    # Count number of elements
    ELEMENT_COUNT=$(grep -o "\"t\":" ./output/element_data_compact.json | wc -l)
    echo "🧩 Found $ELEMENT_COUNT UI elements"
    
    # Show file size
    COMPACT_SIZE=$(du -h ./output/element_data_compact.json | cut -f1)
    echo "📦 Compact JSON file size: $COMPACT_SIZE"
    
    # Create a more readable version with one element per line
    echo "📝 Creating human-readable version..."
    cat ./output/element_data_compact.json | 
        python3 -m json.tool > ./output/element_data_readable.json
    
    echo "ℹ️  Files saved:"
    echo "   - ./output/element_data_compact.json (compressed format)"
    echo "   - ./output/element_data_readable.json (human-readable format)"
    
    echo "ℹ️  The JSON format uses these property names:"
    echo "   t: text              b: bounds"
    echo "   c: className         w: weight"
    echo "   ic: isClickable      ik: isCheckable"
    echo "   ie: isEditable       l: windowLayer"
else
    echo "❌ Error: Could not retrieve element data"
    echo "Make sure the app is running and has accessibility permissions"
    exit 1
fi

echo "📥 Retrieving element data from files..."
JSON_PATH=$(adb shell logcat -d | grep "DROIDRUN_FILE" | grep "JSON data written to" | tail -1 | sed 's/.*JSON data written to: \(.*\)/\1/')
COMPACT_PATH=$(adb shell logcat -d | grep "DROIDRUN_FILE" | grep "Compact JSON written to" | tail -1 | sed 's/.*Compact JSON written to: \(.*\)/\1/')

# Create a directory for the output if it doesn't exist
mkdir -p ./output

if [ -n "$COMPACT_PATH" ]; then
    echo "✅ Found compact JSON file at: $COMPACT_PATH"
    OUTPUT_FILE="./output/element_data_compact.json"
    if [ "$INTERACTIVE" = true ]; then
        OUTPUT_FILE="./output/interactive_elements_compact.json"
    fi
    adb pull "$COMPACT_PATH" "$OUTPUT_FILE"
    echo "💾 Compact JSON data saved to $OUTPUT_FILE"
    
    # Get the size and compression stats
    COMPRESSION_INFO=$(adb shell logcat -d | grep "DROIDRUN_ADB_RESPONSE" | grep "reduction" | tail -1)
    echo "📊 $COMPRESSION_INFO"
    
    # Count number of elements in the compact JSON
    ELEMENT_COUNT=$(grep -o "\"t\":" "$OUTPUT_FILE" | wc -l)
    echo "🧩 Found $ELEMENT_COUNT UI elements in compact format"
    
    # Show file size
    COMPACT_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
    echo "📦 Compact JSON file size: $COMPACT_SIZE"
    
    echo "ℹ️  The compact JSON uses shorter property names:"
    echo "  t: text              b: bounds"
    echo "  c: className         w: weight"
    echo "  a: age               l: windowLayer" 
    echo "  ic: isClickable      ik: isCheckable"
    echo "  ie: isEditable       ac: additionalContext"
fi

if [ -n "$JSON_PATH" ]; then
    echo "✅ Found full JSON file at: $JSON_PATH"
    adb pull "$JSON_PATH" ./output/element_data.json
    echo "💾 Full JSON data saved to ./output/element_data.json"
    
    # Count number of elements
    ELEMENT_COUNT=$(grep -o "\"text\":" ./output/element_data.json | wc -l)
    echo "🧩 Found $ELEMENT_COUNT UI elements in full format"
    
    # Show file size
    FILE_SIZE=$(du -h ./output/element_data.json | cut -f1)
    echo "📦 Full JSON file size: $FILE_SIZE"
fi

if [ -z "$JSON_PATH" ] && [ -z "$COMPACT_PATH" ]; then
    echo "⚠️ JSON file paths not found in logs. Falling back to logcat output."
fi

# Create an element-per-line version of the JSON
if [ -n "$COMPACT_PATH" ]; then
    echo "🔄 Creating element-per-line format from compact JSON..."
    cat "$OUTPUT_FILE" | 
        sed 's/\[{/{/g' | 
        sed 's/},{/},\n{/g' | 
        sed 's/}\]/}\n/g' > ./output/element_data_lines.json
    
    echo "💾 Element-per-line JSON saved to ./output/element_data_lines.json"
    echo "ℹ️  For one element per line, use: cat ./output/element_data_lines.json"
fi

echo ""
echo "📋 Available commands:"
echo "  • View compact data: cat ./output/element_data_compact.json"
echo "  • View full data: cat ./output/element_data.json"
echo "  • View element-per-line: cat ./output/element_data_lines.json"
echo ""

echo "📊 Running logcat to show element data (press Ctrl+C to exit)..."
adb logcat -c  # Clear existing logs
adb logcat | grep -E "DROIDRUN_TEXT|DROIDRUN_FILE|DROIDRUN_PORTAL" 