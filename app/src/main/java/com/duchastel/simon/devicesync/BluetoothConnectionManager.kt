package com.duchastel.simon.devicesync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class BluetoothConnectionManager(private val context: Context) {
    companion object {
        const val TAG = "DeviceSync"
    }

    private val logger = Logger.getInstance(context)

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun connectTrackpad(macAddress: String) {
        if (adapter == null) {
            logger.log("Bluetooth adapter not available")
            return
        }

        if (!adapter.isEnabled) {
            logger.log("Bluetooth is disabled")
            return
        }

        val normalizedMac = normalizeMac(macAddress)
        val bondedDevices = adapter.bondedDevices

        for (device in bondedDevices) {
            if (normalizeMac(device.address) == normalizedMac) {
                logger.log("Found trackpad: ${device.name} (${device.address})")

                // Check if already connected
                if (isDeviceConnected(device)) {
                    logger.log("Trackpad already connected")
                    return
                }

                // Try to connect using reflection
                connectUsingReflection(device)
                return
            }
        }

        logger.log("Trackpad not found in paired devices: $macAddress")
    }

    fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
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

    private fun connectUsingReflection(device: BluetoothDevice) {
        try {
            // Method 1: Try using BluetoothHidHost hidden API (for HID devices like trackpads)
            connectUsingHidHost(device)
        } catch (e: Exception) {
            logger.log("HID host connection failed, trying alternative methods: ${e.message}")
            try {
                // Method 2: Try using createBond with HID profile
                connectUsingCreateBond(device)
            } catch (e2: Exception) {
                logger.log("All connection methods failed: ${e2.message}")
            }
        }
    }

    private fun connectUsingHidHost(device: BluetoothDevice) {
        try {
            logger.log("Attempting HID host connection...")

            // Get BluetoothHidHost class via reflection
            val hidHostClass = Class.forName("android.bluetooth.BluetoothHidHost")

            // Get the HID_HOST profile constant
            val hidHostProfileField = hidHostClass.getDeclaredField("HID_HOST")
            val hidHostProfile = hidHostProfileField.get(null) as Int

            // Get BluetoothProfile proxy
            val serviceListener = object : android.bluetooth.BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                    try {
                        if (profile == hidHostProfile) {
                            // Call connect method on the proxy
                            val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                            val result = connectMethod.invoke(proxy, device) as Boolean

                            if (result) {
                                logger.log("HID connect call succeeded")
                                // Verify connection after a delay
                                handler.postDelayed({
                                    if (isDeviceConnected(device)) {
                                        logger.log("Trackpad connected successfully via HID")
                                    } else {
                                        logger.log("Trackpad connection may have failed")
                                    }
                                }, 2000)
                            } else {
                                logger.log("HID connect call returned false")
                            }

                            // Close the proxy
                            try {
                                val closeMethod = proxy.javaClass.getMethod("closeProxy")
                                closeMethod.invoke(proxy)
                            } catch (e: Exception) {
                                // Method might not exist, ignore
                            }
                        }
                    } catch (e: Exception) {
                        logger.log("Error in HID service connection: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    logger.log("HID service disconnected")
                }
            }

            // Get the proxy
            adapter?.getProfileProxy(context, serviceListener, hidHostProfile)

        } catch (e: Exception) {
            logger.log("HID host connection error: ${e.message}")
            throw e
        }
    }

    private fun connectUsingCreateBond(device: BluetoothDevice) {
        try {
            logger.log("Attempting createBond connection...")

            // For already bonded devices, we can try to trigger a connection
            // by calling connectGatt or using the hidden connect method
            val connectMethod = device.javaClass.getDeclaredMethod("connect")
            connectMethod.isAccessible = true
            val result = connectMethod.invoke(device)

            logger.log("CreateBond connect result: $result")

            // Also try the standard connectGatt approach
            handler.postDelayed({
                if (!isDeviceConnected(device)) {
                    tryConnectGatt(device)
                }
            }, 1000)

        } catch (e: Exception) {
            logger.log("CreateBond connection error: ${e.message}")
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnectGatt(device: BluetoothDevice) {
        try {
            logger.log("Attempting GATT connection...")

            // Use reflection to access connectGatt with specific transport
            val connectGattMethod = device.javaClass.getDeclaredMethod(
                "connectGatt",
                Context::class.java,
                Boolean::class.javaPrimitiveType,
                android.bluetooth.BluetoothGattCallback::class.java,
                Int::class.javaPrimitiveType
            )

            val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: android.bluetooth.BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                            logger.log("GATT connected")
                            gatt.disconnect()
                            gatt.close()
                        }
                        android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                            logger.log("GATT disconnected")
                            gatt.close()
                        }
                    }
                }
            }

            // TRANSPORT_LE = 2, TRANSPORT_BREDR = 1, TRANSPORT_AUTO = 0
            connectGattMethod.invoke(device, context, false, gattCallback, 1)

        } catch (e: Exception) {
            logger.log("GATT connection error: ${e.message}")
        }
    }

    private fun normalizeMac(mac: String): String {
        return mac.uppercase().replace(":", "").replace("-", "")
    }
}
