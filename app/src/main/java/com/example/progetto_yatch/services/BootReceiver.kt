package com.example.progetto_yatch.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                handleBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (intent.dataString?.contains(context.packageName) == true) {
                    handleAppUpdated(context)
                }
            }
        }
    }

    private fun handleBootCompleted(context: Context) {
        try {
            Log.d("BootReceiver", "🔄 Dispositivo riavviato, ripristino servizi Alertify...")

            // Controlla prima il monitoraggio unificato
            val isUnifiedMonitoringEnabled = UnifiedMonitoringPreferences.isUnifiedMonitoringEnabled(context)

            if (isUnifiedMonitoringEnabled) {
                Log.d("BootReceiver", "Avvio monitoraggio unificato (fumo + telecamera)")
                UnifiedMonitoringService.start(context)

                // Log stato ripristino
                Log.i("BootReceiver", """
                    ✅ MONITORAGGIO UNIFICATO RIPRISTINATO:
                    ├─ Sensori Fumo: game-romantic-gnat.ngrok-free.app
                    ├─ Sistema Telecamera: worm-shining-accurately.ngrok-free.app
                    ├─ Intervallo Controllo Fumo: 15 minuti
                    ├─ Intervallo Controllo Telecamera: 10 minuti
                    └─ Notifiche: Abilitate
                """.trimIndent())

            } else {
                // Fallback al monitoraggio legacy solo fumo
                val isSmokeMonitoringEnabled = SmokeMonitoringPreferences.isMonitoringEnabled(context)
                val apiUrl = SmokeMonitoringPreferences.getApiUrl(context)

                Log.d("BootReceiver", "Monitoraggio legacy - Enabled: $isSmokeMonitoringEnabled, URL: $apiUrl")

                if (isSmokeMonitoringEnabled && !apiUrl.isNullOrEmpty()) {
                    Log.d("BootReceiver", "Avvio monitoraggio sensori fumo legacy")
                    SmokeMonitoringWorker.startMonitoring(context, apiUrl)

                    Log.i("BootReceiver", """
                        ✅ MONITORAGGIO FUMO LEGACY RIPRISTINATO:
                        ├─ URL API: $apiUrl
                        ├─ Intervallo: 15 minuti
                        └─ Servizio: WorkManager + Foreground
                    """.trimIndent())
                } else {
                    Log.w("BootReceiver", "⚠️ Nessun monitoraggio configurato da ripristinare")
                }
            }

            // Statistiche rapide
            logQuickStats(context)

        } catch (e: Exception) {
            Log.e("BootReceiver", "❌ Errore ripristino servizi di monitoraggio", e)
        }
    }

    private fun handleAppUpdated(context: Context) {
        try {
            Log.d("BootReceiver", "📱 App Alertify aggiornata, riavvio servizi...")

            // Riavvia tutti i servizi attivi dopo un aggiornamento
            val isUnifiedEnabled = UnifiedMonitoringPreferences.isUnifiedMonitoringEnabled(context)
            val isSmokeEnabled = SmokeMonitoringPreferences.isMonitoringEnabled(context)

            if (isUnifiedEnabled) {
                Log.d("BootReceiver", "Riavvio monitoraggio unificato post-aggiornamento")
                UnifiedMonitoringService.stop(context)
                Thread.sleep(1000) // Breve attesa
                UnifiedMonitoringService.start(context)
            } else if (isSmokeEnabled) {
                val apiUrl = SmokeMonitoringPreferences.getApiUrl(context)
                if (!apiUrl.isNullOrEmpty()) {
                    Log.d("BootReceiver", "Riavvio monitoraggio fumo post-aggiornamento")
                    SmokeMonitoringWorker.stopMonitoring(context)
                    Thread.sleep(1000) // Breve attesa
                    SmokeMonitoringWorker.startMonitoring(context, apiUrl)
                }
            }

            Log.i("BootReceiver", "✅ Servizi riavviati dopo aggiornamento app")

        } catch (e: Exception) {
            Log.e("BootReceiver", "❌ Errore riavvio servizi post-aggiornamento", e)
        }
    }

    private fun logQuickStats(context: Context) {
        try {
            val smokeAlerts = UnifiedMonitoringPreferences.getSmokeAlertsCount(context)
            val cameraAlerts = UnifiedMonitoringPreferences.getCameraAlertsCount(context)
            val lastSmokeCheck = UnifiedMonitoringPreferences.getLastSmokeCheck(context)
            val lastCameraCheck = UnifiedMonitoringPreferences.getLastCameraCheck(context)

            if (smokeAlerts > 0 || cameraAlerts > 0) {
                Log.i("BootReceiver", """
                    📊 STATISTICHE RAPIDE:
                    ├─ Allarmi Fumo: $smokeAlerts
                    ├─ Allarmi Telecamera: $cameraAlerts
                    ├─ Ultimo Check Fumo: ${if (lastSmokeCheck > 0) "✅" else "❌"}
                    └─ Ultimo Check Telecamera: ${if (lastCameraCheck > 0) "✅" else "❌"}
                """.trimIndent())
            } else {
                Log.i("BootReceiver", "📊 Nessun allarme registrato - Sistema pulito")
            }

        } catch (e: Exception) {
            Log.e("BootReceiver", "Errore logging statistiche", e)
        }
    }
}