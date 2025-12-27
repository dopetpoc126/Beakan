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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

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
                AppNavigation()
            }
        }
    }
}

enum class AppScreen {
    Main, Info
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(AppScreen.Main) }
    
    Crossfade(targetState = currentScreen, label = "screen_nav") { screen ->
        when (screen) {
            AppScreen.Main -> MainScreen(onNavigateToInfo = { currentScreen = AppScreen.Info })
            AppScreen.Info -> InfoScreen(onBack = { currentScreen = AppScreen.Main })
        }
    }
}

@Composable
fun LiveMediaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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

fun MainScreen(onNavigateToInfo: () -> Unit) {
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
            AnimatedMusicBackground()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                
                // Hero icon
                AnimatedBeakanIcon()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                "Beakan",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                "See what's playing in your status bar",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Status card
            StatusCard(isActive = hasListenerPermission)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Features
            FeatureList()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedButton(
                onClick = onNavigateToInfo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("How it works")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    }
}

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
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (isActive) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isActive) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isActive) 
                                MaterialTheme.colorScheme.onPrimary 
                            else 
                                MaterialTheme.colorScheme.onError
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        if (isActive) "Active" else "Setup required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        if (isActive) "Live updates enabled" else "Enable notification access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else 
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            if (!isActive) {
                Spacer(modifier = Modifier.height(16.dp))
                
                FilledTonalButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Enable")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How it works") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Status Bar Chip Animation
            Spacer(modifier = Modifier.height(16.dp))
            Text("How it works", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))

            Text("1. Shows song info in status bar", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusBarChipAnimation()
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // 2. Expanded Controls Animation
            Text("2. Expand for controls", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            ExpandedControlsAnimation()
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun FeatureList() {
    val features = listOf(
        FeatureItem(
            Icons.Outlined.Notifications,
            "Status bar updates",
            "Song title shown as a live chip"
        ),
        FeatureItem(
            Icons.Outlined.Refresh,
            "Auto detection",
            "Switches between media apps automatically"
        ),
        FeatureItem(
            Icons.Outlined.PlayArrow,
            "Real-time sync",
            "Progress bar updates every second"
        )
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        features.forEach { feature ->
            ListItem(
                headlineContent = {
                    Text(
                        feature.title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                supportingContent = {
                    Text(
                        feature.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        feature.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}

@Composable
fun AnimatedBeakanIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val duration = 700
    
    // Create 5 animated values for bar heights with different phases
    // We use a combination of different durations and start offsets to create a seemingly random "voice" wave
    val heights = listOf(
        infiniteTransition.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse, StartOffset(0)), "1"),
        infiniteTransition.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(duration + 150, easing = LinearEasing), RepeatMode.Reverse, StartOffset(200)), "2"),
        infiniteTransition.animateFloat(0.5f, 1.0f, infiniteRepeatable(tween(duration - 100, easing = LinearEasing), RepeatMode.Reverse, StartOffset(400)), "3"),
        infiniteTransition.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(duration + 200, easing = LinearEasing), RepeatMode.Reverse, StartOffset(100)), "4"),
        infiniteTransition.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(duration + 50, easing = LinearEasing), RepeatMode.Reverse, StartOffset(300)), "5")
    )

    Surface(
        modifier = Modifier.size(width = 120.dp, height = 72.dp),
        shape = RoundedCornerShape(100.dp), // Pill shape
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            heights.forEach { height ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(6.dp)
                        .fillMaxHeight(height.value * 0.6f)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onPrimaryContainer)
                )
            }
        }
    }
}

@Composable
fun AnimatedMusicBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "background_anim")
    
    // Faster, livelier animations
    val orb1Anim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "orb1"
    )
    
    val orb2Anim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "orb2"
    )
    
    val orb3Anim by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "orb3"
    )
    
    val particleAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), label = "particle"
    )
    
    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorSecondary = MaterialTheme.colorScheme.secondaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer
    val colorError = MaterialTheme.colorScheme.errorContainer
    
    // Subtle background
    Canvas(modifier = Modifier.fillMaxSize().alpha(0.35f)) {
        val width = size.width
        val height = size.height
        
        // Large Sphere 1 (Top Left)
        drawCircle(
            color = colorPrimary.copy(alpha = 0.5f),
            radius = size.width * 0.55f,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.2f + (width * 0.3f * orb1Anim),
                y = height * 0.1f + (height * 0.2f * orb2Anim)
            )
        )
        
        // Large Sphere 2 (Bottom Right)
        drawCircle(
            color = colorSecondary.copy(alpha = 0.5f),
            radius = size.width * 0.5f,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.8f - (width * 0.3f * orb2Anim),
                y = height * 0.8f - (height * 0.2f * orb1Anim)
            )
        )
        
        // Pulse Sphere (Center-ish)
        drawCircle(
            color = colorTertiary.copy(alpha = 0.4f),
            radius = size.width * 0.3f + (size.width * 0.2f * orb3Anim),
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.5f + (width * 0.1f * orb1Anim), 
                y = height * 0.4f + (height * 0.2f * orb3Anim)
            )
        )
        
        // Small "Note" Orb (Top Right)
        drawCircle(
            color = colorError.copy(alpha = 0.3f),
            radius = size.width * 0.15f,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.8f - (width * 0.1f * particleAnim),
                y = height * 0.2f + (height * 0.1f * orb3Anim)
            )
        )
        
        // Floating Particles (representing beats/debris)
        val particleRadius = size.width * 0.02f
        drawCircle(
            color = colorPrimary,
            radius = particleRadius,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.1f + (width * 0.8f * particleAnim),
                y = height * 0.6f + (height * 0.1f * orb2Anim)
            )
        )
        drawCircle(
            color = colorSecondary,
            radius = particleRadius * 1.5f,
            center = androidx.compose.ui.geometry.Offset(
                x = width * 0.9f - (width * 0.6f * particleAnim),
                y = height * 0.3f + (height * 0.4f * orb1Anim)
            )
        )
    }
}

