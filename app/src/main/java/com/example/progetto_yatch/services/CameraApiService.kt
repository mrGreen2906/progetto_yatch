package com.example.progetto_yatch.services

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException

// Modelli di dati per l'API della telecamera
data class CameraStatus(
    val camera: CameraInfo,
    val recognition: RecognitionStats,
    val health: HealthStats,
    val running: Boolean
)

data class CameraInfo(
    val connected: Boolean,
    val info: CameraDetails
)

data class CameraDetails(
    val width: Int,
    val height: Int,
    val fps_setting: Int,
    val fps_actual: Double,
    val frame_count: Int,
    val backend: Int
)

data class RecognitionStats(
    val known_persons: Int,
    val complete_persons: Int,
    val incomplete_persons: Int,
    val total_features: Int,
    val total_images: Int,
    val admin_count: Int,
    val owner_count: Int,
    val guest_count: Int,
    val recent_detections: Int,
    val pending_alerts: Int,
    val avg_recognition_time_ms: Double,
    val cache_size: Int
)

data class HealthStats(
    val uptime_seconds: Int,
    val camera_failures: Int,
    val recognition_errors: Int,
    val memory_warnings: Int,
    val memory_usage_percent: Double
)

data class DetectionAlert(
    val id: String,
    val timestamp: String,
    val type: String,
    val message: String,
    val confidence: Double,
    val quality: Double,
    val location: List<Int>,
    val severity: String,
    val area: String
)

data class RecentDetection(
    val timestamp: String,
    val name: String,
    val confidence: Double,
    val access_level: String,
    val location: List<Int>,
    val quality: Double,
    val is_unknown: Boolean
)

data class KnownPerson(
    val name: String,
    val access_level: String,
    val features_count: Int,
    val image_count: Int,
    val avg_quality: Double,
    val added_at: String,
    val is_complete: Boolean
)

object CameraApiService {
    // URL fisso della telecamera
    private const val BASE_URL = "https://worm-shining-accurately.ngrok-free.app"
    private const val STREAM_URL = "$BASE_URL/video_feed"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Ottieni stato del sistema telecamera
    suspend fun getCameraStatus(): Result<CameraStatus> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/security/status")
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject

                    if (json["success"]?.jsonPrimitive?.boolean == true) {
                        val systemData = json["system"]?.jsonObject

                        val cameraInfo = systemData?.get("camera")?.jsonObject
                        val recognitionData = systemData?.get("recognition")?.jsonObject
                        val healthData = systemData?.get("health")?.jsonObject

                        val status = CameraStatus(
                            camera = CameraInfo(
                                connected = cameraInfo?.get("connected")?.jsonPrimitive?.boolean ?: false,
                                info = CameraDetails(
                                    width = cameraInfo?.get("info")?.jsonObject?.get("width")?.jsonPrimitive?.int ?: 0,
                                    height = cameraInfo?.get("info")?.jsonObject?.get("height")?.jsonPrimitive?.int ?: 0,
                                    fps_setting = cameraInfo?.get("info")?.jsonObject?.get("fps_setting")?.jsonPrimitive?.int ?: 0,
                                    fps_actual = cameraInfo?.get("info")?.jsonObject?.get("fps_actual")?.jsonPrimitive?.double ?: 0.0,
                                    frame_count = cameraInfo?.get("info")?.jsonObject?.get("frame_count")?.jsonPrimitive?.int ?: 0,
                                    backend = cameraInfo?.get("info")?.jsonObject?.get("backend")?.jsonPrimitive?.int ?: 0
                                )
                            ),
                            recognition = RecognitionStats(
                                known_persons = recognitionData?.get("known_persons")?.jsonPrimitive?.int ?: 0,
                                complete_persons = recognitionData?.get("complete_persons")?.jsonPrimitive?.int ?: 0,
                                incomplete_persons = recognitionData?.get("incomplete_persons")?.jsonPrimitive?.int ?: 0,
                                total_features = recognitionData?.get("total_features")?.jsonPrimitive?.int ?: 0,
                                total_images = recognitionData?.get("total_images")?.jsonPrimitive?.int ?: 0,
                                admin_count = recognitionData?.get("admin_count")?.jsonPrimitive?.int ?: 0,
                                owner_count = recognitionData?.get("owner_count")?.jsonPrimitive?.int ?: 0,
                                guest_count = recognitionData?.get("guest_count")?.jsonPrimitive?.int ?: 0,
                                recent_detections = recognitionData?.get("recent_detections")?.jsonPrimitive?.int ?: 0,
                                pending_alerts = recognitionData?.get("pending_alerts")?.jsonPrimitive?.int ?: 0,
                                avg_recognition_time_ms = recognitionData?.get("avg_recognition_time_ms")?.jsonPrimitive?.double ?: 0.0,
                                cache_size = recognitionData?.get("cache_size")?.jsonPrimitive?.int ?: 0
                            ),
                            health = HealthStats(
                                uptime_seconds = healthData?.get("uptime_seconds")?.jsonPrimitive?.int ?: 0,
                                camera_failures = healthData?.get("camera_failures")?.jsonPrimitive?.int ?: 0,
                                recognition_errors = healthData?.get("recognition_errors")?.jsonPrimitive?.int ?: 0,
                                memory_warnings = healthData?.get("memory_warnings")?.jsonPrimitive?.int ?: 0,
                                memory_usage_percent = healthData?.get("memory_usage_percent")?.jsonPrimitive?.double ?: 0.0
                            ),
                            running = json["running"]?.jsonPrimitive?.boolean ?: false
                        )

                        Result.success(status)
                    } else {
                        Result.failure(Exception("API response indicates failure"))
                    }
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ottieni allarmi pendenti
    suspend fun getPendingAlerts(): Result<List<DetectionAlert>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/security/alerts")
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject

