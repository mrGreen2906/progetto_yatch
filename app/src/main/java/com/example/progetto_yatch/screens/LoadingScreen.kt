package com.example.progetto_yatch.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(
    onLoadingComplete: () -> Unit
) {
    // Animazione di rotazione per l'icona di caricamento
    val infiniteTransition = rememberInfiniteTransition(label = "loading_animation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_animation"
    )

    // Progresso del caricamento
    var loadingProgress by remember { mutableStateOf(0f) }
    var loadingText by remember { mutableStateOf("Inizializzazione Alertify...") }

    // Simulazione del caricamento con messaggi aggiornati
    LaunchedEffect(Unit) {
        val steps = listOf(
            "Inizializzazione Alertify..." to 0.1f,
            "Connessione sensori fumo..." to 0.25f,
            "Configurazione telecamere..." to 0.4f,
            "Verifica sistema rilevamento..." to 0.6f,
            "Attivazione allarmi..." to 0.8f,
            "Caricamento interfaccia..." to 0.95f,
            "Alertify pronto!" to 1.0f
        )

        for ((text, progress) in steps) {
            loadingText = text

            // Animazione fluida del progresso
            val currentProgress = loadingProgress
            val targetProgress = progress
            val duration = 800L
            val startTime = System.currentTimeMillis()

            while (loadingProgress < targetProgress) {
                val elapsed = System.currentTimeMillis() - startTime
                val progressRatio = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                loadingProgress = currentProgress + (targetProgress - currentProgress) * progressRatio
                delay(16) // ~60 FPS
            }

            delay(500) // Pausa tra i passaggi
        }

        delay(1000) // Pausa finale
        onLoadingComplete()
    }

    // Gradiente di sfondo
    val purpleGradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF8E2DE2),
            Color(0xFF4A00E0),
            Color(0xFF2A0845)
        ),
        radius = 1000f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(purpleGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo con animazione di rotazione (aggiornato per Alertify)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .rotate(rotation)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(50.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🚨",
                    fontSize = 50.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Testo di caricamento
            Text(
                text = loadingText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Barra di progresso personalizzata
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(6.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadingProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color(0xFFE1F5FE)
                                    )
                                ),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(loadingProgress * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Light
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Indicatori di stato aggiornati per Alertify
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusIndicator("🔥", loadingProgress > 0.2f) // Sensori fumo
                StatusIndicator("📹", loadingProgress > 0.4f) // Telecamere
                StatusIndicator("🚨", loadingProgress > 0.6f) // Sistema allarmi
                StatusIndicator("📱", loadingProgress > 0.8f) // App
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    icon: String,
    isActive: Boolean
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                if (isActive) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f)
        )
    }
}