@Composable
fun StatusBarChipAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "chip_anim")
    
    // Animate chip expansion
    val widthFraction by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0 // Start hidden
                0f at 500 // Wait
                0.2f at 800 with FastOutSlowInEasing // Icon appears
                1f at 1500 with LinearOutSlowInEasing // Chip expands
                1f at 3500 // Stay visible
                0f at 4000 // Reset
            },
            repeatMode = RepeatMode.Restart
        ), label = "width"
    )
    
    // Simulate Status Bar
    Surface(
        modifier = Modifier
            .width(300.dp)
            .height(50.dp),
        color = Color.Black,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // Simulated Clock
            Text(
                "12:00",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 14.dp)
            )
            
            // The Chip
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart // Status bar chip typically appears next to clock or center
            ) {
                 Row(
                    modifier = Modifier
                        .padding(start = 40.dp, top = 8.dp) // Offset from clock
                        .height(28.dp)
                        .fillMaxWidth(widthFraction * 0.6f) // Max width constraint
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (widthFraction > 0.1f) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                        Icons.Default.PlayArrow, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    if (widthFraction > 0.3f) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Song Title...",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(20.dp))
    
    // Phone Frame Bottom (Aesthetic only)
    Surface(
        modifier = Modifier
            .width(300.dp)
            .height(20.dp),
        color = Color.LightGray.copy(alpha = 0.2f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    ) {}
}

@Composable
fun ExpandedControlsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "expanded_anim")
    
    // Animate expansion progress
    val expandProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                0f at 0 // Start collapsed (chip)
                0f at 1000 // Wait
                1f at 1800 with LinearOutSlowInEasing // Expand to card
                1f at 4500 // Hold expanded
                0f at 5000 with FastOutSlowInEasing // Collapse
                0f at 6000 // Reset
            },
            repeatMode = RepeatMode.Restart
        ), label = "expand"
    )

    // Simulate Status Bar / Notification Shade area
    Surface(
        modifier = Modifier
            .width(300.dp)
            .height(220.dp),
        color = Color.Black,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
             modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
             contentAlignment = Alignment.TopStart
        ) {
            // Simulated Clock (Context)
            Text(
                "12:00",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 14.dp)
            )

            // The Expanding Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp), // Align top with status bar
                contentAlignment = Alignment.TopStart // Align left (next to clock)
            ) {
                // Height morphs: 30dp -> 170dp (enough for all content)
                val currentHeight = 30.dp + (140.dp * expandProgress)
                
                // Width morphs: 100dp -> 225dp (Fits in remaining space: 300 - 32(margin) - 46(offset) = ~222 left)
                // We offset by 46dp to clear the "12:00" clock
                val currentWidth = 100.dp + (125.dp * expandProgress) 
                
                val cornerRadius = 50.dp - (34.dp * expandProgress) // 50dp -> 16dp
                
                Surface(
                    modifier = Modifier
                        .padding(start = 46.dp) // Offset to sit right of the clock
                        .width(currentWidth)
                        .height(currentHeight),
                    shape = RoundedCornerShape(cornerRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                         // 1. Collapsed State Content (Chip)
                         Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp) // Inner padding
                                .alpha((1f - expandProgress * 3f).coerceIn(0f, 1f)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start // Left align text in chip
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Song Title", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        
                        // 2. Expanded State Content (Media Controls)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .alpha(((expandProgress - 0.3f) * 2f).coerceIn(0f, 1f)),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Album Art Placeholder
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.padding(12.dp))
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text("Song Title", style = MaterialTheme.typography.titleMedium, maxLines=1)
                                    Text("Artist Name", style = MaterialTheme.typography.bodySmall, maxLines=1)
                                }
                            }
                            
                            // Precise centering: 31dp above and below progress bar
                            Spacer(modifier = Modifier.height(31.dp))
                            
                            // Progress Bar (4dp height)
                            LinearProgressIndicator(
                                progress = { 0.6f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.2f)
                            )
                            
                            Spacer(modifier = Modifier.height(31.dp))
                            
                            // Controls (24dp height)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                 Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(24.dp))
                                 Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp))
                                 Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
