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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.progetto_yatch.utils.NotificationUtils
import com.example.progetto_yatch.services.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import kotlin.math.abs

data class HistoryRecord(
    val timestamp: String,
    val sensor_value: Double,
    val alert_status: Int,
    val isAlert: Boolean,
    val alert_text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSecurity: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val context = LocalContext.current

    // Stati per popup
    var showSensorPopup by remember { mutableStateOf(false) }

    // Dati reali dell'endpoint fumo
    var nodeRedUrl by remember { mutableStateOf("https://game-romantic-gnat.ngrok-free.app") }
    var latestSmokeData by remember { mutableStateOf<SmokeDetectionData?>(null) }
    var smokeHistory by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var smokeIsLoading by remember { mutableStateOf(false) }
    var smokeErrorMessage by remember { mutableStateOf("") }
    var smokLastUpdate by remember { mutableStateOf("") }

    // Dati reali della telecamera
    var cameraStatus by remember { mutableStateOf<CameraStatus?>(null) }
    var cameraAlerts by remember { mutableStateOf<List<DetectionAlert>>(emptyList()) }
    var cameraDetections by remember { mutableStateOf<List<RecentDetection>>(emptyList()) }
    var cameraIsLoading by remember { mutableStateOf(false) }
    var cameraErrorMessage by remember { mutableStateOf("") }
    var cameraLastUpdate by remember { mutableStateOf("") }

    // Stati UI per gestione permessi
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val smokeClient = remember { OkHttpClient() }

    // Launcher per richiedere permessi
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            showPermissionDialog = true
        }
    }

    // Funzione per caricare i dati reali del fumo
    fun loadSmokeData() {
        if (!nodeRedUrl.startsWith("http")) return

        smokeIsLoading = true
        smokeErrorMessage = ""
        val baseUrl = nodeRedUrl.trimEnd('/')
        val apiUrl = "$baseUrl/api/latest"

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()

        smokeClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                smokeIsLoading = false
                smokeErrorMessage = "Errore connessione: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                smokeIsLoading = false
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val latestObj = json["latest"]?.jsonObject

                            if (latestObj != null) {
                                latestSmokeData = SmokeDetectionData(
                                    time = latestObj["time"]?.jsonPrimitive?.content ?: "",
                                    alert_status = latestObj["alert_status"]?.jsonPrimitive?.int ?: 0,
                                    sensor_value = latestObj["sensor_value"]?.jsonPrimitive?.double ?: 0.0,
                                    is_alert = latestObj["is_alert"]?.jsonPrimitive?.boolean ?: false,
                                    alert_text = latestObj["alert_text"]?.jsonPrimitive?.content ?: "Unknown"
                                )
                                smokLastUpdate = "Aggiornato: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                                smokeErrorMessage = ""

                                // Invia notifica se c'è un allarme
                                if (latestSmokeData?.is_alert == true) {
                                    NotificationUtils.sendSmokeAlert(
                                        context = context,
                                        sensorValue = latestSmokeData?.sensor_value ?: 0.0,
                                        alertStatus = latestSmokeData?.alert_status ?: 0,
                                        alertText = latestSmokeData?.alert_text ?: "Allarme rilevato"
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        smokeErrorMessage = "Errore parsing: ${e.message}"
                    }
                } else {
                    smokeErrorMessage = "Errore HTTP: ${response.code}"
                }
            }
        })
    }

    // Funzione per caricare i dati della telecamera
    suspend fun loadCameraData() {
        cameraIsLoading = true
        cameraErrorMessage = ""

        try {
            // Carica stato telecamera
            CameraApiService.getCameraStatus().fold(
                onSuccess = { status ->
                    cameraStatus = status
                    cameraLastUpdate = "Aggiornato: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                },
                onFailure = { error ->
                    cameraErrorMessage = "Errore camera: ${error.message}"
                }
            )

            // Carica allarmi
            CameraApiService.getPendingAlerts().fold(
                onSuccess = { alerts ->
                    cameraAlerts = alerts
                    // Invia notifiche per nuovi intrusi
                    alerts.forEach { alert ->
                        if (alert.type == "INTRUSO_RILEVATO") {
                            NotificationUtils.sendCameraAlert(
                                context = context,
                                alertType = "Intruso Rilevato",
                                message = "${alert.message} - Area: ${alert.area}"
                            )
                        }
                    }
                },
                onFailure = { /* Ignora errori allarmi */ }
            )

            // Carica rilevamenti recenti
            CameraApiService.getRecentDetections().fold(
                onSuccess = { detections ->
                    cameraDetections = detections
                },
                onFailure = { /* Ignora errori rilevamenti */ }
            )

        } catch (e: Exception) {
            cameraErrorMessage = "Errore generale: ${e.message}"
        } finally {
            cameraIsLoading = false
        }
    }

    // Funzione per caricare lo storico fumo
    fun loadSmokeHistory() {
        if (!nodeRedUrl.startsWith("http")) return

        val baseUrl = nodeRedUrl.trimEnd('/')
        val apiUrl = "$baseUrl/api/smoke-data"

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()

        smokeClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Ignora errori per lo storico
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val dataArray = json["data"]?.jsonArray

                            val parsedData = dataArray?.map { item ->
                                val obj = item.jsonObject
                                HistoryRecord(
                                    timestamp = obj["time"]?.jsonPrimitive?.content ?: "",
                                    sensor_value = obj["sensor_value"]?.jsonPrimitive?.double ?: 0.0,
                                    alert_status = obj["alert_status"]?.jsonPrimitive?.int ?: 0,
                                    isAlert = obj["is_alert"]?.jsonPrimitive?.boolean ?: false,
                                    alert_text = obj["alert_text"]?.jsonPrimitive?.content ?: "Unknown"
                                )
                            } ?: emptyList()

                            smokeHistory = parsedData.take(10)
                        }
                    } catch (e: Exception) {
                        // Ignora errori parsing per lo storico
                    }
                }
            }
        })
    }

    // Carica le preferenze salvate e i dati all'avvio
    LaunchedEffect(Unit) {
        NotificationUtils.createNotificationChannel(context)
        hasNotificationPermission = NotificationUtils.areNotificationsEnabled(context)

        try {
            val savedUrl = com.example.progetto_yatch.services.SmokeMonitoringPreferences.getApiUrl(context)
            if (savedUrl != null) {
                nodeRedUrl = savedUrl
            }
        } catch (e: Exception) {
            // Usa URL di default se non disponibile
        }

        // Carica dati iniziali
        loadSmokeData()
        loadSmokeHistory()
        loadCameraData()
    }

    // Aggiornamento automatico ogni 30 secondi
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // 30 secondi
            loadSmokeData()
            loadCameraData()
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
                smokeData = latestSmokeData,
                cameraAlerts = cameraAlerts.size,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card principale yacht con sensori REALI
            YachtMainCard(
                smokeData = latestSmokeData,
                cameraStatus = cameraStatus,
                cameraAlerts = cameraAlerts.size,
                onSensorClick = { showSensorPopup = true },
                onCameraClick = onNavigateToCamera,
                smokeIsLoading = smokeIsLoading,
                cameraIsLoading = cameraIsLoading,
                smokeErrorMessage = smokeErrorMessage,
                cameraErrorMessage = cameraErrorMessage,
                onRefreshSmoke = { loadSmokeData() },
                onRefreshCamera = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        loadCameraData()
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Grid status cards CON DATI REALI
            YachtStatusGrid(
                smokeData = latestSmokeData,
                cameraStatus = cameraStatus,
                cameraAlerts = cameraAlerts.size
            )

            if (smokLastUpdate.isNotEmpty() || cameraLastUpdate.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (smokLastUpdate.isNotEmpty()) smokLastUpdate else cameraLastUpdate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

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

    // Popup sensore completo CON DATI REALI
    if (showSensorPopup) {
        ImprovedYachtSensorPopup(
            smokeData = latestSmokeData,
            history = smokeHistory,
            onDismiss = { showSensorPopup = false }
        )
    }
}

@Composable
private fun YachtHeader(
    smokeData: SmokeDetectionData?,
    cameraAlerts: Int,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
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
                    smokeData?.is_alert == true -> "🚨 ALLARME FUMO ATTIVO"
                    cameraAlerts > 0 -> "🚨 ${cameraAlerts} INTRUSI RILEVATI"
                    !hasNotificationPermission -> "🔔 Abilita notifiche"
                    smokeData != null -> "✅ Sistema attivo"
                    else -> "🔄 Caricamento..."
                },
                fontSize = 16.sp,
                color = when {
                    smokeData?.is_alert == true || cameraAlerts > 0 -> Color(0xFFE53E3E)
                    !hasNotificationPermission -> Color(0xFFD69E2E)
                    smokeData != null -> Color(0xFF38A169)
                    else -> Color(0xFF718096)
                },
                fontWeight = FontWeight.Medium
            )
        }

        // Pulsante notifiche se necessario
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
    }
}

