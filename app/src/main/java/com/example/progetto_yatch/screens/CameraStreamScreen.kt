package com.example.progetto_yatch.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.progetto_yatch.services.*
import com.example.progetto_yatch.utils.NotificationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStreamScreen(
    onBackPressed: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("📹 Live Stream", "👥 Persone", "🚨 Allarmi", "📊 Sistema")

    // Stati per i dati della telecamera
    var cameraStatus by remember { mutableStateOf<CameraStatus?>(null) }
    var pendingAlerts by remember { mutableStateOf<List<DetectionAlert>>(emptyList()) }
    var recentDetections by remember { mutableStateOf<List<RecentDetection>>(emptyList()) }
    var knownPersons by remember { mutableStateOf<List<KnownPerson>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastUpdate by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Funzione per caricare tutti i dati
    suspend fun loadCameraData() {
        isLoading = true
        errorMessage = ""

        try {
            // Carica stato telecamera
            CameraApiService.getCameraStatus().fold(
                onSuccess = { status ->
                    cameraStatus = status
                    lastUpdate = "Aggiornato: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                },
                onFailure = { error ->
                    errorMessage = "Errore stato: ${error.message}"
                }
            )

            // Carica allarmi
            CameraApiService.getPendingAlerts().fold(
                onSuccess = { alerts ->
                    pendingAlerts = alerts
                    // Invia notifiche per nuovi allarmi
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
                onFailure = { error ->
                    if (errorMessage.isEmpty()) errorMessage = "Errore allarmi: ${error.message}"
                }
            )

            // Carica rilevamenti recenti
            CameraApiService.getRecentDetections().fold(
                onSuccess = { detections ->
                    recentDetections = detections
                },
                onFailure = { error ->
                    if (errorMessage.isEmpty()) errorMessage = "Errore rilevamenti: ${error.message}"
                }
            )

            // Carica persone conosciute
            CameraApiService.getKnownPersons().fold(
                onSuccess = { persons ->
                    knownPersons = persons
                },
                onFailure = { error ->
                    if (errorMessage.isEmpty()) errorMessage = "Errore persone: ${error.message}"
                }
            )

        } catch (e: Exception) {
            errorMessage = "Errore generale: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Carica dati all'avvio e ogni 30 secondi
    LaunchedEffect(Unit) {
        loadCameraData()
        while (true) {
            delay(30000) // 30 secondi
            loadCameraData()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text("🎥 Telecamera Sicurezza") },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Text("←", fontSize = 18.sp)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            loadCameraData()
                        }
                    }
                ) {
                    Text("🔄", fontSize = 16.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF4A00E0),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

        // Status bar con informazioni veloci
        CameraStatusBar(
            cameraStatus = cameraStatus,
            pendingAlerts = pendingAlerts.size,
            isLoading = isLoading,
            errorMessage = errorMessage,
            lastUpdate = lastUpdate
        )

        // Tab navigation
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title, fontSize = 12.sp) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        // Contenuto basato sul tab selezionato
        when (selectedTab) {
            0 -> LiveStreamTab()
            1 -> KnownPersonsTab(knownPersons, isLoading)
            2 -> AlertsTab(pendingAlerts, recentDetections, isLoading) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    CameraApiService.clearAlerts().fold(
                        onSuccess = { loadCameraData() },
                        onFailure = { /* Ignora errori di pulizia */ }
                    )
                }
            }
            3 -> SystemInfoTab(cameraStatus, isLoading) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    CameraApiService.reconnectCamera().fold(
                        onSuccess = { loadCameraData() },
                        onFailure = { /* Ignora errori di riconnessione */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraStatusBar(
    cameraStatus: CameraStatus?,
    pendingAlerts: Int,
    isLoading: Boolean,
    errorMessage: String,
    lastUpdate: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                pendingAlerts > 0 -> Color(0xFFFFEBEE)
                cameraStatus?.camera?.connected == true -> Color(0xFFE8F5E8)
                else -> Color(0xFFFFF3E0)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (cameraStatus?.camera?.connected == true) "🟢" else "🔴",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (cameraStatus?.camera?.connected == true) "Camera Online" else "Camera Offline",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        fontSize = 10.sp,
                        color = Color(0xFFE53E3E)
                    )
                } else if (lastUpdate.isNotEmpty()) {
                    Text(
                        text = lastUpdate,
                        fontSize = 10.sp,
                        color = Color(0xFF718096)
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (pendingAlerts > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE53E3E))
                    ) {
                        Text(
                            text = "🚨 $pendingAlerts",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${cameraStatus?.recognition?.known_persons ?: 0} persone",
                    fontSize = 10.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

@Composable
private fun LiveStreamTab() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            // Stream video in WebView
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.domStorageEnabled = true

                            // Carica la pagina dello stream
                            val streamUrl = CameraApiService.getStreamUrl()
                            val htmlContent = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <style>
                                        body { margin: 0; padding: 0; background: #000; display: flex; justify-content: center; align-items: center; height: 100vh; }
                                        img { max-width: 100%; max-height: 100%; object-fit: contain; }
                                    </style>
                                </head>
                                <body>
                                    <img src="$streamUrl" alt="Live Stream" />
                                </body>
                                </html>
                            """.trimIndent()

                            loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pulsanti di controllo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { /* Ricarica stream */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔄 Ricarica")
                }

                Button(
                    onClick = { /* Apri interfaccia completa */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🌐 Web UI")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Stream live dal sistema di riconoscimento facciale",
                fontSize = 12.sp,
                color = Color(0xFF718096),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun KnownPersonsTab(persons: List<KnownPerson>, isLoading: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFC))
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
                            text = "👥 Persone Registrate",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${persons.size} persone totali",
                            fontSize = 12.sp,
                            color = Color(0xFF718096)
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        if (persons.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👤", fontSize = 48.sp)
                        Text(
                            text = "Nessuna persona registrata",
                            fontSize = 16.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Usa l'interfaccia web per aggiungere persone",
                            fontSize = 12.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(persons) { person ->
                PersonCard(person)
            }
        }
    }
}

@Composable
private fun PersonCard(person: KnownPerson) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (person.is_complete) Color.White else Color(0xFFFFF7ED)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        when (person.access_level) {
                            "owner" -> Color(0xFF4A00E0)
                            "admin" -> Color(0xFF38A169)
                            else -> Color(0xFF718096)
                        },
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (person.access_level) {
                        "owner" -> "👑"
                        "admin" -> "🔧"
                        else -> "👤"
                    },
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${person.image_count} foto • Qualità: ${(person.avg_quality * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = Color(0xFF718096)
                )
                Text(
                    text = person.access_level.uppercase(),
                    fontSize = 10.sp,
                    color = when (person.access_level) {
                        "owner" -> Color(0xFF4A00E0)
                        "admin" -> Color(0xFF38A169)
                        else -> Color(0xFF718096)
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (person.is_complete) "✅" else "⚠️",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun AlertsTab(
    alerts: List<DetectionAlert>,
    detections: List<RecentDetection>,
    isLoading: Boolean,
    onClearAlerts: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (alerts.isNotEmpty()) Color(0xFFFFEBEE) else Color(0xFFF7FAFC)
                )
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
                            text = "🚨 Allarmi Attivi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alerts.isNotEmpty()) Color(0xFFE53E3E) else Color(0xFF2D3748)
                        )
                        Text(
                            text = "${alerts.size} allarmi pendenti",
                            fontSize = 12.sp,
                            color = Color(0xFF718096)
                        )
                    }

                    if (alerts.isNotEmpty()) {
                        Button(
                            onClick = onClearAlerts,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E))
                        ) {
                            Text("🧹 Pulisci", color = Color.White)
                        }
                    }
                }
            }
        }

        items(alerts) { alert ->
            AlertCard(alert)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "📊 Rilevamenti Recenti",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(detections.take(10)) { detection ->
            DetectionCard(detection)
        }
    }
}

@Composable
private fun AlertCard(alert: DetectionAlert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚨 ${alert.type}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53E3E)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (alert.severity) {
                            "HIGH" -> Color(0xFFE53E3E)
                            "MEDIUM" -> Color(0xFFED8936)
                            else -> Color(0xFF718096)
                        }
                    )
                ) {
                    Text(
                        text = alert.severity,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = alert.message,
                fontSize = 13.sp,
                color = Color(0xFF2D3748)
            )

            Text(
                text = "${alert.timestamp} • ${alert.area} • Qualità: ${(alert.quality * 100).toInt()}%",
                fontSize = 11.sp,
                color = Color(0xFF718096)
            )
        }
    }
}

@Composable
private fun DetectionCard(detection: RecentDetection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (detection.is_unknown) Color(0xFFFFF5F5) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (detection.is_unknown) "❓" else "✅",
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detection.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (detection.is_unknown) Color(0xFFE53E3E) else Color(0xFF2D3748)
                )
                Text(
                    text = "${detection.timestamp} • ${(detection.confidence * 100).toInt()}% • Q:${(detection.quality * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = Color(0xFF718096)
                )
            }

            Text(
                text = detection.access_level.uppercase(),
                fontSize = 10.sp,
                color = when (detection.access_level) {
                    "owner" -> Color(0xFF4A00E0)
                    "admin" -> Color(0xFF38A169)
                    "guest" -> Color(0xFF718096)
                    else -> Color(0xFFE53E3E)
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SystemInfoTab(
    cameraStatus: CameraStatus?,
    isLoading: Boolean,
    onReconnectCamera: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (cameraStatus?.camera?.connected == true)
                        Color(0xFFE8F5E8) else Color(0xFFFFEBEE)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 Stato Sistema",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (cameraStatus?.camera?.connected != true) {
                            Button(
                                onClick = onReconnectCamera,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E))
                            ) {
                                Text("🔄 Riconnetti", color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    cameraStatus?.let { status ->
                        SystemInfoRow("📹", "Camera", if (status.camera.connected) "Online" else "Offline")
                        SystemInfoRow("🖥️", "Risoluzione", "${status.camera.info.width}x${status.camera.info.height}")
                        SystemInfoRow("🎯", "FPS", "${status.camera.info.fps_actual}")
                        SystemInfoRow("👥", "Persone", "${status.recognition.known_persons}")
                        SystemInfoRow("⚡", "Uptime", "${status.health.uptime_seconds / 3600}h")
                        SystemInfoRow("💾", "Memoria", "${status.health.memory_usage_percent.toInt()}%")
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color(0xFF718096)
            )
        }

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}