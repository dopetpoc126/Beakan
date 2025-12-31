package com.example.livemedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        setContent {
            LiveMediaTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun LiveMediaTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var hasListenerPermission by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    
    DisposableEffect(Unit) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                hasListenerPermission = isNotificationServiceEnabled(context)
            }
        }
        val view = (context as? android.app.Activity)?.window?.decorView
        view?.viewTreeObserver?.addOnWindowFocusChangeListener(listener)
        onDispose {
            view?.viewTreeObserver?.removeOnWindowFocusChangeListener(listener)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Themed Animated Background
            ThemedAnimatedBackground()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                
                // Hero Section
                HeroMorphingIcon()
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Beakan",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "OTPs • Downloads • Media",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    "All in your status bar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Status Card
                StatusCard(isActive = hasListenerPermission)
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Feature Showcase
                Text(
                    "How it works",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // OTP Showcase
                OtpShowcaseCard()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Download Showcase
                DownloadShowcaseCard()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Media Showcase
                MediaShowcaseCard()
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// ============== THEMED ANIMATED BACKGROUND ==============

@Composable
fun ThemedAnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "time"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "pulse"
    )
    
    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorSecondary = MaterialTheme.colorScheme.secondaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer
    
    Canvas(modifier = Modifier.fillMaxSize().alpha(0.4f)) {
        val w = size.width
        val h = size.height
        
        // Large gradient orbs
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colorPrimary, colorPrimary.copy(alpha = 0f)),
                center = Offset(w * 0.2f, h * 0.1f),
                radius = w * 0.6f
            ),
            center = Offset(w * 0.2f + sin(Math.toRadians(time.toDouble())).toFloat() * 50, h * 0.1f),
            radius = w * 0.5f * pulse
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colorSecondary, colorSecondary.copy(alpha = 0f)),
                center = Offset(w * 0.8f, h * 0.4f)
            ),
            center = Offset(w * 0.8f, h * 0.4f + sin(Math.toRadians((time + 120).toDouble())).toFloat() * 40),
            radius = w * 0.45f
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colorTertiary, colorTertiary.copy(alpha = 0f))
            ),
            center = Offset(w * 0.5f + sin(Math.toRadians((time + 60).toDouble())).toFloat() * 30, h * 0.85f),
            radius = w * 0.4f * pulse
        )
        
        // Floating themed shapes
        val shapeOffset = time / 10f
        
        // Lock shape (OTP)
        drawLockShape(
            center = Offset(w * 0.15f, h * 0.3f + sin(Math.toRadians((time * 2).toDouble())).toFloat() * 20),
            size = 40f,
            color = colorPrimary.copy(alpha = 0.6f),
            rotation = shapeOffset
        )
        
        // Download arrow (Downloads)
        drawDownloadArrow(
            center = Offset(w * 0.85f, h * 0.6f + sin(Math.toRadians((time * 1.5).toDouble())).toFloat() * 25),
            size = 35f,
            color = colorSecondary.copy(alpha = 0.6f),
            rotation = -shapeOffset / 2
        )
        
        // Music note (Media)
        drawMusicNote(
            center = Offset(w * 0.25f + sin(Math.toRadians(time.toDouble())).toFloat() * 15, h * 0.7f),
            size = 30f,
            color = colorTertiary.copy(alpha = 0.6f),
            rotation = shapeOffset * 0.5f
        )
        
        // Additional smaller shapes
        drawLockShape(
            center = Offset(w * 0.75f, h * 0.15f + sin(Math.toRadians((time * 3).toDouble())).toFloat() * 15),
            size = 25f,
            color = colorPrimary.copy(alpha = 0.4f),
            rotation = -shapeOffset * 2
        )
        
        drawDownloadArrow(
            center = Offset(w * 0.4f, h * 0.5f + sin(Math.toRadians((time * 2.5).toDouble())).toFloat() * 20),
            size = 20f,
            color = colorSecondary.copy(alpha = 0.4f),
            rotation = shapeOffset
        )
    }
}

// Shape drawing helpers
private fun DrawScope.drawLockShape(center: Offset, size: Float, color: Color, rotation: Float) {
    rotate(rotation, pivot = center) {
        // Lock body
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - size/2, center.y - size/4),
            size = androidx.compose.ui.geometry.Size(size, size * 0.75f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.15f)
        )
        // Lock shackle
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - size * 0.35f, center.y - size * 0.7f),
            size = androidx.compose.ui.geometry.Size(size * 0.7f, size * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size * 0.12f)
        )
    }
}

