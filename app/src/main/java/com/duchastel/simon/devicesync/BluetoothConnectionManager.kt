package com.duchastel.simon.devicesync

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method

class BluetoothConnectionManager(private val context: Context) {
    companion object {
        const val TAG = "DeviceSync"
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val handler = Handler(Looper.getMainLooper())

    fun connectTrackpad(macAddress: String) {
        if (adapter == null) {
            Log.e(TAG, "Bluetooth adapter not available")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled")
            return
        }

        val normalizedMac = normalizeMac(macAddress)
        val bondedDevices = adapter.bondedDevices

        for (device in bondedDevices) {
            if (normalizeMac(device.address) == normalizedMac) {
                Log.d(TAG, "Found trackpad: ${device.name} (${device.address})")

                // Check if already connected
                if (isDeviceConnected(device)) {
                    Log.d(TAG, "Trackpad already connected")
                    return
                }

                // Try to connect using reflection
                connectUsingReflection(device)
                return
            }
        }

        Log.e(TAG, "Trackpad not found in paired devices: $macAddress")
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
                Log.e(TAG, "Failed to check connection status", e2)
                false
            }
        }
    }

    private fun connectUsingReflection(device: BluetoothDevice) {
        try {
            // Method 1: Try using BluetoothHidHost hidden API (for HID devices like trackpads)
            connectUsingHidHost(device)
        } catch (e: Exception) {
            Log.w(TAG, "HID host connection failed, trying alternative methods", e)
            try {
                // Method 2: Try using createBond with HID profile
                connectUsingCreateBond(device)
            } catch (e2: Exception) {
                Log.e(TAG, "All connection methods failed", e2)
            }
        }
    }

    private fun connectUsingHidHost(device: BluetoothDevice) {
        try {
            Log.d(TAG, "Attempting HID host connection...")

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
                                Log.d(TAG, "HID connect call succeeded")
                                // Verify connection after a delay
                                handler.postDelayed({
                                    if (isDeviceConnected(device)) {
                                        Log.d(TAG, "Trackpad connected successfully via HID")
                                    } else {
                                        Log.w(TAG, "Trackpad connection may have failed")
                                    }
                                }, 2000)
                            } else {
                                Log.e(TAG, "HID connect call returned false")
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
                        Log.e(TAG, "Error in HID service connection", e)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    Log.d(TAG, "HID service disconnected")
                }
            }

            // Get the proxy
            adapter?.getProfileProxy(context, serviceListener, hidHostProfile)

        } catch (e: Exception) {
            Log.e(TAG, "HID host connection error", e)
            throw e
        }
    }

    private fun connectUsingCreateBond(device: BluetoothDevice) {
        try {
            Log.d(TAG, "Attempting createBond connection...")

            // For already bonded devices, we can try to trigger a connection
            // by calling connectGatt or using the hidden connect method
            val connectMethod = device.javaClass.getDeclaredMethod("connect")
            connectMethod.isAccessible = true
            val result = connectMethod.invoke(device)

            Log.d(TAG, "CreateBond connect result: $result")

            // Also try the standard connectGatt approach
            handler.postDelayed({
                if (!isDeviceConnected(device)) {
                    tryConnectGatt(device)
                }
            }, 1000)

        } catch (e: Exception) {
            Log.e(TAG, "CreateBond connection error", e)
            throw e
        }
    }

    private fun tryConnectGatt(device: BluetoothDevice) {
        try {
            Log.d(TAG, "Attempting GATT connection...")

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
                            Log.d(TAG, "GATT connected")
                            gatt.disconnect()
                            gatt.close()
                        }
                        android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d(TAG, "GATT disconnected")
                            gatt.close()
                        }
                    }
                }
            }

            // TRANSPORT_LE = 2, TRANSPORT_BREDR = 1, TRANSPORT_AUTO = 0
            connectGattMethod.invoke(device, context, false, gattCallback, 1)

        } catch (e: Exception) {
            Log.e(TAG, "GATT connection error", e)
        }
    }

    private fun normalizeMac(mac: String): String {
        return mac.uppercase().replace(":", "").replace("-", "")
    }
}
