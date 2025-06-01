package com.example.progetto_yatch.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Received intent: ${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                Log.d("BootReceiver", "Boot completed, checking monitoring preferences...")

                val isMonitoringEnabled = SmokeMonitoringPreferences.isMonitoringEnabled(context)
                val apiUrl = SmokeMonitoringPreferences.getApiUrl(context)

                Log.d("BootReceiver", "Monitoring enabled: $isMonitoringEnabled, API URL: $apiUrl")

                if (isMonitoringEnabled && !apiUrl.isNullOrEmpty()) {
                    Log.d("BootReceiver", "Starting smoke monitoring service...")
                    SmokeMonitoringWorker.startMonitoring(context, apiUrl)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error restarting monitoring service", e)
            }
        }
    }
}