private fun DrawScope.drawDownloadArrow(center: Offset, size: Float, color: Color, rotation: Float) {
    rotate(rotation, pivot = center) {
        val path = Path().apply {
            // Arrow body
            moveTo(center.x, center.y - size * 0.6f)
            lineTo(center.x, center.y + size * 0.2f)
            // Arrow head
            moveTo(center.x - size * 0.4f, center.y - size * 0.1f)
            lineTo(center.x, center.y + size * 0.4f)
            lineTo(center.x + size * 0.4f, center.y - size * 0.1f)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size * 0.15f, cap = StrokeCap.Round))
        
        // Base line
        drawLine(color, Offset(center.x - size * 0.4f, center.y + size * 0.5f), 
                 Offset(center.x + size * 0.4f, center.y + size * 0.5f), strokeWidth = size * 0.12f)
    }
}

private fun DrawScope.drawMusicNote(center: Offset, size: Float, color: Color, rotation: Float) {
    rotate(rotation, pivot = center) {
        // Note head
        drawOval(
            color = color,
            topLeft = Offset(center.x - size * 0.3f, center.y + size * 0.2f),
            size = androidx.compose.ui.geometry.Size(size * 0.5f, size * 0.35f)
        )
        // Stem
        drawLine(color, Offset(center.x + size * 0.15f, center.y + size * 0.35f),
                 Offset(center.x + size * 0.15f, center.y - size * 0.5f), strokeWidth = size * 0.1f)
        // Flag
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x + size * 0.1f, center.y - size * 0.5f),
            size = androidx.compose.ui.geometry.Size(size * 0.35f, size * 0.4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size * 0.08f)
        )
    }
}

// ============== HERO MORPHING ICON ==============

@Composable
fun HeroMorphingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_morph")
    
    // Cycle 0 -> 1 -> 2 -> 0 (Wave -> Lock -> Download)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase"
    )
    
    // Determine current indices and fraction
    val currentIndex = phase.toInt() % 3
    val nextIndex = (currentIndex + 1) % 3
    val fraction = phase - phase.toInt()
    
    // Easing for snap effect
    val easedFraction = remember(fraction) {
        val t = fraction
        if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2) * (-2 * t + 2) * (-2 * t + 2) / 2
    }
    
    // Status Bar Chip Pill Container - BIGGER
    Surface(
        modifier = Modifier
            .width(96.dp)
            .height(48.dp),
        shape = RoundedCornerShape(50), // Pill / Stadium
        color = Color(0xFF121212), // Black "Status Bar" color
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(32.dp) // Bigger icon
            ) {
                val size = size.minDimension
                val scale = size / 100f
                
                // --- SHAPE DEFINITIONS (4 Segments Total: A1, A2, B1, B2) ---
                // Format: P0x, P0y, C0x, C0y, C1x, C1y, P1x, P1y
                
                // === WAVE (Sine) ===
                // Precise Sine Approximation
                // A1: Up Slope (10,50 -> 30,20) - Steep start, Flat peak
                // A2: Down Slope (30,20 -> 50,50) - Flat peak, Steep end
                // B1: Down Slope (50,50 -> 70,80) - Steep start, Flat trough
                // B2: Up Slope (70,80 -> 90,50) - Flat trough, Steep end
                val wave = listOf(
                    // Path A (Left Hump)
                    10f,50f, 20f,35f, 20f,20f, 30f,20f, // A1
                    30f,20f, 40f,20f, 40f,35f, 50f,50f, // A2
                    // Path B (Right Hump)
                    50f,50f, 60f,65f, 60f,80f, 70f,80f, // B1
                    70f,80f, 80f,80f, 80f,65f, 90f,50f  // B2
                )
                
                // === SHIELD (OTP Symbol) ===
                // A1: Top Left -> Top Center (20,20 -> 50,15)
                // A2: Top Center -> Top Right (50,15 -> 80,20)
                // B1: Right Side -> Bottom Tip (80,20 -> 50,90) - Curve it
                // B2: Bottom Tip -> Left Side (50,90 -> 20,20) - Curve it
                val shield = listOf(
                    // Path A (Top Rim)
                    20f,20f, 20f,20f, 35f,15f, 50f,15f, // A1
                    50f,15f, 65f,15f, 80f,20f, 80f,20f, // A2
                    // Path B (Body)
                    80f,20f, 80f,60f, 60f,85f, 50f,90f, // B1
                    50f,90f, 40f,85f, 20f,60f, 20f,20f  // B2
                )
                
                // === DOWNLOAD ===
                // A1: Top Shaft (50,15 -> 50,50)
                // A2: Bottom Shaft (50,50 -> 50,85)
                // B1: Left Wing (30,55 -> 50,85)
                // B2: Right Wing (50,85 -> 70,55)
                val dl = listOf(
                    // Path A (Shaft)
                    50f,15f, 50f,15f, 50f,50f, 50f,50f, // A1
                    50f,50f, 50f,50f, 50f,85f, 50f,85f, // A2
                    // Path B (Arrowhead)
                    30f,55f, 30f,55f, 40f,70f, 50f,85f, // B1
                    50f,85f, 60f,70f, 70f,55f, 70f,55f  // B2
                )
                
                val allShapes = listOf(wave, shield, dl)
                
                // LERP
                val start = allShapes[currentIndex]
                val end = allShapes[nextIndex]
                
                fun v(i: Int) = start[i] + (end[i] - start[i]) * easedFraction
                fun s(x: Float) = x * scale
                
                // DRAW
                // Combined Path for seamless joins
                val path = Path()
                // A1
                path.moveTo(s(v(0)), s(v(1)))
                path.cubicTo(s(v(2)), s(v(3)), s(v(4)), s(v(5)), s(v(6)), s(v(7)))
                // A2
                path.cubicTo(s(v(10)), s(v(11)), s(v(12)), s(v(13)), s(v(14)), s(v(15)))
                // B1 - Connects smoothly from A2 end
                // Note: For non-continuous shapes (if any), moveTo might be needed, 
                // but all current shapes are continuous closed or connected loops.
                // Shield: 80,20 connected. DL: A2 ends 50,85. B1 starts 30,55.
                // DOWNLOAD IS NOT CONTINUOUS!
                // We must check if B starts where A ends.
                
                // Check continuity at the junction (Index 14,15 vs 16,17)
                val aEnd = Offset(v(14), v(15))
                val bStart = Offset(v(16), v(17))
                val isContinuous = (aEnd - bStart).getDistance() < 1f
                
                if (!isContinuous) {
                    path.moveTo(s(v(16)), s(v(17)))
                }
                
                // B1
                path.cubicTo(s(v(18)), s(v(19)), s(v(20)), s(v(21)), s(v(22)), s(v(23)))
                // B2
                path.cubicTo(s(v(26)), s(v(27)), s(v(28)), s(v(29)), s(v(30)), s(v(31)))
                
                drawPath(path, Color.White, style = Stroke(width = s(8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ============== STATUS CARD ==============

@Composable
fun StatusCard(isActive: Boolean) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isActive) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        if (isActive) "Active" else "Setup Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isActive) "Live updates enabled" else "Enable notification access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (!isActive) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Access")
                }
            }
        }
    }
}

