package com.example.progetto_yatch.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.progetto_yatch.MainActivity
import com.example.progetto_yatch.R

object NotificationUtils {
    private const val CHANNEL_ID = "alertify_security_alerts"
    private const val SMOKE_CHANNEL_ID = "alertify_smoke_alerts"
    private const val CHANNEL_NAME = "Alertify Security Alerts"
    private const val SMOKE_CHANNEL_NAME = "Alertify Smoke Detection"
    private const val CHANNEL_DESCRIPTION = "Notifiche per allarmi del sistema di sicurezza Alertify"
    private const val SMOKE_CHANNEL_DESCRIPTION = "Notifiche per rilevamento fumo e gas"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canale per allarmi generali di sicurezza
            val securityChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }

            // Canale specifico per allarmi fumo (priorità massima)
            val smokeChannel = NotificationChannel(SMOKE_CHANNEL_ID, SMOKE_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = SMOKE_CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true) // Bypassa modalità non disturbare
            }

            notificationManager.createNotificationChannel(securityChannel)
            notificationManager.createNotificationChannel(smokeChannel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 13, le notifiche erano abilitate per default
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendSensorAlert(context: Context, sensorType: String, value: String, threshold: String) {
        // Controllo esplicito dei permessi
        if (!hasNotificationPermission(context)) {
            return // Non inviare notifiche se il permesso non è concesso
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🚨 Allarme Sicurezza Alertify")
                .setContentText("$sensorType: $value (soglia: $threshold)")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("ATTENZIONE: Il sensore $sensorType ha rilevato un valore anomalo.\n\nValore attuale: $value\nSoglia sicurezza: $threshold\n\nTocca per aprire Alertify e verificare.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 1000, 500, 1000))
                .setLights(0xFFFF0000.toInt(), 1000, 500)
                .build()

            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), notification)
            }
        } catch (e: SecurityException) {
            // Gestisci l'eccezione se i permessi vengono revocati durante l'esecuzione
            e.printStackTrace()
        }
    }

    fun sendSmokeAlert(context: Context, sensorValue: Double, alertStatus: Int, alertText: String) {
        // Controllo esplicito dei permessi
        if (!hasNotificationPermission(context)) {
            return
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, SMOKE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🔥 ALLARME FUMO - Alertify")
                .setContentText("$alertText - Valore: ${sensorValue.toInt()}")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("🚨 RILEVAMENTO FUMO/GAS:\n\n$alertText\n\nValore sensore: ${sensorValue.toInt()}\nStato allarme: $alertStatus\n\nAPRI SUBITO ALERTIFY per verificare la situazione!")
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setLights(0xFFFF0000.toInt(), 500, 500)
                .setFullScreenIntent(pendingIntent, true) // Mostra a schermo intero se possibile
                .build()

            with(NotificationManagerCompat.from(context)) {
                notify(9999, notification) // ID fisso per allarmi fumo
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun sendCameraAlert(context: Context, alertType: String, message: String) {
        // Controllo esplicito dei permessi
        if (!hasNotificationPermission(context)) {
            return // Non inviare notifiche se il permesso non è concesso
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("📹 Allarme Telecamera - Alertify")
                .setContentText("$alertType: $message")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("RILEVAMENTO TELECAMERA:\n\n$alertType\n$message\n\nTocca per visualizzare le immagini in Alertify.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), notification)
            }
        } catch (e: SecurityException) {
            // Gestisci l'eccezione se i permessi vengono revocati durante l'esecuzione
            e.printStackTrace()
        }
    }

    // Funzione utility per verificare se le notifiche sono abilitate
    fun areNotificationsEnabled(context: Context): Boolean {
        return hasNotificationPermission(context)
    }

    // Funzione per cancellare tutte le notifiche di allarme fumo
    fun clearSmokeAlerts(context: Context) {
        try {
            with(NotificationManagerCompat.from(context)) {
                cancel(9999) // Cancella l'allarme fumo fisso
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}