package com.example.progetto_yatch

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.progetto_yatch.ui.theme.Progetto_yatchTheme
import kotlinx.coroutines.delay
import okhttp3.*
import java.io.IOException
import kotlinx.serialization.json.*

// Modelli di dati per il rilevamento fumo
data class SmokeDetectionData(
    val time: String,
    val alert_status: Int,
    val sensor_value: Double,
    val is_alert: Boolean,
    val alert_text: String
)

data class SmokeApiResponse(
    val status: String,
    val timestamp: String,
    val data: List<SmokeDetectionData>,
    val count: Int,
    val alerts_count: Int
)

data class LatestSmokeResponse(
    val status: String,
    val timestamp: String,
    val latest: SmokeDetectionData
)

// NUOVI MODELLI PER IL SISTEMA DI SICUREZZA
data class SecurityStatus(
    val success: Boolean,
    val status: String,
    val timestamp: String,
    val system: SecuritySystemData?
)

data class SecuritySystemData(
    val known_persons: Int,
    val complete_persons: Int,
    val pending_alerts: Int,
    val recent_detections: Int,
    val threshold: Double
)

data class SecurityAlert(
    val id: String,
    val timestamp: String,
    val type: String,
    val message: String,
    val confidence: Double,
    val quality: Double,
    val location: List<Int>,
    val severity: String
)

data class SecurityAlertsResponse(
    val success: Boolean,
    val timestamp: String,
    val alerts: List<SecurityAlert>,
    val count: Int
)

data class SecurityDetection(
    val timestamp: String,
    val name: String,
    val confidence: Double,
    val access_level: String,
    val location: List<Int>,
    val quality: Double,
    val is_unknown: Boolean
)

data class SecurityDetectionsResponse(
    val success: Boolean,
    val timestamp: String,
    val detections: List<SecurityDetection>,
    val count: Int
)

data class SecurityPerson(
    val name: String,
    val access_level: String,
    val features_count: Int,
    val image_count: Int,
    val avg_quality: Double,
    val added_at: String,
    val is_complete: Boolean
)

data class SecurityPersonsResponse(
    val success: Boolean,
    val persons: List<SecurityPerson>,
    val count: Int
)

