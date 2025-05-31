package com.example.progetto_yatch.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.progetto_yatch.utils.NotificationUtils
import kotlinx.coroutines.delay
import kotlin.math.abs

// Data classes specifiche per la HomeScreen
data class YachtSensorData(
    val temperature: Float = 22.5f,
    val humidity: Float = 65.2f,
    val gasLevel: Float = 0.02f,
    val smokeLevel: Float = 0.01f,
    val isAlert: Boolean = false,
    val lastUpdate: String = "Ora"
)

data class YachtCameraData(
    val isOnline: Boolean = true,
    val recordingStatus: String = "Attiva",
    val lastMotion: String = "5 minuti fa",
    val detectedPeople: Int = 0,
    val motionSensitivity: Int = 75,
    val nightVision: Boolean = true
)

data class HistoryRecord(
    val timestamp: String,
    val temperature: Float,
    val humidity: Float,
    val gasLevel: Float,
    val isAlert: Boolean
)

data class CameraRecording(
    val id: String,
    val timestamp: String,
    val duration: String,
    val hasMotion: Boolean,
    val thumbnailPath: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSecurity: () -> Unit
) {
    val context = LocalContext.current

    // Stati per popup
    var showSensorPopup by remember { mutableStateOf(false) }
    var showCameraPopup by remember { mutableStateOf(false) }

    // Dati simulati
    var sensorData by remember { mutableStateOf(YachtSensorData()) }
    var cameraData by remember { mutableStateOf(YachtCameraData()) }
    var sensorHistory by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var cameraRecordings by remember { mutableStateOf<List<CameraRecording>>(emptyList()) }

    // Stati UI per gestione permessi
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var lastAlertTime by remember { mutableStateOf(0L) }

    // Launcher per richiedere permessi
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            showPermissionDialog = true
        }
    }

    // Controlla permessi all'avvio
    LaunchedEffect(Unit) {
        NotificationUtils.createNotificationChannel(context)
        hasNotificationPermission = NotificationUtils.areNotificationsEnabled(context)

        // Genera storico iniziale
        sensorHistory = generateInitialHistory()
        cameraRecordings = generateInitialRecordings()
    }

    // Simulazione aggiornamento dati sensori
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // Aggiorna ogni 2 secondi

            val newGasLevel = (Math.random() * 0.12).toFloat() // 0-120 ppm
            val newTemperature = 18f + (Math.random() * 15).toFloat() // 18-33°C
            val newHumidity = 45f + (Math.random() * 35).toFloat() // 45-80%
            val newSmokeLevel = (Math.random() * 0.08).toFloat()

            sensorData = sensorData.copy(
                temperature = newTemperature,
                humidity = newHumidity,
                gasLevel = newGasLevel,
                smokeLevel = newSmokeLevel,
                lastUpdate = "Ora"
            )

            // Aggiorna storico ogni 10 aggiornamenti
            if ((Math.random() * 10).toInt() == 0) {
                val newRecord = HistoryRecord(
                    timestamp = "${(Math.random() * 60).toInt()} min fa",
                    temperature = newTemperature,
                    humidity = newHumidity,
                    gasLevel = newGasLevel,
                    isAlert = newGasLevel > 0.05f
                )
                sensorHistory = listOf(newRecord) + sensorHistory.take(19) // Mantieni ultimi 20
            }
        }
    }

    // Simulazione aggiornamento telecamera
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000) // Aggiorna ogni 15 secondi

            cameraData = cameraData.copy(
                detectedPeople = (Math.random() * 3).toInt(),
                lastMotion = if (Math.random() > 0.7) "Ora" else cameraData.lastMotion
            )

            // Simula nuove registrazioni occasionalmente
            if (Math.random() > 0.8) {
                val newRecording = CameraRecording(
                    id = "rec_${System.currentTimeMillis()}",
                    timestamp = "Ora",
                    duration = "${(2 + Math.random() * 8).toInt()} min",
                    hasMotion = Math.random() > 0.4
                )
                cameraRecordings = listOf(newRecording) + cameraRecordings.take(9) // Mantieni ultime 10
            }
        }
    }

    // Sistema di allarmi intelligente
    LaunchedEffect(sensorData.gasLevel, sensorData.smokeLevel) {
        val currentTime = System.currentTimeMillis()
        val gasThreshold = 0.05f // 50 ppm
        val smokeThreshold = 0.04f

        val isGasAlert = sensorData.gasLevel > gasThreshold
        val isSmokeAlert = sensorData.smokeLevel > smokeThreshold
        val shouldAlert = (isGasAlert || isSmokeAlert) && !sensorData.isAlert

        if (shouldAlert && (currentTime - lastAlertTime) > 5000) { // Evita spam notifiche
            sensorData = sensorData.copy(isAlert = true)
            lastAlertTime = currentTime

            // Invia notifica se permessi abilitati
            if (hasNotificationPermission && NotificationUtils.areNotificationsEnabled(context)) {
                val alertType = when {
                    isGasAlert && isSmokeAlert -> "Gas e Fumo"
                    isGasAlert -> "Gas"
                    else -> "Fumo"
                }

                NotificationUtils.sendSensorAlert(
                    context = context,
                    sensorType = alertType,
                    value = if (isGasAlert) "${(sensorData.gasLevel * 1000).toInt()} ppm"
                    else "${(sensorData.smokeLevel * 1000).toInt()} ppm",
                    threshold = if (isGasAlert) "${(gasThreshold * 1000).toInt()} ppm"
                    else "${(smokeThreshold * 1000).toInt()} ppm"
                )
            }
        } else if (!isGasAlert && !isSmokeAlert && sensorData.isAlert) {
            sensorData = sensorData.copy(isAlert = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFE2E8F0)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header principale
            YachtHeader(
                sensorData = sensorData,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onNavigateToSecurity = onNavigateToSecurity
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card principale yacht con sensori
            YachtMainCard(
                sensorData = sensorData,
                cameraData = cameraData,
                onSensorClick = { showSensorPopup = true },
                onCameraClick = { showCameraPopup = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Grid status cards
            YachtStatusGrid(sensorData = sensorData, cameraData = cameraData)

            // Card permessi se necessario
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                Spacer(modifier = Modifier.height(16.dp))
                PermissionCard(
                    onRequestPermission = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }

    // Dialog per spiegare l'importanza dei permessi
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permessi Notifiche") },
            text = {
                Text("Le notifiche sono essenziali per ricevere allarmi di sicurezza in tempo reale. Puoi abilitarle dalle impostazioni dell'app.")
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Popup sensore completo
    if (showSensorPopup) {
        YachtSensorPopup(
            sensorData = sensorData,
            history = sensorHistory,
            onDismiss = { showSensorPopup = false }
        )
    }

    // Popup telecamera completo
    if (showCameraPopup) {
        YachtCameraPopup(
            cameraData = cameraData,
            recordings = cameraRecordings,
            onDismiss = { showCameraPopup = false }
        )
    }
}

@Composable
private fun YachtHeader(
    sensorData: YachtSensorData,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToSecurity: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "🚨 Alertify",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A202C)
            )

            Text(
                text = when {
                    sensorData.isAlert -> "⚠️ Attenzione richiesta"
                    !hasNotificationPermission -> "🔔 Abilita notifiche"
                    else -> "✅ Tutto sotto controllo"
                },
                fontSize = 16.sp,
                color = when {
                    sensorData.isAlert -> Color(0xFFE53E3E)
                    !hasNotificationPermission -> Color(0xFFD69E2E)
                    else -> Color(0xFF38A169)
                },
                fontWeight = FontWeight.Medium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Notifiche button
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                IconButton(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFED8936), CircleShape)
                ) {
                    Text("🔔", fontSize = 20.sp, color = Color.White)
                }
            }

            // Settings button
            IconButton(
                onClick = onNavigateToSecurity,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF4A00E0), CircleShape)
            ) {
                Text("⚙️", fontSize = 22.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun YachtMainCard(
    sensorData: YachtSensorData,
    cameraData: YachtCameraData,
    onSensorClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo Alertify principale
                Text(
                    text = "🚨",
                    fontSize = 120.sp,
                    modifier = Modifier.offset(y = 5.dp)
                )

                Text(
                    text = "ALERTIFY SYSTEM",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF718096),
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Sensori interattivi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Sensore Gas/Fumo
                    YachtSensorButton(
                        icon = "🔥",
                        label = "Sensore",
                        sublabel = "Gas/Fumo",
                        isAlert = sensorData.isAlert,
                        value = "${(sensorData.gasLevel * 1000).toInt()}ppm",
                        onClick = onSensorClick
                    )

                    // Telecamera
                    YachtSensorButton(
                        icon = "📹",
                        label = "Camera",
                        sublabel = "Sicurezza",
                        isAlert = false,
                        value = if (cameraData.isOnline) "Online" else "Offline",
                        onClick = onCameraClick
                    )
                }
            }
        }
    }
}

