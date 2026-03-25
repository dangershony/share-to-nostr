package com.sharetonostr.util

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Collects recent logcat output filtered to this app's process.
 */
object LogCollector {

    /**
     * Clear the entire logcat buffer.
     */
    fun clearLogs() {
        Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
    }

    /**
     * Capture the last [lines] log lines from this app's process.
     * Filters to common app tags plus any warnings/errors.
     *
     * This is a suspend function that runs the blocking logcat I/O on [Dispatchers.IO]
     * so it is safe to call from a main-thread coroutine without blocking the UI.
     */
    suspend fun collectLogs(lines: Int = 500): String = withContext(Dispatchers.IO) {
        try {
            val pid = android.os.Process.myPid()
            // Get recent logs from this process only
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", "$lines")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logs = reader.readText()
            reader.close()
            process.waitFor()

            buildString {
                appendLine("=== Share to Nostr - Debug Logs ===")
                appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App PID: $pid")
                appendLine("===================================")
                appendLine()
                append(logs)
            }
        } catch (e: Exception) {
            "Failed to collect logs: ${e.message}"
        }
    }
}
