package com.duchastel.simon.devicesync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private lateinit var prefs: android.content.SharedPreferences
    private var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("device_sync_prefs", MODE_PRIVATE)

        // Initialize permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            // Handle permission results if needed
        }

        setContent {
            DeviceSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        prefs = prefs,
                        requiredPermissions = requiredPermissions,
                        onRequestPermissions = { 
                            permissionLauncher?.launch(it)
                        },
                        onCheckPermissions = { checkPermissions() },
                        onCheckBatteryOptimization = { checkBatteryOptimization(this) }
                    )
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun MainScreen(
    prefs: android.content.SharedPreferences,
    requiredPermissions: Array<String>,
    onRequestPermissions: (Array<String>) -> Unit,
    onCheckPermissions: () -> Boolean,
    onCheckBatteryOptimization: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var keyboardMac by remember { mutableStateOf(prefs.getString("keyboard_mac", "") ?: "") }
    var trackpadMac by remember { mutableStateOf(prefs.getString("trackpad_mac", "") ?: "") }
    var isRunning by remember { mutableStateOf(BluetoothMonitorService.isRunning) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Update status when resumed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isRunning = BluetoothMonitorService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Device Sync",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Keyboard MAC input
        OutlinedTextField(
            value = keyboardMac,
            onValueChange = { 
                keyboardMac = it.uppercase().replace(Regex("[^0-9A-F:]"), "")
                if (it.length > keyboardMac.length) {
                    errorMessage = "Only hex digits and colons allowed"
                } else {
                    errorMessage = null
                }
            },
            label = { Text("Keyboard MAC Address") },
            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null && keyboardMac.isNotEmpty() && !isValidMac(keyboardMac),
            supportingText = {
                if (keyboardMac.isNotEmpty() && !isValidMac(keyboardMac)) {
                    Text("Invalid MAC address format")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Trackpad MAC input
        OutlinedTextField(
            value = trackpadMac,
            onValueChange = { 
                trackpadMac = it.uppercase().replace(Regex("[^0-9A-F:]"), "")
                if (it.length > trackpadMac.length) {
                    errorMessage = "Only hex digits and colons allowed"
                } else {
                    errorMessage = null
                }
            },
            label = { Text("Trackpad MAC Address") },
            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null && trackpadMac.isNotEmpty() && !isValidMac(trackpadMac),
            supportingText = {
                if (trackpadMac.isNotEmpty() && !isValidMac(trackpadMac)) {
                    Text("Invalid MAC address format")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Status text
        Text(
            text = if (isRunning) "Status: Running" else "Status: Stopped",
            style = MaterialTheme.typography.titleMedium,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    if (!onCheckPermissions()) {
                        val missingPerms = requiredPermissions.filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }.toTypedArray()
                        onRequestPermissions(missingPerms)
                        return@Button
                    }
                    
                    if (!isValidMac(keyboardMac) || !isValidMac(trackpadMac)) {
                        errorMessage = "Please enter valid MAC addresses"
                        return@Button
                    }

                    // Save MAC addresses
                    prefs.edit().apply {
                        putString("keyboard_mac", keyboardMac)
                        putString("trackpad_mac", trackpadMac)
                        apply()
                    }

                    // Check battery optimization
                    onCheckBatteryOptimization()

                    // Start the service
                    val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
                        putExtra("keyboard_mac", keyboardMac)
                        putExtra("trackpad_mac", trackpadMac)
                    }

                    context.startForegroundService(serviceIntent)
                    isRunning = true
                },
                enabled = !isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }

            Button(
                onClick = {
                    val serviceIntent = Intent(context, BluetoothMonitorService::class.java)
                    context.stopService(serviceIntent)
                    isRunning = false
                },
                enabled = isRunning,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Instructions
        Text(
            text = "Run setup.sh via ADB to grant permissions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun isValidMac(mac: String): Boolean {
    return mac.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$"))
}

private fun checkBatteryOptimization(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    }
}

@Composable
fun DeviceSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