@Composable
private fun YachtSensorButton(
    icon: String,
    label: String,
    sublabel: String,
    isAlert: Boolean,
    value: String,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sensor_animation")

    val alertScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAlert) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_scale"
    )

    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAlert) 0.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(alertScale)
                .background(
                    if (isAlert) Color(0xFFE53E3E).copy(alpha = alertAlpha)
                    else Color(0xFF4A00E0),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 40.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748),
            textAlign = TextAlign.Center
        )

        Text(
            text = sublabel,
            fontSize = 12.sp,
            color = Color(0xFF718096),
            textAlign = TextAlign.Center
        )

        Text(
            text = value,
            fontSize = 11.sp,
            color = if (isAlert) Color(0xFFE53E3E) else Color(0xFF4A00E0),
            fontWeight = if (isAlert) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun YachtStatusGrid(
    sensorData: YachtSensorData,
    cameraData: YachtCameraData
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        YachtStatusCard(
            modifier = Modifier.weight(1f),
            icon = "🌡️",
            title = "Temperatura",
            value = "${sensorData.temperature.toInt()}°C",
            color = when {
                sensorData.temperature > 30 -> Color(0xFFE53E3E)
                sensorData.temperature < 15 -> Color(0xFF3182CE)
                else -> Color(0xFF38A169)
            }
        )

        YachtStatusCard(
            modifier = Modifier.weight(1f),
            icon = "💧",
            title = "Umidità",
            value = "${sensorData.humidity.toInt()}%",
            color = when {
                sensorData.humidity > 70 -> Color(0xFFD69E2E)
                else -> Color(0xFF3182CE)
            }
        )

        YachtStatusCard(
            modifier = Modifier.weight(1f),
            icon = "🔥",
            title = "Gas",
            value = "${(sensorData.gasLevel * 1000).toInt()}ppm",
            color = if (sensorData.isAlert) Color(0xFFE53E3E) else Color(0xFF38A169),
            isAlert = sensorData.isAlert
        )
    }
}