data class StreamUrlResponse(
    val success: Boolean,
    val stream_url: String,
    val format: String,
    val resolution: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Progetto_yatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IntegratedSecurityApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedSecurityApp() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("🔒 Sicurezza", "🔥 Rilevamento", "📊 Storico", "⚡ Monitor")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        when (selectedTab) {
            0 -> SecuritySystemTab()
            1 -> SmokeDetectionTab()
            2 -> SmokeHistoryTab()
            3 -> LiveMonitorTab()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySystemTab() {
    var securityUrl by remember { mutableStateOf("http://192.168.1.100:5000") }
    var securityStatus by remember { mutableStateOf<SecurityStatus?>(null) }
    var securityAlerts by remember { mutableStateOf<List<SecurityAlert>>(emptyList()) }
    var securityDetections by remember { mutableStateOf<List<SecurityDetection>>(emptyList()) }
    var securityPersons by remember { mutableStateOf<List<SecurityPerson>>(emptyList()) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastUpdate by remember { mutableStateOf("") }
    var selectedSubTab by remember { mutableStateOf(0) }

    val client = remember { OkHttpClient() }

    // Funzioni per le chiamate API
    fun loadSecurityStatus() {
        if (!securityUrl.startsWith("http")) return

        isLoading = true
        errorMessage = ""
        val apiUrl = "${securityUrl.trimEnd('/')}/api/security/status"

        val request = Request.Builder().url(apiUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                isLoading = false
                errorMessage = "Errore connessione: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                isLoading = false
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject

                            securityStatus = SecurityStatus(
                                success = json["success"]?.jsonPrimitive?.boolean ?: false,
                                status = json["status"]?.jsonPrimitive?.content ?: "UNKNOWN",
                                timestamp = json["timestamp"]?.jsonPrimitive?.content ?: "",
                                system = json["system"]?.jsonObject?.let { systemObj ->
                                    SecuritySystemData(
                                        known_persons = systemObj["known_persons"]?.jsonPrimitive?.int ?: 0,
                                        complete_persons = systemObj["complete_persons"]?.jsonPrimitive?.int ?: 0,
                                        pending_alerts = systemObj["pending_alerts"]?.jsonPrimitive?.int ?: 0,
                                        recent_detections = systemObj["recent_detections"]?.jsonPrimitive?.int ?: 0,
                                        threshold = systemObj["threshold"]?.jsonPrimitive?.double ?: 0.0
                                    )
                                }
                            )

                            lastUpdate = "Aggiornato: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Errore parsing: ${e.message}"
                    }
                } else {
                    errorMessage = "Errore HTTP: ${response.code}"
                }
            }
        })
    }

    fun loadSecurityAlerts() {
        if (!securityUrl.startsWith("http")) return

        val apiUrl = "${securityUrl.trimEnd('/')}/api/security/alerts"
        val request = Request.Builder().url(apiUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Errore alerts: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val alertsArray = json["alerts"]?.jsonArray

                            securityAlerts = alertsArray?.map { alertJson ->
                                val alert = alertJson.jsonObject
                                SecurityAlert(
                                    id = alert["id"]?.jsonPrimitive?.content ?: "",
                                    timestamp = alert["timestamp"]?.jsonPrimitive?.content ?: "",
                                    type = alert["type"]?.jsonPrimitive?.content ?: "",
                                    message = alert["message"]?.jsonPrimitive?.content ?: "",
                                    confidence = alert["confidence"]?.jsonPrimitive?.double ?: 0.0,
                                    quality = alert["quality"]?.jsonPrimitive?.double ?: 0.0,
                                    location = alert["location"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList(),
                                    severity = alert["severity"]?.jsonPrimitive?.content ?: "LOW"
                                )
                            } ?: emptyList()
                        }
                    } catch (e: Exception) {
                        errorMessage = "Errore parsing alerts: ${e.message}"
                    }
                }
            }
        })
    }

    fun loadSecurityDetections() {
        if (!securityUrl.startsWith("http")) return

        val apiUrl = "${securityUrl.trimEnd('/')}/api/security/detections?limit=10"
        val request = Request.Builder().url(apiUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Errore detections: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val detectionsArray = json["detections"]?.jsonArray

                            securityDetections = detectionsArray?.map { detectionJson ->
                                val detection = detectionJson.jsonObject
                                SecurityDetection(
                                    timestamp = detection["timestamp"]?.jsonPrimitive?.content ?: "",
                                    name = detection["name"]?.jsonPrimitive?.content ?: "",
                                    confidence = detection["confidence"]?.jsonPrimitive?.double ?: 0.0,
                                    access_level = detection["access_level"]?.jsonPrimitive?.content ?: "",
                                    location = detection["location"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList(),
                                    quality = detection["quality"]?.jsonPrimitive?.double ?: 0.0,
                                    is_unknown = detection["is_unknown"]?.jsonPrimitive?.boolean ?: false
                                )
                            } ?: emptyList()
                        }
                    } catch (e: Exception) {
                        errorMessage = "Errore parsing detections: ${e.message}"
                    }
                }
            }
        })
    }

    fun loadStreamUrl() {
        if (!securityUrl.startsWith("http")) return

        val apiUrl = "${securityUrl.trimEnd('/')}/api/security/stream-url"
        val request = Request.Builder().url(apiUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                streamUrl = null
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            streamUrl = json["stream_url"]?.jsonPrimitive?.content
                        }
                    } catch (e: Exception) {
                        streamUrl = null
                    }
                }
            }
        })
    }

    fun clearAlerts() {
        if (!securityUrl.startsWith("http")) return

        val apiUrl = "${securityUrl.trimEnd('/')}/api/security/alerts/clear"
        val request = Request.Builder().url(apiUrl).method("POST", RequestBody.create(null, "")).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Errore pulizia alerts: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    securityAlerts = emptyList()
                    loadSecurityStatus() // Ricarica status
                }
            }
        })
    }

    // Auto-refresh ogni 5 secondi
    LaunchedEffect(securityUrl) {
        while (true) {
            if (securityUrl.startsWith("http")) {
                loadSecurityStatus()
                loadSecurityAlerts()
                loadSecurityDetections()
                loadStreamUrl()
            }
            delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (securityAlerts.isNotEmpty())
                    Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒 Sistema Sicurezza Yacht",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (securityAlerts.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "🚨 ${securityAlerts.size} ALLARMI",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = securityUrl,
                    onValueChange = { securityUrl = it },
                    label = { Text("URL Sistema Sicurezza") },
                    placeholder = { Text("http://192.168.1.100:5000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status del sistema
                if (securityStatus != null && securityStatus!!.success) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (securityStatus!!.status == "ACTIVE")
                                Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Status: ${securityStatus!!.status}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            securityStatus!!.system?.let { system ->
                                Text(
                                    text = "👥 Persone: ${system.known_persons} | 🚨 Allarmi: ${system.pending_alerts}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "📊 Rilevamenti: ${system.recent_detections} | 🎯 Soglia: ${(system.threshold * 100).toInt()}%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottoni azioni
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { loadSecurityStatus() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && securityUrl.startsWith("http")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("🔄 Aggiorna")
                        }
                    }

                    Button(
                        onClick = { clearAlerts() },
                        modifier = Modifier.weight(1f),
                        enabled = securityAlerts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Text("🧹 Pulisci Allarmi")
                    }
                }

                if (lastUpdate.isNotEmpty()) {
                    Text(
                        text = lastUpdate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sub-tabs
        val subTabs = listOf("📹 Stream", "🚨 Allarmi", "📊 Rilevamenti")
        TabRow(selectedTabIndex = selectedSubTab) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contenuto sub-tab
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            when (selectedSubTab) {
                0 -> {
                    // Tab Stream Video
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "📹 Live Stream Sicurezza",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (streamUrl != null) {
                            AndroidView(
                                factory = { context ->
                                    WebView(context).apply {
                                        webViewClient = WebViewClient()
                                        settings.javaScriptEnabled = true
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        loadUrl(streamUrl!!)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .background(Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔌 Stream non disponibile\nVerifica connessione",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Tab Allarmi
                    LazyColumn(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "🚨 Allarmi Sicurezza (${securityAlerts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (securityAlerts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "✅",
                                            style = MaterialTheme.typography.displayLarge
                                        )
                                        Text(
                                            text = "Nessun allarme attivo",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(securityAlerts) { alert ->
                                SecurityAlertCard(alert)
                            }
                        }
                    }
                }
                2 -> {
                    // Tab Rilevamenti
                    LazyColumn(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "📊 Rilevamenti Recenti (${securityDetections.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (securityDetections.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📭 Nessun rilevamento recente",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(securityDetections) { detection ->
                                SecurityDetectionCard(detection)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityAlertCard(alert: SecurityAlert) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (alert.severity) {
                "HIGH" -> Color(0xFFFFEBEE)
                "MEDIUM" -> Color(0xFFFFF3E0)
                else -> Color(0xFFF3E5F5)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚨 ${alert.type}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (alert.severity) {
                        "HIGH" -> Color(0xFFD32F2F)
                        "MEDIUM" -> Color(0xFFF57C00)
                        else -> Color(0xFF7B1FA2)
                    }
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (alert.severity) {
                            "HIGH" -> Color(0xFFFF5252)
                            "MEDIUM" -> Color(0xFFFF9800)
                            else -> Color(0xFF9C27B0)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = alert.severity,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🕐 ${alert.timestamp.substring(11, 19)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "📊 Qualità: ${(alert.quality * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SecurityDetectionCard(detection: SecurityDetection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (detection.is_unknown)
                Color(0xFFFFEBEE) else Color(0xFFE8F5E8)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (detection.is_unknown) "❓ ${detection.name}" else "✅ ${detection.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (detection.is_unknown) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )

                Text(
                    text = getAccessLevelLabel(detection.access_level),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "🕐 ${detection.timestamp.substring(11, 19)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "🎯 Conf: ${(detection.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column {
                    Text(
                        text = "📊 Qualità: ${(detection.quality * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "📍 Pos: ${detection.location.joinToString(",")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

fun getAccessLevelLabel(level: String): String {
    return when (level) {
        "admin" -> "🔧 Admin"
        "owner" -> "👑 Owner"
        "guest" -> "🏃 Guest"
        else -> "❓ Sconosciuto"
    }
}

// Le altre funzioni rimangono uguali...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmokeDetectionTab() {
    var nodeRedUrl by remember { mutableStateOf("https://0e36-188-95-73-113.ngrok-free.app") }
    var latestData by remember { mutableStateOf<SmokeDetectionData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastUpdate by remember { mutableStateOf("") }

    val client = remember { OkHttpClient() }

    // Funzione per caricare i dati più recenti
    fun loadLatestData() {
        if (!nodeRedUrl.startsWith("http")) return

        isLoading = true
        errorMessage = ""
        val baseUrl = nodeRedUrl.trimEnd('/')
        val apiUrl = "$baseUrl/api/latest"

        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                isLoading = false
                errorMessage = "Errore connessione: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                isLoading = false
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val latestObj = json["latest"]?.jsonObject

                            if (latestObj != null) {
                                latestData = SmokeDetectionData(
                                    time = latestObj["time"]?.jsonPrimitive?.content ?: "",
                                    alert_status = latestObj["alert_status"]?.jsonPrimitive?.int ?: 0,
                                    sensor_value = latestObj["sensor_value"]?.jsonPrimitive?.double ?: 0.0,
                                    is_alert = latestObj["is_alert"]?.jsonPrimitive?.boolean ?: false,
                                    alert_text = latestObj["alert_text"]?.jsonPrimitive?.content ?: "Unknown"
                                )
                                lastUpdate = "Aggiornato: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                                errorMessage = ""
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "Errore parsing: ${e.message}"
                    }
                } else {
                    errorMessage = "Errore HTTP: ${response.code}"
                }
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (latestData?.is_alert == true)
                    Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 Sistema Rilevamento Fumo",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (latestData?.is_alert == true) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "🚨 ALLARME",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = nodeRedUrl,
                    onValueChange = { nodeRedUrl = it },
                    label = { Text("URL Ngrok Node-RED") },
                    placeholder = { Text("https://xyz789.ngrok.app") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { loadLatestData() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && nodeRedUrl.startsWith("http"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (latestData?.is_alert == true)
                            Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("🔄 Controlla Stato Fumo")
                }

                if (lastUpdate.isNotEmpty()) {
                    Text(
                        text = lastUpdate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (nodeRedUrl.isNotEmpty() && !nodeRedUrl.startsWith("http")) {
                    Text(
                        text = "⚠️ URL deve iniziare con https://",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (latestData != null) {
            SmokeStatusCard(latestData!!)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔍",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "Premi 'Controlla Stato Fumo' per verificare i sensori",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmokeHistoryTab() {
    var nodeRedUrl by remember { mutableStateOf("https://0e36-188-95-73-113.ngrok-free.app") }
    var smokeData by remember { mutableStateOf<List<SmokeDetectionData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var alertsCount by remember { mutableStateOf(0) }

    val client = remember { OkHttpClient() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 Storico Rilevamenti Fumo",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = nodeRedUrl,
                    onValueChange = { nodeRedUrl = it },
                    label = { Text("URL Ngrok Node-RED") },
                    placeholder = { Text("https://xyz789.ngrok.app") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = ""

                        val baseUrl = nodeRedUrl.trimEnd('/')
                        val apiUrl = "$baseUrl/api/smoke-data"

                        val request = Request.Builder()
                            .url(apiUrl)
                            .build()

                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                isLoading = false
                                errorMessage = "Errore connessione: ${e.message}"
                            }

                            override fun onResponse(call: Call, response: Response) {
                                isLoading = false
                                if (response.isSuccessful) {
                                    try {
                                        val responseBody = response.body?.string()
                                        if (responseBody != null) {
                                            val json = Json.parseToJsonElement(responseBody).jsonObject
                                            val dataArray = json["data"]?.jsonArray
                                            alertsCount = json["alerts_count"]?.jsonPrimitive?.int ?: 0

                                            val parsedData = dataArray?.map { item ->
                                                val obj = item.jsonObject
                                                SmokeDetectionData(
                                                    time = obj["time"]?.jsonPrimitive?.content ?: "",
                                                    alert_status = obj["alert_status"]?.jsonPrimitive?.int ?: 0,
                                                    sensor_value = obj["sensor_value"]?.jsonPrimitive?.double ?: 0.0,
                                                    is_alert = obj["is_alert"]?.jsonPrimitive?.boolean ?: false,
                                                    alert_text = obj["alert_text"]?.jsonPrimitive?.content ?: "Unknown"
                                                )
                                            } ?: emptyList()

                                            smokeData = parsedData
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Errore parsing JSON: ${e.message}"
                                    }
                                } else {
                                    errorMessage = "Errore HTTP: ${response.code}"
                                }
                            }
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && nodeRedUrl.startsWith("http")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("📈 Carica Storico Rilevamenti")
                }

                if (smokeData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📊 Tot: ${smokeData.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "🚨 Allarmi: $alertsCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (alertsCount > 0) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (smokeData.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "🔥 Ultimi ${smokeData.size} rilevamenti",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(smokeData) { data ->
                        SmokeDataCard(data)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMonitorTab() {
    var nodeRedUrl by remember { mutableStateOf("https://0e36-188-95-73-113.ngrok-free.app") }
    var latestData by remember { mutableStateOf<SmokeDetectionData?>(null) }
    var isAutoRefresh by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastUpdate by remember { mutableStateOf("") }

    val client = remember { OkHttpClient() }

    fun loadLatestData() {
        if (!nodeRedUrl.startsWith("http")) return

        val baseUrl = nodeRedUrl.trimEnd('/')
        val apiUrl = "$baseUrl/api/latest"

        val request = Request.Builder()
            .url(apiUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                errorMessage = "Errore: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = Json.parseToJsonElement(responseBody).jsonObject
                            val latestObj = json["latest"]?.jsonObject

                            if (latestObj != null) {
                                latestData = SmokeDetectionData(
                                    time = latestObj["time"]?.jsonPrimitive?.content ?: "",
                                    alert_status = latestObj["alert_status"]?.jsonPrimitive?.int ?: 0,
                                    sensor_value = latestObj["sensor_value"]?.jsonPrimitive?.double ?: 0.0,
                                    is_alert = latestObj["is_alert"]?.jsonPrimitive?.boolean ?: false,
                                    alert_text = latestObj["alert_text"]?.jsonPrimitive?.content ?: "Unknown"
                                )
                                lastUpdate = "Aggiornato: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
                                errorMessage = ""
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "Errore parsing: ${e.message}"
                    }
                } else {
                    errorMessage = "Errore HTTP: ${response.code}"
                }
            }
        })
    }

    // Auto-refresh ogni 5 secondi quando attivo
    LaunchedEffect(isAutoRefresh) {
        while (isAutoRefresh) {
            loadLatestData()
            delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (latestData?.is_alert == true)
                    Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚡ Monitor Live Fumo",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = nodeRedUrl,
                    onValueChange = { nodeRedUrl = it },
                    label = { Text("URL Ngrok Node-RED") },
                    placeholder = { Text("https://xyz789.ngrok.app") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔄 Auto-refresh (5s)")
                    Switch(
                        checked = isAutoRefresh,
                        onCheckedChange = {
                            isAutoRefresh = it
                            if (it) loadLatestData()
                        },
                        enabled = nodeRedUrl.startsWith("http")
                    )
                }

                Button(
                    onClick = { loadLatestData() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nodeRedUrl.startsWith("http")
                ) {
                    Text("🔄 Aggiorna Ora")
                }

                if (lastUpdate.isNotEmpty()) {
                    Text(
                        text = lastUpdate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (latestData != null) {
            SmokeStatusCard(latestData!!)
        }
    }
}

@Composable
fun SmokeDataCard(data: SmokeDetectionData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (data.is_alert)
                Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🕐 ${data.time}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (data.is_alert) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "🚨",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("🔥 Valore: ${String.format("%.0f", data.sensor_value)}")
                    Text("📊 Status: ${data.alert_status}")
                }
                Column {
                    Text(
                        text = data.alert_text,
                        fontWeight = if (data.is_alert) FontWeight.Bold else FontWeight.Normal,
                        color = if (data.is_alert) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SmokeStatusCard(data: SmokeDetectionData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (data.is_alert)
                Color(0xFFFF5252) else Color(0xFF4CAF50)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (data.is_alert) "🚨" else "✅",
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = data.alert_text,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Valore Sensore",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${String.format("%.0f", data.sensor_value)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Stato Alert",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${data.alert_status}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ultimo aggiornamento: ${data.time}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}