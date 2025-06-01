package com.example.progetto_yatch

import android.app.Application
import com.example.progetto_yatch.utils.NotificationUtils
import com.example.progetto_yatch.services.SmokeMonitoringPreferences
import com.example.progetto_yatch.services.SmokeMonitoringWorker
import com.example.progetto_yatch.services.SmokeMonitoringForegroundService

class AlertifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inizializza i canali di notifica
        NotificationUtils.createNotificationChannel(this)

        // Avvia automaticamente il monitoraggio se era attivo
        initializeBackgroundMonitoring()
    }

    private fun initializeBackgroundMonitoring() {
        try {
            // Controlla se il monitoraggio era abilitato
            val isMonitoringEnabled = SmokeMonitoringPreferences.isMonitoringEnabled(this)
            val apiUrl = SmokeMonitoringPreferences.getApiUrl(this)

            if (isMonitoringEnabled && !apiUrl.isNullOrEmpty()) {
                // Avvia entrambi i servizi per massima affidabilità
                SmokeMonitoringWorker.startMonitoring(this, apiUrl)
                SmokeMonitoringForegroundService.start(this, apiUrl)
            }
        } catch (e: Exception) {
            // Se ci sono errori nelle preferenze, ignora per ora
            e.printStackTrace()
        }
    }
}