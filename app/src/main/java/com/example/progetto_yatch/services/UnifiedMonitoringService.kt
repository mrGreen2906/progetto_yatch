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

class UnifiedMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()
    private var monitoringJob: Job? = null
    private var lastSmokeCheck = 0L
    private var lastCameraCheck = 0L

    companion object {
        const val SERVICE_ID = 1002
        const val CHANNEL_ID = "alertify_unified_monitoring"

        // URLs fissi
        private const val SMOKE_BASE_URL = "https://game-romantic-gnat.ngrok-free.app"
        private const val CAMERA_BASE_URL = "https://worm-shining-accurately.ngrok-free.app"

        // Intervalli di controllo
        private const val SMOKE_CHECK_INTERVAL = 15 * 60 * 1000L // 15 minuti
        private const val CAMERA_CHECK_INTERVAL = 10 * 60 * 1000L // 10 minuti

        fun start(context: Context) {
            val intent = Intent(context, UnifiedMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UnifiedMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_ID, createNotification())
        startUnifiedMonitoring()
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
                "Alertify Monitoraggio Completo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoraggio sensori fumo e telecamera in background"
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
            .setContentText("Sensori fumo e telecamera sotto controllo")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startUnifiedMonitoring() {
        monitoringJob?.cancel()

        monitoringJob = serviceScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()

                try {
                    // Controllo sensori fumo ogni 15 minuti
                    if (currentTime - lastSmokeCheck >= SMOKE_CHECK_INTERVAL) {
                        checkSmokeDetectors()
                        lastSmokeCheck = currentTime
                    }

                    // Controllo telecamera ogni 10 minuti
                    if (currentTime - lastCameraCheck >= CAMERA_CHECK_INTERVAL) {
                        checkCameraSecurity()
                        lastCameraCheck = currentTime
                    }

                    // Aggiorna notifica con stato
                    updateNotificationWithStatus()

                } catch (e: Exception) {
                    android.util.Log.e("UnifiedMonitoring", "Errore monitoraggio", e)
                }

                // Attendi 1 minuto prima del prossimo ciclo
                delay(60 * 1000)
            }
        }
    }

    private suspend fun checkSmokeDetectors() {
        try {
            val endpoint = "$SMOKE_BASE_URL/api/latest"
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

                                if (isAlert) {
                                    val sensorValue = latestObj["sensor_value"]?.jsonPrimitive?.double ?: 0.0
                                    val alertStatus = latestObj["alert_status"]?.jsonPrimitive?.int ?: 0
                                    val alertText = latestObj["alert_text"]?.jsonPrimitive?.content ?: "Allarme rilevato"

                                    // Invia notifica di emergenza fumo
                                    NotificationUtils.sendSmokeAlert(
                                        context = this@UnifiedMonitoringService,
                                        sensorValue = sensorValue,
                                        alertStatus = alertStatus,
                                        alertText = alertText
                                    )

                                    android.util.Log.w("UnifiedMonitoring", "🚨 ALLARME FUMO: $alertText")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UnifiedMonitoring", "Errore controllo fumo", e)
        }
    }

    private suspend fun checkCameraSecurity() {
        try {
            val endpoint = "$CAMERA_BASE_URL/api/security/alerts"
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

                            if (json["success"]?.jsonPrimitive?.boolean == true) {
                                val alertsArray = json["alerts"]?.jsonArray ?: JsonArray(emptyList())

                                // Controlla se ci sono nuovi allarmi intrusi
                                val intruderAlerts = alertsArray.filter { alertElement ->
                                    val alert = alertElement.jsonObject
                                    alert["type"]?.jsonPrimitive?.content == "INTRUSO_RILEVATO"
                                }

                                if (intruderAlerts.isNotEmpty()) {
                                    val alertCount = intruderAlerts.size
                                    val latestAlert = intruderAlerts.first().jsonObject
                                    val message = latestAlert["message"]?.jsonPrimitive?.content ?: "Intruso rilevato"
                                    val area = latestAlert["area"]?.jsonPrimitive?.content ?: "Area sconosciuta"

                                    // Invia notifica di emergenza telecamera
                                    NotificationUtils.sendCameraAlert(
                                        context = this@UnifiedMonitoringService,
                                        alertType = "🚨 $alertCount Intrusi Rilevati",
                                        message = "$message in $area"
                                    )

                                    android.util.Log.w("UnifiedMonitoring", "🚨 ALLARME INTRUSI: $alertCount intrusi in $area")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UnifiedMonitoring", "Errore controllo telecamera", e)
        }
    }

    private fun updateNotificationWithStatus() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Calcola tempo prossimo controllo
            val currentTime = System.currentTimeMillis()
            val nextSmokeCheck = (SMOKE_CHECK_INTERVAL - (currentTime - lastSmokeCheck)) / 1000 / 60
            val nextCameraCheck = (CAMERA_CHECK_INTERVAL - (currentTime - lastCameraCheck)) / 1000 / 60

            val statusText = "Prossimi controlli: Fumo ${nextSmokeCheck}min, Camera ${nextCameraCheck}min"

            val updatedNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 Alertify - Monitoraggio Attivo")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            notificationManager.notify(SERVICE_ID, updatedNotification)
        } catch (e: Exception) {
            android.util.Log.e("UnifiedMonitoring", "Errore aggiornamento notifica", e)
        }
    }
}