@Composable
private fun YachtMainCard(
    smokeData: SmokeDetectionData?,
    cameraStatus: CameraStatus?,
    cameraAlerts: Int,
    onSensorClick: () -> Unit,
    onCameraClick: () -> Unit,
    smokeIsLoading: Boolean,
    cameraIsLoading: Boolean,
    smokeErrorMessage: String,
    cameraErrorMessage: String,
    onRefreshSmoke: () -> Unit,
    onRefreshCamera: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                smokeData?.is_alert == true || cameraAlerts > 0 -> Color(0xFFFFEBEE)
                else -> Color.White
            }
        ),
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
                // Logo Alertify principale con stato
                if (smokeData?.is_alert == true || cameraAlerts > 0) {
                    // Animazione di allarme
                    val infiniteTransition = rememberInfiniteTransition(label = "alarm_animation")
                    val alertScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alert_scale"
                    )

                    Text(
                        text = "🚨",
                        fontSize = (120 * alertScale).sp,
                        modifier = Modifier.offset(y = 5.dp),
                        color = Color(0xFFE53E3E)
                    )
                } else {
                    Text(
                        text = "🚨",
                        fontSize = 120.sp,
                        modifier = Modifier.offset(y = 5.dp)
                    )
                }

                Text(
                    text = "ALERTIFY SYSTEM",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        smokeData?.is_alert == true || cameraAlerts > 0 -> Color(0xFFE53E3E)
                        else -> Color(0xFF718096)
                    },
                    letterSpacing = 3.sp
                )

                when {
                    smokeData?.is_alert == true -> {
                        Text(
                            text = smokeData.alert_text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53E3E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    cameraAlerts > 0 -> {
                        Text(
                            text = "$cameraAlerts intrusi rilevati!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53E3E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Sensori con dati reali
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Sensore Gas/Fumo CON DATI REALI
                    YachtSensorButton(
                        icon = "🔥",
                        label = "Sensore Fumo",
                        sublabel = if (smokeIsLoading) "Caricando..." else "Gas/Fumo",
                        isAlert = smokeData?.is_alert == true,
                        value = when {
                            smokeIsLoading -> "..."
                            smokeData != null -> "${smokeData.sensor_value.toInt()}"
                            smokeErrorMessage.isNotEmpty() -> "Errore"
                            else -> "Non disponibile"
                        },
                        onClick = onSensorClick,
                        onRefresh = onRefreshSmoke,
                        showRefresh = smokeErrorMessage.isNotEmpty()
                    )

                    // Telecamera CON DATI REALI
                    YachtSensorButton(
                        icon = "📹",
                        label = "Camera",
                        sublabel = if (cameraIsLoading) "Caricando..." else "Sicurezza",
                        isAlert = cameraAlerts > 0,
                        value = when {
                            cameraIsLoading -> "..."
                            cameraStatus?.camera?.connected == true -> "Online"
                            cameraStatus?.camera?.connected == false -> "Offline"
                            cameraErrorMessage.isNotEmpty() -> "Errore"
                            else -> "Non disponibile"
                        },
                        onClick = onCameraClick,
                        onRefresh = onRefreshCamera,
                        showRefresh = cameraErrorMessage.isNotEmpty()
                    )
                }

                if (smokeErrorMessage.isNotEmpty() || cameraErrorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = smokeErrorMessage.ifEmpty { cameraErrorMessage },
                        color = Color(0xFFE53E3E),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
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
    onClick: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    showRefresh: Boolean = false
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

        if (showRefresh && onRefresh != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🔄",
                fontSize = 12.sp,
                modifier = Modifier.clickable { onRefresh() }
            )
        }
    }
}

@Composable
private fun YachtStatusGrid(
    smokeData: SmokeDetectionData?,
    cameraStatus: CameraStatus?,
    cameraAlerts: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sensore Fumo
        YachtStatusCard(
            modifier = Modifier.weight(1f),
            icon = "🔥",
            title = "Sensore",
            value = when {
                smokeData != null -> "${smokeData.sensor_value.toInt()}"
                else -> "---"
            },
            color = if (smokeData?.is_alert == true) Color(0xFFE53E3E) else Color(0xFF38A169),
            isAlert = smokeData?.is_alert == true
        )

        // Stato Sistema
        YachtStatusCard(
            modifier = Modifier.weight(1f),
            icon = "📊",
            title = "Status",
            value = when {
                smokeData?.is_alert == true || cameraAlerts > 0 -> "ALERT"
                smokeData != null && cameraStatus?.camera?.connected == true -> "OK"
                else -> "..."
            },
            color = when {
                smokeData?.is_alert == true || cameraAlerts > 0 -> Color(0xFFE53E3E)
                smokeData != null && cameraStatus?.camera?.connected == true -> Color(0xFF38A169)
                else -> Color(0xFF718096)
            },
            isAlert = smokeData?.is_alert == true || cameraAlerts > 0
        )

        // Camera con dati reali
        YachtStatusCard(
            modifier = Modifier.weight(1f),
            icon = "📹",
            title = "Camera",
            value = when {
                cameraAlerts > 0 -> "$cameraAlerts Intrusi"
                cameraStatus?.camera?.connected == true -> "Online"
                cameraStatus?.camera?.connected == false -> "Offline"
                else -> "..."
            },
            color = when {
                cameraAlerts > 0 -> Color(0xFFE53E3E)
                cameraStatus?.camera?.connected == true -> Color(0xFF38A169)
                else -> Color(0xFFE53E3E)
            },
            isAlert = cameraAlerts > 0
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
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFFFED7D7) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(text = icon, fontSize = 28.sp)

            Text(
                text = title,
                fontSize = 11.sp,
                color = Color(0xFF718096),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isAlert) {
                Text(
                    text = "ALERT",
                    fontSize = 9.sp,
                    color = Color(0xFFE53E3E),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
                    text = "Ricevi avvisi istantanei per emergenze e allarmi sensori",
                    fontSize = 13.sp,
                    color = Color(0xFF744210),
                    lineHeight = 18.sp
                )
            }
            Text("→", fontSize = 20.sp, color = Color(0xFFD69E2E))
        }
    }
}

