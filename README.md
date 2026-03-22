# Android Device Sync

A lightweight Android app that automatically connects your Bluetooth trackpad when your keyboard connects.

## How It Works

When your Bluetooth keyboard connects to your phone, this app detects the connection and automatically initiates a connection to your Bluetooth trackpad.

## Requirements

- Android 10+ (API 29+)
- Bluetooth keyboard and trackpad already paired with your phone
- ADB access for initial setup (one-time)

## Installation

### 1. Build the APK

```bash
./gradlew assembleDebug
```

Or use Android Studio to build the project.

### 2. Install on your phone

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant Permissions (One-time setup)

```bash
./setup.sh
```

This script grants the necessary permissions via ADB.

## Usage

1. **Open the app**
2. **Enter MAC addresses:**
   - Keyboard MAC: Your keyboard's Bluetooth address (e.g., `AA:BB:CC:DD:EE:FF`)
   - Trackpad MAC: Your trackpad's Bluetooth address
   
   To find MAC addresses: Settings → Connected Devices → Bluetooth → [Device] → Details

3. **Tap "Start"**

The app will now run in the background and automatically connect your trackpad whenever the keyboard connects.

## Similar Projects

- [macOS Device Sync](../macos-device-sync) - macOS version of the same functionality