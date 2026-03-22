# ProGuard rules for Device Sync

# Keep classes that use reflection
-keep class android.bluetooth.BluetoothHidHost { *; }
-keep class android.bluetooth.BluetoothDevice { *; }
-keepclassmembers class android.bluetooth.BluetoothDevice {
    public boolean isConnected();
    public boolean connect();
    public android.bluetooth.BluetoothGatt connectGatt(android.content.Context, boolean, android.bluetooth.BluetoothGattCallback, int);
}

# Keep service
-keep class com.devicesync.bluetooth.BluetoothMonitorService { *; }
-keep class com.devicesync.bluetooth.BluetoothConnectionManager { *; }

# Keep Activity
-keep class com.devicesync.bluetooth.MainActivity { *; }
