<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./static/droidrun-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./static/droidrun.png">
  <img src="./static/droidrun.png"  width="full">
</picture>

[![GitHub stars](https://img.shields.io/github/stars/droidrun/droidrun-portal?style=social)](https://github.com/droidrun/droidrun-portal/stargazers)
[![Discord](https://img.shields.io/discord/1360219330318696488?color=7289DA&label=Discord&logo=discord&logoColor=white)](https://discord.gg/ZZbKEZZkwK)
[![Documentation](https://img.shields.io/badge/Documentation-📕-blue)](https://docs.droidrun.ai)
[![Twitter Follow](https://img.shields.io/twitter/follow/droid_run?style=social)](https://x.com/droid_run)

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
- Can extract ALL visible elements (even non-interactive ones) with detailed properties

### 📊 Data Collection
- Exports element data in JSON format
- Provides element properties including:
  - Text content
  - Class name
  - Element index
  - Bounding rectangle coordinates
  - Element type (clickable, checkable, input, scrollable, focusable)
- Accessible via ADB commands or file output
- Separate commands for interactive elements only or complete screen hierarchy

## 🚀 Usage

### ⚙️ Setup
1. Install the app on your Android device
2. Enable the accessibility service in Android Settings → Accessibility → Droidrun Portal
3. Grant overlay permission when prompted

### 💻 ADB Commands
```bash
# Get interactive elements as JSON (clickable, checkable, etc.)
adb shell am broadcast -a com.droidrun.portal.GET_ELEMENTS
# Alternative command for interactive elements
adb shell am broadcast -a com.droidrun.portal.GET_INTERACTIVE_ELEMENTS

# Get ALL elements (even non-interactive ones) as JSON
adb shell am broadcast -a com.droidrun.portal.GET_ALL_ELEMENTS

# Toggle overlay visibility
adb shell am broadcast -a com.droidrun.portal.TOGGLE_OVERLAY --ez overlay_visible true/false

# Reset element timestamps (useful for testing)
adb shell am broadcast -a com.droidrun.portal.RETRIGGER_ELEMENTS
```

### 📤 Data Output
Element data is output in JSON format through ADB logs and is also saved to the app's external storage directory. The data can be captured using the included script:

#### Using dump_view.sh (direct log capture)
```bash
# Get only interactive elements (default)
./scripts/dump_view.sh

# Get ALL elements including non-interactive ones
./scripts/dump_view.sh -a

# Specify mode explicitly
./scripts/dump_view.sh -m all    # All elements
./scripts/dump_view.sh -m clickable    # Only interactive elements
```

JSON files will be saved to the output directory with the appropriate naming (elements.json or all_elements.json).

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
- Complete screen content extraction for automation or analysis

## 🔄 Continuous Integration

This project uses GitHub Actions for automated building and releasing.

### 📦 Automated Builds

Every push to the main branch or pull request will trigger the build workflow that:
- Builds the Android app
- Creates the APK
- Uploads the APK as an artifact in the GitHub Actions run