@Composable
private fun YachtStatusCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    value: String,
    color: Color,
    isAlert: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFFFED7D7) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 32.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = Color(0xFF718096),
                fontWeight = FontWeight.Medium
            )

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (isAlert) {
                Text(
                    text = "ALLARME",
                    fontSize = 10.sp,
                    color = Color(0xFFE53E3E),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E6)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRequestPermission() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔔", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Abilita Notifiche Push",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD69E2E)
                )
                Text(
                    text = "Ricevi avvisi istantanei per emergenze, allarmi sensori e attività sospette",
                    fontSize = 13.sp,
                    color = Color(0xFF744210),
                    lineHeight = 18.sp
                )
            }
            Text("→", fontSize = 20.sp, color = Color(0xFFD69E2E))
        }
    }
}

@Composable
private fun YachtSensorPopup(
    sensorData: YachtSensorData,
    history: List<HistoryRecord>,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf("📊 Stato Attuale", "📈 Storico")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .background(Color.White, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (abs(dragAmount) > 80) {
                            currentPage = if (dragAmount > 0) {
                                (currentPage - 1).coerceAtLeast(0)
                            } else {
                                (currentPage + 1).coerceAtMost(pages.size - 1)
                            }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header popup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 Sensore Gas/Fumo",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 20.sp, color = Color(0xFF718096))
                    }
                }

                // Tab indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.forEachIndexed { index, title ->
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable { currentPage = index },
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == currentPage) Color(0xFF4A00E0)
                                else Color(0xFFF7FAFC)
                            )
                        ) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (index == currentPage) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == currentPage) Color.White else Color(0xFF718096),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contenuto pagine
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 0.dp)
                ) {
                    when (currentPage) {
                        0 -> SensorCurrentStatusPage(sensorData)
                        1 -> SensorHistoryPage(history)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorCurrentStatusPage(sensorData: YachtSensorData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Status principale
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (sensorData.isAlert) Color(0xFFE53E3E) else Color(0xFF38A169)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (sensorData.isAlert) "⚠️" else "✅",
                        fontSize = 48.sp
                    )
                    Text(
                        text = if (sensorData.isAlert) "ALLARME ATTIVO" else "AMBIENTE SICURO",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Ultimo aggiornamento: ${sensorData.lastUpdate}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            // Metriche dettagliate
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "📊 Metriche Ambientali",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SensorMetricRow(
                        icon = "🌡️",
                        label = "Temperatura",
                        value = "${sensorData.temperature.toInt()}°C",
                        isNormal = sensorData.temperature in 15f..30f
                    )

                    SensorMetricRow(
                        icon = "💧",
                        label = "Umidità Relativa",
                        value = "${sensorData.humidity.toInt()}%",
                        isNormal = sensorData.humidity <= 70f
                    )

                    SensorMetricRow(
                        icon = "☁️",
                        label = "Livello Gas",
                        value = "${(sensorData.gasLevel * 1000).toInt()} ppm",
                        isNormal = sensorData.gasLevel <= 0.05f
                    )

                    SensorMetricRow(
                        icon = "🌫️",
                        label = "Livello Fumo",
                        value = "${(sensorData.smokeLevel * 1000).toInt()} ppm",
                        isNormal = sensorData.smokeLevel <= 0.04f
                    )
                }
            }
        }

        if (sensorData.isAlert) {
            item {
                // Alert dettagliato
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFED7D7))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚨", fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Condizioni Anomale Rilevate",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53E3E)
                            )
                            Text(
                                text = buildString {
                                    if (sensorData.gasLevel > 0.05f) {
                                        append("Gas: ${(sensorData.gasLevel * 1000).toInt()} ppm (soglia: 50 ppm)")
                                    }
                                    if (sensorData.smokeLevel > 0.04f) {
                                        if (isNotEmpty()) append(" • ")
                                        append("Fumo: ${(sensorData.smokeLevel * 1000).toInt()} ppm (soglia: 40 ppm)")
                                    }
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF744210)
                            )
                        }
                    }
                }
            }
        }

        item {
            // Azioni rapide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A00E0))
                ) {
                    Text("📱 Dettagli", color = Color.White)
                }

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⚙️ Configura")
                }
            }
        }
    }
}