// POPUP MIGLIORATO per sensore fumo (codice esistente rimane uguale)
@Composable
private fun ImprovedYachtSensorPopup(
    smokeData: SmokeDetectionData?,
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header popup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 Sensore Gas/Fumo",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, color = Color(0xFF718096))
                    }
                }

                // Tab indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    pages.forEachIndexed { index, title ->
                        val isSelected = index == currentPage
                        val animatedAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.6f,
                            animationSpec = tween(300),
                            label = "tab_alpha"
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 0.95f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "tab_scale"
                        )

                        Card(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .scale(animatedScale)
                                .clickable { currentPage = index },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF4A00E0)
                                else Color(0xFFF7FAFC)
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 6.dp else 2.dp
                            )
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF718096),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .graphicsLayer { alpha = animatedAlpha }
                            )
                        }
                    }
                }

                // Indicatore di pagina
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = index == currentPage
                        val animatedWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "indicator_width"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(animatedWidth)
                                .height(3.dp)
                                .background(
                                    if (isSelected) Color(0xFF4A00E0) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Contenuto pagine
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    val contentAlpha by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "content_alpha"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = contentAlpha }
                    ) {
                        when (currentPage) {
                            0 -> SensorCurrentStatusPage(smokeData)
                            1 -> SensorHistoryPage(history)
                        }
                    }
                }
            }
        }
    }
}