// ============== OTP SHOWCASE CARD ==============

@Composable
fun OtpShowcaseCard() {
    // Simple state loop for "bare minimum" complexity
    var isExpanded by remember { mutableStateOf(false) }
    
    // Sequential animation loop: 4s ON, 8s OFF
    LaunchedEffect(Unit) {
        while (true) {
            isExpanded = true
            delay(4000) // Show for 4s
            isExpanded = false
            delay(8000) // Wait 8s for other cards
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("OTP Detection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Phone mockup
            Surface(
                modifier = Modifier.fillMaxWidth(), // Height auto-adjusts or fix it
                color = Color(0xFF121212),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "12:00",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp) // Align visually with chip
                        )
                        
                        Spacer(Modifier.width(16.dp))
                        
                        // THE "BARE MINIMUM" MAGIC: animateContentSize
                        Surface(
                            modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                            shape = RoundedCornerShape(if (isExpanded) 16.dp else 50.dp), // Morph shape too
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            // Content switches simply based on state
                            // No pixel math. Just "Show this" or "Show that".
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("SECURITY CODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                                            Text("433 502", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    
                                    // Simulated Button
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "COPY CODE", 
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text("433502", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Automatically detects and offers to copy OTPs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============== DOWNLOAD SHOWCASE CARD ==============

@Composable
fun DownloadShowcaseCard() {
    var isExpanded by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "download_progress")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "progress"
    )
    
    LaunchedEffect(Unit) {
        delay(4000)
        while (true) {
            isExpanded = true
            delay(4000)
            isExpanded = false
            delay(8000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Text("Download Progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(Modifier.height(20.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF121212),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "12:00",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Surface(
                            modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                            shape = RoundedCornerShape(if (isExpanded) 16.dp else 50.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("DOWNLOADING...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
                                            Text("${progress.toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                    
                                    LinearProgressIndicator(
                                        progress = { progress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.1f)
                                    )
                                    
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "CANCEL", 
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${progress.toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Shows download progress. Cancel with a tap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============== MEDIA SHOWCASE CARD ==============

@Composable
fun MediaShowcaseCard() {
    var isExpanded by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "media_play")
    val playProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "play"
    )
    
    LaunchedEffect(Unit) {
        delay(8000)
        while (true) {
            isExpanded = true
            delay(4000)
            isExpanded = false
            delay(8000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Text("Media Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(Modifier.height(20.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF121212),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "12:00",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        Spacer(Modifier.width(16.dp))
                        
                        Surface(
                            modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                            shape = RoundedCornerShape(if (isExpanded) 16.dp else 50.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            if (isExpanded) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Song Title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                            Text("Artist Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7f))
                                        }
                                    }
                                    
                                    LinearProgressIndicator(
                                        progress = { playProgress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.1f)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Icon(Icons.Default.ArrowBack, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7f))
                                        Icon(Icons.Default.PlayArrow, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Icon(Icons.Default.ArrowForward, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7f))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Playing...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Text("Control music from anywhere. Fluid animations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============== UTILS ==============

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}