@Composable
private fun SensorHistoryPage(history: List<HistoryRecord>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "📈 Storico Rilevamenti",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📭", fontSize = 48.sp)
                        Text(
                            text = "Nessun dato storico disponibile",
                            fontSize = 16.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(history) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (record.isAlert) Color(0xFFFED7D7) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = record.timestamp,
                                fontSize = 12.sp,
                                color = Color(0xFF718096)
                            )
                            Text(
                                text = "${record.temperature.toInt()}°C • ${record.humidity.toInt()}% • ${(record.gasLevel * 1000).toInt()}ppm",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2D3748)
                            )
                        }
                        Text(
                            text = if (record.isAlert) "⚠️" else "✅",
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorMetricRow(
    icon: String,
    label: String,
    value: String,
    isNormal: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color(0xFF718096)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNormal) Color(0xFF38A169) else Color(0xFFE53E3E)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isNormal) "✅" else "⚠️",
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun YachtCameraPopup(
    cameraData: YachtCameraData,
    recordings: List<CameraRecording>,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf("📹 Live View", "📼 Registrazioni")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
                .background(Color.White, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (abs(dragAmount) > 80) {
                            currentPage = if (dragAmount > 0) {
                                (currentPage - 1).coerceAtLeast(0)
                            } else {
                                (currentPage + 1).coerceAtMost(pages.size - 1)
                            }
                        }
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header popup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📹 Telecamera Sicurezza",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 20.sp, color = Color(0xFF718096))
                    }
                }

                // Tab indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.forEachIndexed { index, title ->
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clickable { currentPage = index },
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == currentPage) Color(0xFF4A00E0)
                                else Color(0xFFF7FAFC)
                            )
                        ) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (index == currentPage) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == currentPage) Color.White else Color(0xFF718096),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contenuto pagine
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    when (currentPage) {
                        0 -> CameraLiveViewPage(cameraData)
                        1 -> CameraRecordingsPage(recordings)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraLiveViewPage(cameraData: YachtCameraData) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Live view principale
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A202C)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraData.isOnline) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📹", fontSize = 64.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFE53E3E), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LIVE",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📵", fontSize = 64.sp, color = Color.Gray)
                            Text(
                                text = "TELECAMERA OFFLINE",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            // Status e informazioni
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "📊 Stato Telecamera",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    CameraStatusRow(
                        icon = "🟢",
                        label = "Connessione",
                        value = if (cameraData.isOnline) "Online" else "Offline",
                        isGood = cameraData.isOnline
                    )

                    CameraStatusRow(
                        icon = "📼",
                        label = "Registrazione",
                        value = cameraData.recordingStatus,
                        isGood = cameraData.recordingStatus == "Attiva"
                    )

                    CameraStatusRow(
                        icon = "🏃",
                        label = "Ultimo Movimento",
                        value = cameraData.lastMotion,
                        isGood = true
                    )

                    CameraStatusRow(
                        icon = "👥",
                        label = "Persone Rilevate",
                        value = "${cameraData.detectedPeople}",
                        isGood = cameraData.detectedPeople == 0
                    )

                    CameraStatusRow(
                        icon = "🌙",
                        label = "Visione Notturna",
                        value = if (cameraData.nightVision) "Attiva" else "Disattiva",
                        isGood = cameraData.nightVision
                    )

                    CameraStatusRow(
                        icon = "🎚️",
                        label = "Sensibilità Movimento",
                        value = "${cameraData.motionSensitivity}%",
                        isGood = true
                    )
                }
            }
        }

        item {
            // Controlli rapidi
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A00E0))
                    ) {
                        Text("📱 Visualizza", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📸 Snapshot")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚙️ Impostazioni")
                    }

                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔄 Riavvia")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraRecordingsPage(recordings: List<CameraRecording>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "📼 Registrazioni Recenti",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (recordings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📹", fontSize = 48.sp)
                        Text(
                            text = "Nessuna registrazione disponibile",
                            fontSize = 16.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recordings) { recording ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail placeholder
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color(0xFF1A202C), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📹", fontSize = 24.sp, color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = recording.timestamp,
                                fontSize = 12.sp,
                                color = Color(0xFF718096)
                            )
                            Text(
                                text = "Durata: ${recording.duration}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2D3748)
                            )
                            if (recording.hasMotion) {
                                Text(
                                    text = "🏃 Movimento rilevato",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE53E3E)
                                )
                            }
                        }

                        IconButton(onClick = { }) {
                            Text("▶️", fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraStatusRow(
    icon: String,
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color(0xFF718096)
            )
        }

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isGood) Color(0xFF38A169) else Color(0xFFE53E3E)
        )
    }
}

// Funzioni helper per generare dati iniziali
private fun generateInitialHistory(): List<HistoryRecord> {
    return (1..15).map { index ->
        val hoursAgo = index * 2
        val temp = 20f + (Math.random() * 8).toFloat()
        val humidity = 50f + (Math.random() * 25).toFloat()
        val gas = (Math.random() * 0.08).toFloat()

        HistoryRecord(
            timestamp = "${hoursAgo}h fa",
            temperature = temp,
            humidity = humidity,
            gasLevel = gas,
            isAlert = gas > 0.05f
        )
    }
}

private fun generateInitialRecordings(): List<CameraRecording> {
    return (1..8).map { index ->
        val hoursAgo = index * 4
        CameraRecording(
            id = "rec_$index",
            timestamp = "${hoursAgo}h fa",
            duration = "${(3 + Math.random() * 12).toInt()} min",
            hasMotion = Math.random() > 0.4
        )
    }
}