// Resto delle funzioni rimangono identiche...
@Composable
private fun SensorCurrentStatusPage(smokeData: SmokeDetectionData?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (smokeData?.is_alert == true) Color(0xFFE53E3E) else Color(0xFF38A169)
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
                        text = if (smokeData?.is_alert == true) "⚠️" else "✅",
                        fontSize = 48.sp
                    )
                    Text(
                        text = smokeData?.alert_text ?: if (smokeData != null) "AMBIENTE SICURO" else "CARICAMENTO...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    if (smokeData != null) {
                        Text(
                            text = "Ultimo aggiornamento: ${smokeData.time}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (smokeData != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "📊 Metriche Sensore",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        SensorMetricRow(
                            icon = "🔥",
                            label = "Valore Sensore",
                            value = "${smokeData.sensor_value.toInt()}",
                            isNormal = !smokeData.is_alert
                        )

                        SensorMetricRow(
                            icon = "📊",
                            label = "Status Allarme",
                            value = "${smokeData.alert_status}",
                            isNormal = smokeData.alert_status == 0
                        )

                        SensorMetricRow(
                            icon = "⚠️",
                            label = "Stato Sistema",
                            value = if (smokeData.is_alert) "ALLARME" else "NORMALE",
                            isNormal = !smokeData.is_alert
                        )

                        SensorMetricRow(
                            icon = "📝",
                            label = "Messaggio",
                            value = smokeData.alert_text,
                            isNormal = !smokeData.is_alert
                        )
                    }
                }
            }
        }

        if (smokeData?.is_alert == true) {
            item {
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
                                text = "Allarme Fumo Attivo",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53E3E)
                            )
                            Text(
                                text = "Valore: ${smokeData.sensor_value.toInt()} • Status: ${smokeData.alert_status} • ${smokeData.alert_text}",
                                fontSize = 13.sp,
                                color = Color(0xFF744210)
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Aggiorna Dati")
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
                                text = "Valore: ${record.sensor_value.toInt()} • Status: ${record.alert_status}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2D3748)
                            )
                            Text(
                                text = record.alert_text,
                                fontSize = 12.sp,
                                color = if (record.isAlert) Color(0xFFE53E3E) else Color(0xFF718096)
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