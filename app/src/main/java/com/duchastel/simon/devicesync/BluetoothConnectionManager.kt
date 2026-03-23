package com.duchastel.simon.devicesync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

class BluetoothConnectionManager(private val context: Context) {
    private val logger = Logger.getInstance(context)

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    @SuppressLint("MissingPermission")
    fun connectTrackpad(macAddress: String) {
        logger.log("Connecting trackpad with MAC: $macAddress")

        if (adapter == null) {
            logger.log("Bluetooth adapter not available")
            return
        }

        if (!adapter.isEnabled) {
            logger.log("Bluetooth is disabled")
            return
        }

        val normalizedMac = normalizeMac(macAddress)
        logger.log("Normalized trackpad MAC: $normalizedMac")
        
        val bondedDevices = adapter.bondedDevices
        logger.log("Found ${bondedDevices.size} bonded devices")
        
        bondedDevices.forEach { device ->
            logger.log("  Bonded device: ${device.name} (${device.address}) [normalized: ${normalizeMac(device.address)}]")
        }

        for (device in bondedDevices) {
            if (normalizeMac(device.address) == normalizedMac) {
                logger.log("Found trackpad: ${device.name} (${device.address})")

                // Check if already connected
                if (isDeviceConnected(device)) {
                    logger.log("Trackpad already connected")
                    return
                }

                // Try to connect
                connectToDevice(device)
                return
            }
        }

        logger.log("Trackpad not found in paired devices: $macAddress")
    }

    fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (_: Exception) {
            // Fallback: check if device is in the connected devices list via reflection
            try {
                val bluetoothManagerClass = Class.forName("android.bluetooth.BluetoothManager")
                val getConnectedDevicesMethod = bluetoothManagerClass.getDeclaredMethod("getConnectedDevices", Int::class.javaPrimitiveType)
                @Suppress("UNCHECKED_CAST")
                val connectedDevices = getConnectedDevicesMethod.invoke(bluetoothManager, 1) as? List<BluetoothDevice>
                connectedDevices?.any { it.address == device.address } ?: false
            } catch (e2: Exception) {
                logger.log("Failed to check connection status: ${e2.message}")
                false
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        logger.log("Connecting to device via INPUT_DEVICE profile (profile 2)...")
        
        adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile != 2) { // INPUT_DEVICE = 2
                    return
                }

                logger.log("Connected to INPUT_DEVICE profile")
                
                // Try to connect using reflection since INPUT_DEVICE has privileged methods
                try {
                    val connectMethod = proxy?.javaClass?.getMethod("connect", BluetoothDevice::class.java)
                    val success = connectMethod?.invoke(proxy, device) as? Boolean ?: false
                    logger.log("connect() returned: $success")
                    
                    if (!success) {
                        // Try setConnectionPolicy as fallback
                        try {
                            val setPolicyMethod = proxy?.javaClass?.getMethod("setConnectionPolicy", 
                                BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                            val result = setPolicyMethod?.invoke(proxy, device, 100) // 100 = CONNECTION_POLICY_ALLOWED
                            logger.log("setConnectionPolicy() returned: $result")
                        } catch (e: Exception) {
                            logger.log("setConnectionPolicy failed: ${e.message}")
                            if (e is SecurityException || e.cause is SecurityException) {
                                triggerConnectionDialog()
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.log("Connection failed: ${e.message}")
                    if (e is SecurityException || e.cause is SecurityException) {
                        triggerConnectionDialog()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == 2) { // INPUT_DEVICE = 2
                    logger.log("Disconnected from INPUT_DEVICE profile")
                }
            }
        }, 2) // INPUT_DEVICE profile
    }

    private fun normalizeMac(mac: String): String {
        return mac.uppercase().replace(":", "").replace("-", "")
    }

    @SuppressLint("MissingPermission")
    private fun triggerConnectionDialog() {
        logger.log("Opening system Bluetooth device picker...")
        try {
            // Launch system device picker
            val intent = android.content.Intent("android.bluetooth.devicepicker.action.LAUNCH")
            intent.putExtra("android.bluetooth.devicepicker.extra.NEED_AUTH", true)
            intent.putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 0) // 0 = All devices
            intent.putExtra("android.bluetooth.devicepicker.extra.LAUNCH_PACKAGE", context.packageName)
            intent.putExtra("android.bluetooth.devicepicker.extra.DEVICE_PICKER_LAUNCH_CLASS", 
                "com.duchastel.simon.devicesync.DevicePickerReceiver")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            logger.log("Device picker launched!")
        } catch (e: Exception) {
            logger.log("Failed to launch device picker: ${e.message}")

            // Fallback: Try Bluetooth settings
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                intent.setClassName("com.android.settings", "com.android.settings.bluetooth.BluetoothSettings")
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                logger.log("Opened Bluetooth settings as fallback")
            } catch (e2: Exception) {
                logger.log("Failed to open Bluetooth settings: ${e2.message}")
            }
        }
    }
}