// Estensione delle preferenze per il monitoraggio unificato
object UnifiedMonitoringPreferences {
    private const val PREFS_NAME = "alertify_unified_monitoring"
    private const val KEY_UNIFIED_MONITORING_ENABLED = "unified_monitoring_enabled"
    private const val KEY_LAST_SMOKE_CHECK = "last_smoke_check"
    private const val KEY_LAST_CAMERA_CHECK = "last_camera_check"
    private const val KEY_SMOKE_ALERTS_COUNT = "smoke_alerts_count"
    private const val KEY_CAMERA_ALERTS_COUNT = "camera_alerts_count"

    fun setUnifiedMonitoringEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_UNIFIED_MONITORING_ENABLED, enabled).apply()

        if (enabled) {
            // Avvia monitoraggio unificato
            UnifiedMonitoringService.start(context)

            // Avvia anche il monitoraggio specifico del fumo se configurato
            try {
                val smokeUrl = SmokeMonitoringPreferences.getApiUrl(context)
                if (!smokeUrl.isNullOrEmpty()) {
                    SmokeMonitoringWorker.startMonitoring(context, smokeUrl)
                }
            } catch (e: Exception) {
                // Ignora se non configurato
            }
        } else {
            // Ferma tutti i servizi
            UnifiedMonitoringService.stop(context)
            SmokeMonitoringWorker.stopMonitoring(context)
            SmokeMonitoringForegroundService.stop(context)
        }
    }

    fun isUnifiedMonitoringEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_UNIFIED_MONITORING_ENABLED, false)
    }

    fun updateLastSmokeCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SMOKE_CHECK, System.currentTimeMillis()).apply()
    }

    fun updateLastCameraCheck(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CAMERA_CHECK, System.currentTimeMillis()).apply()
    }

    fun getLastSmokeCheck(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SMOKE_CHECK, 0)
    }

    fun getLastCameraCheck(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_CAMERA_CHECK, 0)
    }

    fun incrementSmokeAlerts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_SMOKE_ALERTS_COUNT, 0)
        prefs.edit().putInt(KEY_SMOKE_ALERTS_COUNT, current + 1).apply()
    }

    fun incrementCameraAlerts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_CAMERA_ALERTS_COUNT, 0)
        prefs.edit().putInt(KEY_CAMERA_ALERTS_COUNT, current + 1).apply()
    }

    fun getSmokeAlertsCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SMOKE_ALERTS_COUNT, 0)
    }

    fun getCameraAlertsCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CAMERA_ALERTS_COUNT, 0)
    }

    fun resetAlertsCounts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_SMOKE_ALERTS_COUNT, 0)
            .putInt(KEY_CAMERA_ALERTS_COUNT, 0)
            .apply()
    }
}