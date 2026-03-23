#!/bin/bash

# Device Sync Android Setup Script
# Run this after installing the APK to grant necessary permissions

PACKAGE_NAME="com.duchastel.simon.devicesync"

echo "Device Sync - Android Setup"
echo "=========================="
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "Error: adb not found. Please install Android SDK Platform Tools."
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected. Please connect your device and enable USB debugging."
    exit 1
fi

echo "Granting permissions via ADB..."
echo ""

# Grant WRITE_SECURE_SETTINGS permission
echo "1. Granting WRITE_SECURE_SETTINGS permission..."
adb shell pm grant $PACKAGE_NAME android.permission.WRITE_SECURE_SETTINGS

if [ $? -eq 0 ]; then
    echo "   ✓ WRITE_SECURE_SETTINGS granted"
else
    echo "   ✗ Failed to grant WRITE_SECURE_SETTINGS"
    echo "     Make sure your device allows this permission via ADB"
fi

# Disable battery optimization
echo ""
echo "2. Disabling battery optimization..."
adb shell dumpsys deviceidle whitelist +$PACKAGE_NAME

if [ $? -eq 0 ]; then
    echo "   ✓ Battery optimization disabled"
else
    echo "   ✗ Failed to disable battery optimization"
fi

# Grant other permissions
echo ""
echo "3. Granting Bluetooth permissions..."
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_SCAN 2>/dev/null || echo "   ! BLUETOOTH_SCAN may require manual grant"
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_CONNECT 2>/dev/null || echo "   ! BLUETOOTH_CONNECT may require manual grant"
adb shell pm grant $PACKAGE_NAME android.permission.ACCESS_FINE_LOCATION 2>/dev/null || echo "   ! Location permission may require manual grant"

# Try to grant BLUETOOTH_PRIVILEGED (requires elevated privileges)
echo ""
echo "4. Attempting to grant BLUETOOTH_PRIVILEGED (for auto-connect)..."
echo "   This requires elevated ADB access..."

# Try with regular adb first
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_PRIVILEGED 2>/dev/null
if [ $? -eq 0 ]; then
    echo "   ✓ BLUETOOTH_PRIVILEGED granted via ADB"
else
    # Try with root
    echo "   Trying with root access..."
    adb shell su -c "pm grant $PACKAGE_NAME android.permission.BLUETOOTH_PRIVILEGED" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "   ✓ BLUETOOTH_PRIVILEGED granted via root"
    else
        echo "   ✗ Could not grant BLUETOOTH_PRIVILEGED"
        echo ""
        echo "   Alternative: Installing as system app..."
        echo "   This requires full root access. The app will work with limited functionality"
        echo "   (manual trackpad connection required) without this permission."
    fi
fi

echo ""
echo "4. Setting up auto-start (optional)..."
# This starts the app after boot - requires additional setup on some devices
adb shell appops set $PACKAGE_NAME android.permission.RECEIVE_BOOT_ALLOW 0 2>/dev/null || true

echo ""
echo "Setup complete!"
echo ""
echo "Next steps:"
echo "1. Open the Device Sync app on your phone"
echo "2. Enter your keyboard MAC address (e.g., AA:BB:CC:DD:EE:FF)"
echo "3. Enter your trackpad MAC address"
echo "4. Tap 'Start' to begin monitoring"
echo ""
echo "To find MAC addresses:"
echo "- Settings > Connected Devices > Bluetooth > [Device] > Details"
echo ""
echo "The app will now automatically connect your trackpad when the keyboard connects."