                    if (json["success"]?.jsonPrimitive?.boolean == true) {
                        val alertsArray = json["alerts"]?.jsonArray ?: JsonArray(emptyList())

                        val alerts = alertsArray.map { alertElement ->
                            val alert = alertElement.jsonObject
                            DetectionAlert(
                                id = alert["id"]?.jsonPrimitive?.content ?: "",
                                timestamp = alert["timestamp"]?.jsonPrimitive?.content ?: "",
                                type = alert["type"]?.jsonPrimitive?.content ?: "",
                                message = alert["message"]?.jsonPrimitive?.content ?: "",
                                confidence = alert["confidence"]?.jsonPrimitive?.double ?: 0.0,
                                quality = alert["quality"]?.jsonPrimitive?.double ?: 0.0,
                                location = alert["location"]?.jsonArray?.map {
                                    it.jsonPrimitive.int
                                } ?: emptyList(),
                                severity = alert["severity"]?.jsonPrimitive?.content ?: "",
                                area = alert["area"]?.jsonPrimitive?.content ?: ""
                            )
                        }

                        Result.success(alerts)
                    } else {
                        Result.failure(Exception("API response indicates failure"))
                    }
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ottieni rilevamenti recenti
    suspend fun getRecentDetections(): Result<List<RecentDetection>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/security/detections")
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject

                    if (json["success"]?.jsonPrimitive?.boolean == true) {
                        val detectionsArray = json["detections"]?.jsonArray ?: JsonArray(emptyList())

                        val detections = detectionsArray.map { detectionElement ->
                            val detection = detectionElement.jsonObject
                            RecentDetection(
                                timestamp = detection["timestamp"]?.jsonPrimitive?.content ?: "",
                                name = detection["name"]?.jsonPrimitive?.content ?: "",
                                confidence = detection["confidence"]?.jsonPrimitive?.double ?: 0.0,
                                access_level = detection["access_level"]?.jsonPrimitive?.content ?: "",
                                location = detection["location"]?.jsonArray?.map {
                                    it.jsonPrimitive.int
                                } ?: emptyList(),
                                quality = detection["quality"]?.jsonPrimitive?.double ?: 0.0,
                                is_unknown = detection["is_unknown"]?.jsonPrimitive?.boolean ?: false
                            )
                        }

                        Result.success(detections)
                    } else {
                        Result.failure(Exception("API response indicates failure"))
                    }
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ottieni persone conosciute
    suspend fun getKnownPersons(): Result<List<KnownPerson>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/security/persons")
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject

                    if (json["success"]?.jsonPrimitive?.boolean == true) {
                        val personsArray = json["persons"]?.jsonArray ?: JsonArray(emptyList())

                        val persons = personsArray.map { personElement ->
                            val person = personElement.jsonObject
                            KnownPerson(
                                name = person["name"]?.jsonPrimitive?.content ?: "",
                                access_level = person["access_level"]?.jsonPrimitive?.content ?: "",
                                features_count = person["features_count"]?.jsonPrimitive?.int ?: 0,
                                image_count = person["image_count"]?.jsonPrimitive?.int ?: 0,
                                avg_quality = person["avg_quality"]?.jsonPrimitive?.double ?: 0.0,
                                added_at = person["added_at"]?.jsonPrimitive?.content ?: "",
                                is_complete = person["is_complete"]?.jsonPrimitive?.boolean ?: false
                            )
                        }

                        Result.success(persons)
                    } else {
                        Result.failure(Exception("API response indicates failure"))
                    }
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Pulisci allarmi
    suspend fun clearAlerts(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/security/alerts/clear")
                .post(RequestBody.create(null, ""))
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject
                    Result.success(json["success"]?.jsonPrimitive?.boolean ?: false)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Riconnetti telecamera
    suspend fun reconnectCamera(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/security/camera/reconnect")
                .post(RequestBody.create(null, ""))
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json.parseToJsonElement(responseBody).jsonObject
                    Result.success(json["success"]?.jsonPrimitive?.boolean ?: false)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // URL dello stream video
    fun getStreamUrl(): String = STREAM_URL

    // URL dell'interfaccia web completa
    fun getWebInterfaceUrl(): String = BASE_URL
}