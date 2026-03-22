package com.duchastel.simon.devicesync

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class BluetoothMonitorService : Service() {
    private lateinit var bluetoothConnectionManager: BluetoothConnectionManager
    private var keyboardMac: String = ""
    private var trackpadMac: String = ""
    private var autoDisconnectTrackpad: Boolean = true
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val TAG = "DeviceSync"
        const val CHANNEL_ID = "bluetooth_monitor_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }

    @SuppressLint("MissingPermission")
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val deviceAddress = it.address
                        val deviceName = it.name ?: "Unknown"
                        Log.d(TAG, "Device connected: $deviceName ($deviceAddress)")

                        if (matchesKeyboard(deviceAddress)) {
                            Log.d(TAG, "Keyboard connected! Attempting to connect trackpad...")
                            bluetoothConnectionManager.connectTrackpad(trackpadMac)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val deviceAddress = it.address
                        val deviceName = it.name ?: "Unknown"
                        Log.d(TAG, "Device disconnected: $deviceName ($deviceAddress)")

                        if (matchesKeyboard(deviceAddress) && autoDisconnectTrackpad) {
                            Log.d(TAG, "Keyboard disconnected! Auto-disconnecting trackpad...")
                            bluetoothConnectionManager.disconnectDevice(trackpadMac)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothConnectionManager = BluetoothConnectionManager(this)

        // Acquire wake lock to keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DeviceSync::WakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        keyboardMac = intent?.getStringExtra("keyboard_mac") ?: ""
        trackpadMac = intent?.getStringExtra("trackpad_mac") ?: ""
        autoDisconnectTrackpad = intent?.getBooleanExtra("auto_disconnect_trackpad", true) ?: true

        if (keyboardMac.isEmpty() || trackpadMac.isEmpty()) {
            Log.e(TAG, "MAC addresses not provided")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Acquire wake lock
        wakeLock?.acquire(10*60*1000L) // 10 minutes

        registerBluetoothReceiver()
        isRunning = true

        Log.d(TAG, "Service started - monitoring keyboard: $keyboardMac, trackpad: $trackpadMac, autoDisconnect: $autoDisconnectTrackpad")

        // Check if keyboard is already connected
        checkInitialConnectionStatus()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        isRunning = false
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    private fun matchesKeyboard(address: String): Boolean {
        return normalizeMac(address) == normalizeMac(keyboardMac)
    }

    private fun normalizeMac(mac: String): String {
        return mac.uppercase().replace(":", "").replace("-", "")
    }

    @SuppressLint("MissingPermission")
    private fun checkInitialConnectionStatus() {
        // Check if keyboard is already connected and connect trackpad if so
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter != null && adapter.isEnabled) {
            val bondedDevices = adapter.bondedDevices
            for (device in bondedDevices) {
                if (matchesKeyboard(device.address)) {
                    try {
                        val isConnected = bluetoothConnectionManager.isDeviceConnected(device)
                        if (isConnected) {
                            Log.d(TAG, "Keyboard already connected on startup, connecting trackpad")
                            bluetoothConnectionManager.connectTrackpad(trackpadMac)
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking initial connection status", e)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Bluetooth connections"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Device Sync")
            .setContentText("Monitoring Bluetooth connections")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
