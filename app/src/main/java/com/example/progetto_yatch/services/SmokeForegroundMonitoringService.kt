package com.example.progetto_yatch.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.progetto_yatch.MainActivity
import com.example.progetto_yatch.R
import com.example.progetto_yatch.utils.NotificationUtils
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*

class SmokeMonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private var monitoringJob: Job? = null

    companion object {
        const val SERVICE_ID = 1001
        const val CHANNEL_ID = "alertify_monitoring_service"
        const val EXTRA_API_URL = "api_url"

        fun start(context: Context, apiUrl: String) {
            val intent = Intent(context, SmokeMonitoringForegroundService::class.java).apply {
                putExtra(EXTRA_API_URL, apiUrl)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmokeMonitoringForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apiUrl = intent?.getStringExtra(EXTRA_API_URL)

        if (apiUrl.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(SERVICE_ID, createNotification())
        startMonitoring(apiUrl)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitoringJob?.cancel()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertify Monitoraggio Attivo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoraggio sensori di sicurezza in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 Alertify - Monitoraggio Attivo")
            .setContentText("Sistema di sicurezza in funzione")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startMonitoring(apiUrl: String) {
        monitoringJob?.cancel()

        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkSensors(apiUrl)
                    delay(15 * 60 * 1000) // 15 minuti
                } catch (e: Exception) {
                    delay(5 * 60 * 1000) // 5 minuti in caso di errore
                }
            }
        }
    }

    private suspend fun checkSensors(apiUrl: String) {
        try {
            val endpoint = "$apiUrl/api/latest"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val latestObj = json["latest"]?.jsonObject

                            if (latestObj != null) {
                                val isAlert = latestObj["is_alert"]?.jsonPrimitive?.boolean ?: false

                                // Aggiorna ultima verifica
                                SmokeMonitoringPreferences.updateLastCheck(this@SmokeMonitoringForegroundService)

                                if (isAlert) {
                                    val sensorValue = latestObj["sensor_value"]?.jsonPrimitive?.double ?: 0.0
                                    val alertStatus = latestObj["alert_status"]?.jsonPrimitive?.int ?: 0
                                    val alertText = latestObj["alert_text"]?.jsonPrimitive?.content ?: "Allarme rilevato"

                                    // Invia notifica di emergenza
                                    NotificationUtils.sendSmokeAlert(
                                        context = this@SmokeMonitoringForegroundService,
                                        sensorValue = sensorValue,
                                        alertStatus = alertStatus,
                                        alertText = alertText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}