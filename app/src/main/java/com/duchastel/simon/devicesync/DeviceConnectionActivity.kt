package com.duchastel.simon.devicesync

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresPermission

class DeviceConnectionActivity : Activity() {
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val logger = Logger.getInstance(this)
        
        if (intent?.action == "android.bluetooth.devicepicker.action.DEVICE_SELECTED") {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            device?.let {
                logger.log("Device selected in picker: ${it.name}")

                // Show toast to user
                Toast.makeText(
                    this,
                    "Please manually connect to ${it.name} in Bluetooth settings",
                    Toast.LENGTH_LONG
                ).show()
                
                // Open Bluetooth settings
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(settingsIntent)
            }
        }
        
        // Finish this activity immediately
        finish()
    }
}