# 🤖 Droidrun Portal

## 👁️ Overview
Droidrun Portal is an Android accessibility service that provides real-time visual feedback and data collection for UI elements on the screen. It creates an interactive overlay that highlights clickable, checkable, editable, scrollable, and focusable elements, making it an invaluable tool for UI testing, automation development, and accessibility assessment.

## ✨ Features

### 🎨 Visual Overlay
- Real-time highlighting of interactive UI elements
- Heat map visualization (red to blue) indicating element freshness
- Automatic depth ordering to handle overlapping elements
- Element labeling with type and index information
- Adjustable visibility through ADB commands

### 🔍 Element Detection
- Identifies all interactive elements (clickable, checkable, editable, scrollable, and focusable)
- Handles nested elements and scrollable containers
- Tracks element persistence across screen updates
- Assigns unique indices to interactive elements for reference

### 📊 Data Collection
- Exports element data in JSON format
- Provides element properties including:
  - Text content
  - Class name
  - Element index
  - Bounding rectangle coordinates
  - Element type (clickable, checkable, input, scrollable, focusable)
- Accessible via ADB commands or file output

## 🚀 Usage

### ⚙️ Setup
1. Install the app on your Android device
2. Enable the accessibility service in Android Settings → Accessibility → Droidrun Portal
3. Grant overlay permission when prompted

### 💻 ADB Commands
```bash
# Get all current elements as JSON
adb shell am broadcast -a com.droidrun.portal.GET_ELEMENTS

# Toggle overlay visibility
adb shell am broadcast -a com.droidrun.portal.TOGGLE_OVERLAY --ez overlay_visible true/false

# Reset element timestamps (useful for testing)
adb shell am broadcast -a com.droidrun.portal.RETRIGGER_ELEMENTS
```

### 📤 Data Output
Element data is output in JSON format through ADB logs and is also saved to the app's external storage directory as `element_data.json`. The data can be captured using the included `dump_view.sh` script.

## 🔧 Technical Details
- Minimum Android API level: 24 (Android 7.0)
- Uses Android Accessibility Service API
- Implements custom drawing overlay using Window Manager
- Supports multi-window environments
- Built with Kotlin

## 💡 Use Cases
- UI testing and verification
- Developing accessibility tools
- Creating UI automation scripts
- Analyzing app UI structure
- Learning about Android UI components

## 📜 License
[Your license information here] 