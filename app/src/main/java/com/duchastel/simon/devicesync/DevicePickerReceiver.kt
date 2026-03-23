package com.duchastel.simon.devicesync

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission

class DevicePickerReceiver : BroadcastReceiver() {
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context, intent: Intent) {
        val logger = Logger.getInstance(context)
        
        when (intent.action) {
            "android.bluetooth.devicepicker.action.DEVICE_SELECTED" -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    logger.log("Device selected from picker: ${it.name} (${it.address})")
                    // Open device settings so user can manually connect
                    openDeviceSettings(context, it)
                }
            }
            else -> {
                logger.log("DevicePickerReceiver received unknown action: ${intent.action}")
            }
        }
    }
    
    private fun openDeviceSettings(context: Context, device: BluetoothDevice) {
        val logger = Logger.getInstance(context)
        try {
            // Try to open the device's Bluetooth settings page
            val intent = Intent().apply {
                action = "android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST"
                putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                putExtra("android.bluetooth.device.extra.CONNECTION_ACCESS_RSP", "yes")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            logger.log("Opened device connection access request")
        } catch (e: Exception) {
            logger.log("Failed to open connection access: ${e.message}")
            
            // Fallback: Open general Bluetooth settings
            try {
                val settingsIntent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(settingsIntent)
                logger.log("Opened Bluetooth settings as fallback")
            } catch (e2: Exception) {
                logger.log("Failed to open Bluetooth settings: ${e2.message}")
            }
        }
    }
}