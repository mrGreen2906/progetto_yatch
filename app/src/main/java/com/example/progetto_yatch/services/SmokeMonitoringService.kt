package com.example.progetto_yatch.services

import android.content.Context
import androidx.work.*
import com.example.progetto_yatch.utils.NotificationUtils
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

// Worker per il monitoraggio automatico dei sensori di fumo
class SmokeMonitoringWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val apiUrl = inputData.getString("api_url") ?: return Result.failure()
        val endpoint = "$apiUrl/api/latest"

        return try {
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject
                    val latestObj = json["latest"]?.jsonObject

                    if (latestObj != null) {
                        val isAlert = latestObj["is_alert"]?.jsonPrimitive?.boolean ?: false

                        if (isAlert) {
                            val sensorValue = latestObj["sensor_value"]?.jsonPrimitive?.double ?: 0.0
                            val alertStatus = latestObj["alert_status"]?.jsonPrimitive?.int ?: 0
                            val alertText = latestObj["alert_text"]?.jsonPrimitive?.content ?: "Allarme rilevato"

                            // Invia notifica di emergenza
                            NotificationUtils.sendSmokeAlert(
                                context = applicationContext,
                                sensorValue = sensorValue,
                                alertStatus = alertStatus,
                                alertText = alertText
                            )
                        }
                    }
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "alertify_smoke_monitoring"

        fun startMonitoring(context: Context, apiUrl: String) {
            val inputData = Data.Builder()
                .putString("api_url", apiUrl)
                .build()

            val smokeMonitoringRequest = PeriodicWorkRequestBuilder<SmokeMonitoringWorker>(
                repeatInterval = 15, // Controlla ogni 15 minuti
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    smokeMonitoringRequest
                )
        }

        fun stopMonitoring(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
}

// Utilità per gestire le preferenze di monitoraggio
object SmokeMonitoringPreferences {
    private const val PREFS_NAME = "alertify_smoke_monitoring"
    private const val KEY_API_URL = "api_url"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_LAST_CHECK = "last_check"

    fun saveApiUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_URL, url).apply()
    }

    fun getApiUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_URL, null)
    }

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()

        if (enabled) {
            getApiUrl(context)?.let { url ->
                // Avvia sia WorkManager che Foreground Service per massima affidabilità
                SmokeMonitoringWorker.startMonitoring(context, url)
                SmokeMonitoringForegroundService.start(context, url)
            }
        } else {
            // Ferma entrambi i servizi
            SmokeMonitoringWorker.stopMonitoring(context)
            SmokeMonitoringForegroundService.stop(context)
        }
    }

    fun isMonitoringEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    fun updateLastCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun getLastCheck(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_CHECK, 0)
    }
}