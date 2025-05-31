package com.example.progetto_yatch.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.progetto_yatch.R

@Composable
fun WelcomeScreen(
    onNavigateToLoading: () -> Unit
) {
    // Animazione per il logo
    val infiniteTransition = rememberInfiniteTransition(label = "logo_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )

    // Gradiente di sfondo viola come nel logo
    val purpleGradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF8E2DE2),  // Viola più chiaro al centro
            Color(0xFF4A00E0),  // Viola scuro ai bordi
            Color(0xFF2A0845)   // Viola molto scuro
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
            // Logo animato - per ora useremo un placeholder
            // Sostituisci con: Image(painterResource(R.drawable.app_logo), ...)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(60.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👁️",
                    fontSize = 60.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Titolo dell'app
            Text(
                text = "Yacht Security",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sistema di sicurezza avanzato\nper il tuo yacht",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Pulsante di ingresso
            Button(
                onClick = onNavigateToLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF4A00E0)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "🚀 Inizia",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Info versione
            Text(
                text = "v1.0.0 • Powered by AI",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // Elementi decorativi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            Color.White.copy(alpha = if (index == 0) 1f else 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}