package com.duchastel.simon.devicesync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

data class LogEntry(
    val timestamp: Date,
    val message: String
) {
    val formattedString: String
        get() {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return "[${dateFormat.format(timestamp)}] $message"
        }
}

class Logger private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "DeviceSyncLogger"
        private const val MAX_LOGS = 1000
        private const val PURGE_AGE_MS = 300000L // 5 minutes
        private const val CHECK_INTERVAL_MS = 30000L // 30 seconds

        @Volatile
        private var instance: Logger? = null

        fun getInstance(context: Context): Logger {
            return instance ?: synchronized(this) {
                instance ?: Logger(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logEntries = mutableListOf<LogEntry>()
    private val _logsFlow = MutableStateFlow("")
    val logsFlow: StateFlow<String> = _logsFlow.asStateFlow()

    private val logFile: File = File(appContext.filesDir, "debug.log")
    private var autoClearTimer: Timer? = null
    private var hasPurgedOldLogs = false

    var autoClearEnabled: Boolean
        get() = appContext.getSharedPreferences("device_sync_prefs", Context.MODE_PRIVATE)
            .getBoolean("auto_clear_logs", true)
        set(value) {
            appContext.getSharedPreferences("device_sync_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("auto_clear_logs", value)
                .apply()
            if (value) {
                startAutoClearTimer()
            } else {
                stopAutoClearTimer()
            }
        }

    init {
        log("Logger initialized")
        if (autoClearEnabled) {
            startAutoClearTimer()
        }
    }

    fun log(message: String) {
        val entry = LogEntry(timestamp = Date(), message = message)

        // Write to file first
        writeToFile(entry)

        // Update in-memory logs on main thread
        synchronized(logEntries) {
            logEntries.add(entry)
            if (logEntries.size > MAX_LOGS) {
                val excess = logEntries.size - MAX_LOGS
                logEntries.removeAll(logEntries.take(excess))
                hasPurgedOldLogs = true
            }
            updateLogsFlow()
        }

        Log.d(TAG, entry.formattedString)
    }

    private fun writeToFile(entry: LogEntry) {
        try {
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(entry.formattedString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    private fun updateLogsFlow() {
        val logs = synchronized(logEntries) {
            val header = if (hasPurgedOldLogs && logEntries.isNotEmpty()) {
                "[Older logs deleted]\n"
            } else {
                ""
            }
            header + logEntries.joinToString("\n") { it.formattedString }
        }
        _logsFlow.value = logs
    }

    fun clear() {
        synchronized(logEntries) {
            logEntries.clear()
            hasPurgedOldLogs = false
            updateLogsFlow()
        }
        log("Logs cleared")
    }

    private fun purgeOldLogs() {
        val cutoffTime = System.currentTimeMillis() - PURGE_AGE_MS
        val beforeCount = logEntries.size

        synchronized(logEntries) {
            logEntries.removeAll { it.timestamp.time < cutoffTime }
            if (logEntries.size < beforeCount) {
                hasPurgedOldLogs = true
                updateLogsFlow()
            }
        }
    }

    private fun startAutoClearTimer() {
        stopAutoClearTimer()
        autoClearTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    purgeOldLogs()
                }
            }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS)
        }
    }

    private fun stopAutoClearTimer() {
        autoClearTimer?.cancel()
        autoClearTimer = null
    }

    fun getAllLogs(): String {
        purgeOldLogs()
        return _logsFlow.value
    }
}
