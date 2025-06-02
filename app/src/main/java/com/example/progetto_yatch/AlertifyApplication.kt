package com.example.progetto_yatch

import android.app.Application
import android.util.Log
import com.example.progetto_yatch.utils.NotificationUtils
import com.example.progetto_yatch.services.*

class AlertifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inizializza i canali di notifica
        NotificationUtils.createNotificationChannel(this)

        // Avvia automaticamente il monitoraggio se era attivo
        initializeBackgroundMonitoring()

        Log.i("AlertifyApplication", "🚨 Alertify Application inizializzata")
    }

    private fun initializeBackgroundMonitoring() {
        try {
            // Controlla se il monitoraggio unificato è abilitato
            val isUnifiedMonitoringEnabled = UnifiedMonitoringPreferences.isUnifiedMonitoringEnabled(this)

            if (isUnifiedMonitoringEnabled) {
                Log.i("AlertifyApplication", "Avvio monitoraggio unificato (fumo + telecamera)")
                UnifiedMonitoringService.start(this)
            } else {
                // Controlla il monitoraggio legacy solo del fumo
                val isSmokeMonitoringEnabled = SmokeMonitoringPreferences.isMonitoringEnabled(this)
                val apiUrl = SmokeMonitoringPreferences.getApiUrl(this)

                if (isSmokeMonitoringEnabled && !apiUrl.isNullOrEmpty()) {
                    Log.i("AlertifyApplication", "Avvio monitoraggio sensori fumo")
                    SmokeMonitoringWorker.startMonitoring(this, apiUrl)
                    SmokeMonitoringForegroundService.start(this, apiUrl)
                } else {
                    Log.i("AlertifyApplication", "Nessun monitoraggio attivo")
                }
            }

            // Log dello stato dei servizi
            logMonitoringStatus()

        } catch (e: Exception) {
            Log.e("AlertifyApplication", "Errore inizializzazione monitoraggio", e)
        }
    }

    private fun logMonitoringStatus() {
        try {
            val unifiedEnabled = UnifiedMonitoringPreferences.isUnifiedMonitoringEnabled(this)
            val smokeEnabled = SmokeMonitoringPreferences.isMonitoringEnabled(this)
            val smokeUrl = SmokeMonitoringPreferences.getApiUrl(this)

            Log.i("AlertifyApplication", """
                📊 STATO MONITORAGGIO ALERTIFY:
                ├─ Monitoraggio Unificato: ${if (unifiedEnabled) "✅ ATTIVO" else "❌ DISATTIVO"}
                ├─ Monitoraggio Solo Fumo: ${if (smokeEnabled) "✅ ATTIVO" else "❌ DISATTIVO"}
                ├─ URL Sensori Fumo: ${smokeUrl ?: "Non configurato"}
                ├─ URL Telecamera: worm-shining-accurately.ngrok-free.app (fisso)
                └─ Notifiche: ${if (NotificationUtils.areNotificationsEnabled(this)) "✅ ABILITATE" else "❌ DISABILITATE"}
            """.trimIndent())

            // Statistiche degli allarmi
            val smokeAlerts = UnifiedMonitoringPreferences.getSmokeAlertsCount(this)
            val cameraAlerts = UnifiedMonitoringPreferences.getCameraAlertsCount(this)
            val lastSmokeCheck = UnifiedMonitoringPreferences.getLastSmokeCheck(this)
            val lastCameraCheck = UnifiedMonitoringPreferences.getLastCameraCheck(this)

            if (smokeAlerts > 0 || cameraAlerts > 0) {
                Log.w("AlertifyApplication", """
                    🚨 STATISTICHE ALLARMI:
                    ├─ Allarmi Fumo Totali: $smokeAlerts
                    ├─ Allarmi Telecamera Totali: $cameraAlerts
                    ├─ Ultimo Controllo Fumo: ${if (lastSmokeCheck > 0) formatTimestamp(lastSmokeCheck) else "Mai"}
                    └─ Ultimo Controllo Telecamera: ${if (lastCameraCheck > 0) formatTimestamp(lastCameraCheck) else "Mai"}
                """.trimIndent())
            }

        } catch (e: Exception) {
            Log.e("AlertifyApplication", "Errore logging stato monitoraggio", e)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60 * 1000 -> "Meno di 1 minuto fa"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minuti fa"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} ore fa"
            else -> "${diff / (24 * 60 * 60 * 1000)} giorni fa"
        }
    }

    companion object {
        // Versione dell'applicazione per debug
        const val APP_VERSION = "1.0.0"
        const val APP_BUILD = "2025.06.01"

        // URLs dei servizi (centralizzati qui)
        const val SMOKE_SENSOR_URL = "https://game-romantic-gnat.ngrok-free.app"
        const val CAMERA_SYSTEM_URL = "https://worm-shining-accurately.ngrok-free.app"

        fun getAppInfo(): String {
            return "Alertify v$APP_VERSION (Build $APP_BUILD)"
        }
    }
}