package com.example.livemedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.animation.OvershootInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
// removed rotate from drawscope as we are using Modifier.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
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
                AppScreen()
            }
        }
    }
}

@Composable
fun LiveMediaTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// -----------------------------------------------------------------------------
// APP SCREEN: Handles Splash -> Main -> Settings Transition
// -----------------------------------------------------------------------------
enum class AppDestination { SPLASH, MAIN, SETTINGS }

@Composable
fun AppScreen() {
    var destination by remember { mutableStateOf(AppDestination.SPLASH) }

    // Logic: Only show nav bar on MAIN and SETTINGS
    val showNavBar = destination != AppDestination.SPLASH
    
    Box(modifier = Modifier.fillMaxSize()) {
        // BACKGROUND
        DoodleBackground(modifier = Modifier.alpha(0.6f))

        // CONTENT
        Box(modifier = Modifier.fillMaxSize()) {
            when (destination) {
                AppDestination.SPLASH -> {
                    SplashScreen(onComplete = { destination = AppDestination.MAIN })
                }
                AppDestination.MAIN -> {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(durationMillis = 800))
                    ) {
                        MainContent()
                    }
                }
                AppDestination.SETTINGS -> {
                    SettingsScreen(onBack = { destination = AppDestination.MAIN })
                }
            }
        }
        
        // FLOATING NAV BAR (Bottom Center)
        if (showNavBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                FloatingNavigationBar(
                    currentDestination = destination,
                    onDestinationChanged = { destination = it }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// SPLASH SCREEN - PILL BURST INTO BACKGROUND
// -----------------------------------------------------------------------------
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    // Animation Controls
    val burstProgress = remember { Animatable(0f) }
    val centerPillScale = remember { Animatable(0f) }
    val centerPillAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    // Shape Definitions for Burst
    data class BurstShape(
        val type: ShapeType,
        val angle: Float,
        val distance: Float,
        val size: Dp,
        val colorParam: ColorScheme.() -> Color,
        val rotation: Float = 0f
    )
    
    // Configured shapes to burst out
    val shapes = remember {
        listOf(
            // Top Right Cluster
            BurstShape(ShapeType.Circle, 45f, 120f, 40.dp, { primary }),
            BurstShape(ShapeType.Pill, 30f, 180f, 60.dp, { tertiaryContainer }, 45f),
            
            // Right
            BurstShape(ShapeType.RoundRect, 0f, 150f, 30.dp, { secondary }),
            
            // Bottom Right
            BurstShape(ShapeType.Pill, 315f, 140f, 50.dp, { inversePrimary }, -45f),
            
            // Bottom Left
            BurstShape(ShapeType.Circle, 225f, 130f, 45.dp, { tertiary }),
            BurstShape(ShapeType.RoundRect, 240f, 190f, 35.dp, { secondaryContainer }, 15f),
            
            // Left
            BurstShape(ShapeType.Pill, 180f, 160f, 55.dp, { primaryContainer }),
            
            // Top Left
            BurstShape(ShapeType.Circle, 135f, 140f, 38.dp, { errorContainer }), // Pop of color
            BurstShape(ShapeType.Pill, 120f, 170f, 48.dp, { surfaceVariant }, -30f)
        )
    }

    LaunchedEffect(Unit) {
        // 1. Center Pill Pops In
        launch { centerPillScale.animateTo(1f, tween(400, easing = LinearOutSlowInEasing)) }
        launch { centerPillAlpha.animateTo(1f, tween(300)) }
        delay(500)

        // 2. BURST!
        launch { 
            burstProgress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) 
        }
        // Fade out center pill during burst
        launch {
            delay(100)
            centerPillAlpha.animateTo(0f, tween(300))
            centerPillScale.animateTo(3f, tween(400))
        }

        delay(400) // Wait a bit into burst
        
        // 3. Text Fades In
        textAlpha.animateTo(1f, tween(500))
        delay(800)
        
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        // --- BURST SHAPES ---
        if (burstProgress.value > 0f) {
            shapes.forEach { shape ->
                // Calculate position based on progress
                // Start near center (offset by small amount) -> End at distance
                val currentDist = (shape.distance * 2.5f) * burstProgress.value // Expand outwards 2.5x screen
                val rad = Math.toRadians(shape.angle.toDouble())
                val x = (currentDist * kotlin.math.cos(rad)).dp
                val y = (currentDist * kotlin.math.sin(rad)).dp // Inverted Y in standard coords, but Compose offset is +y down

                // Scale and Alpha lifecycle
                // Grow quickly then shrink? or just fly out?
                // Textures usually fly out and scale up slightly then fade
                val shapeAlpha = (1f - burstProgress.value).coerceIn(0f, 1f)
                val shapeScale = 0.5f + (burstProgress.value * 0.5f)

                Surface(
                    modifier = Modifier
                        .offset(x = x, y = -y) // Negative Y to match standard cartesian angle intuition
                        .rotate(shape.rotation + (burstProgress.value * 90f)) // Spin slightly
                        .scale(shapeScale)
                        .alpha(shapeAlpha)
                        .size(width = if(shape.type == ShapeType.Pill) shape.size * 2 else shape.size, height = shape.size),
                    shape = when(shape.type) {
                        ShapeType.Circle -> CircleShape
                        ShapeType.RoundRect -> RoundedCornerShape(25)
                        ShapeType.Pill -> CircleShape
                    },
                    color = shape.colorParam(MaterialTheme.colorScheme)
                ) {}
            }
        }

        // --- CENTER PILL (Initial) ---
        // This morphs/disappears nicely
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .width(120.dp)
                .height(48.dp)
                .scale(centerPillScale.value)
                .alpha(centerPillAlpha.value)
        ) {
            Box(contentAlignment = Alignment.Center) {
               // Initial simple icon content
               Row(verticalAlignment = Alignment.CenterVertically) {
                   Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                   Spacer(Modifier.width(8.dp))
                   Box(Modifier.size(24.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surface))
               }
            }
        }

        // --- FINAL TITLE ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(textAlpha.value)
        ) {
            Text(
                "Beakan",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

enum class ShapeType { Circle, RoundRect, Pill }

// Needed to map Android interpolator to Compose
fun android.view.animation.Interpolator.toEasing(): Easing = Easing { x -> getInterpolation(x) }


// -----------------------------------------------------------------------------
// MAIN CONTENT
// -----------------------------------------------------------------------------
@Composable
fun MainContent() {
    val context = LocalContext.current
    var hasListenerPermission by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(Unit) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                hasListenerPermission = isNotificationServiceEnabled(context)
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
            }
        }
        val view = (context as? android.app.Activity)?.window?.decorView
        view?.viewTreeObserver?.addOnWindowFocusChangeListener(listener)
        onDispose { view?.viewTreeObserver?.removeOnWindowFocusChangeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient Background
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.05f)) {
             drawCircle(Color.Gray, radius = size.width * 0.8f, center = Offset(size.width, 0f))
             drawCircle(Color.Gray, radius = size.width * 0.6f, center = Offset(0f, size.height))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header Row (Simple Title)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Text(
                    "Beakan",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // THE MAIN COMPONENT
            InteractiveShowcase()

            Spacer(modifier = Modifier.weight(1f))

            // Subtext removed
            
            // Spacer to prevent nav bar overlap
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Bottom Permission Alerts
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 100.dp), // Lifted above Nav Bar
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasListenerPermission) {
                PermissionCard(
                    text = "Sync Required: Tap to enable",
                    icon = Icons.Default.Notifications, 
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }

            if (!hasAccessibilityPermission) {
                PermissionCard(
                    text = "Hide Duplicates: Tap to enable",
                    icon = Icons.Default.Warning, // Use Warning or similar
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    text: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    containerColor: androidx.compose.ui.graphics.Color, 
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


// -----------------------------------------------------------------------------
// INTERACTIVE SHOWCASE (PHONE MOCKUP)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InteractiveShowcase() {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Pager for swipeable content
    val pagerState = rememberPagerState(pageCount = { 4 }) // OTP, DOWNLOAD, MEDIA, TORCH
    
    // =========================================================================
    // PRECISE CALCULATIONS
    // =========================================================================
    // Phone frame dimensions (1:2 aspect ratio, like modern phones)
    val phoneWidth = 280.dp
    val phoneHeight = 560.dp
    val phoneCornerRadius = 36.dp
    val phoneBorderWidth = 3.dp
    
    // Chip positioning
    val chipTopMargin = 12.dp  // Distance from phone top edge to chip top
    val chipHorizontalMargin = 15.dp  // Side margins when expanded
    
    // Collapsed chip (small pill in status bar)
    val chipCollapsedWidth = 120.dp
    val chipCollapsedHeight = 36.dp
    val chipCollapsedCorner = 18.dp  // Fully rounded ends
    
    // Expanded chip (must fit within phone width minus margins)
    val chipExpandedWidth = phoneWidth - (chipHorizontalMargin * 2) - (phoneBorderWidth * 2)  // 280 - 30 - 6 = 244dp
    val chipExpandedHeight = 160.dp
    val chipExpandedCorner = 24.dp
    
    // Status bar visual height (just a colored strip behind the chip)
    val statusBarHeight = chipCollapsedHeight + (chipTopMargin * 2)  // ~60dp
    
    // Content area starts below the expanded chip area
    val contentAreaTopPadding = chipTopMargin + chipExpandedHeight + 16.dp  // ~188dp from top
    // =========================================================================
    
    // Animated chip dimensions
    val chipWidth by animateDpAsState(
        targetValue = if (isExpanded) chipExpandedWidth else chipCollapsedWidth,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipWidth"
    )
    val chipHeight by animateDpAsState(
        targetValue = if (isExpanded) chipExpandedHeight else chipCollapsedHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipHeight"
    )
    val chipCorner by animateDpAsState(
        targetValue = if (isExpanded) chipExpandedCorner else chipCollapsedCorner,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipCorner"
    )
    
    // Subtle pulse for the collapsed chip
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Phone Frame
        Surface(
            modifier = Modifier
                .width(phoneWidth)
                .height(phoneHeight),
            shape = RoundedCornerShape(phoneCornerRadius),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(phoneBorderWidth, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            tonalElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // Layer 1: Status bar background (subtle tint at top)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                )
                
                // Layer 2: Mock phone content (behind chip, visible when collapsed)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = contentAreaTopPadding,
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 32.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Placeholder text lines
                    repeat(6) { i ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(
                                    when (i) {
                                        0 -> 0.7f
                                        5 -> 0.5f
                                        else -> 0.9f - (i * 0.05f)
                                    }
                                )
                                .height(10.dp),
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        ) {}
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Mock app icons grid (2 rows)
                    repeat(2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(4) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                ) {}
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Layer 3: THE DYNAMIC ISLAND CHIP (positioned absolutely at top-center)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = chipTopMargin),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .width(chipWidth)
                            .height(chipHeight)
                            .scale(if (!isExpanded) pulse else 1f)
                            .clickable { isExpanded = !isExpanded },
                        shape = RoundedCornerShape(chipCorner),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = if (isExpanded) 12.dp else 4.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (!isExpanded) {
                                // COLLAPSED: Show app icons row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Animated dot indicator
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                    )
                                    // Feature icons - representing each demo
                                    Icon(
                                        Icons.Outlined.Lock, // OTP/Security
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f)
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowDown, // Download
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f)
                                    )
                                    Icon(
                                        Icons.Default.PlayArrow, // Media
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f)
                                    )
                                }
                            } else {
                                // EXPANDED: Pager with demos
                                Column(modifier = Modifier.fillMaxSize()) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    ) { page ->
                                        DemoPageContent(page)
                                    }
                                    
                                    // Page indicators
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        repeat(4) { i ->
                                            val selected = pagerState.currentPage == i
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .size(if (selected) 8.dp else 6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        MaterialTheme.colorScheme.inverseOnSurface.copy(
                                                            alpha = if (selected) 1f else 0.35f
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Home indicator at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .width(120.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                )
            }
        }
        
        // Improved tagline below phone
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Your notifications, reimagined.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Tap to explore",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

// -----------------------------------------------------------------------------
// DEMO PAGES
// -----------------------------------------------------------------------------
@Composable
fun DemoPageContent(pageIndex: Int) {
    // Pages: 0=OTP, 1=DL, 2=MEDIA, 3=TORCH
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Row: Icon + Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, title) = when(pageIndex) {
                0 -> Icons.Outlined.Lock to "OTP"
                1 -> Icons.Default.KeyboardArrowDown to "Download"
                2 -> Icons.Filled.PlayArrow to "Media"
                else -> null to "Torch" // No icon for torch
            }
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                title,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Center Content
        when(pageIndex) {
            0 -> OtpDemo()
            1 -> DownloadDemo()
            2 -> MediaDemo()
            3 -> TorchDemo()
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun OtpDemo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // OTP Code
        Text(
            "748 102",
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        
        // Copy Button - Small pill style
        Surface(
            modifier = Modifier.clickable { },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check, // Using Check as "copy done" indicator
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Copy",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    }
}

@Composable
fun DownloadDemo() {
    val infiniteTransition = rememberInfiniteTransition("dl")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "prog"
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // File info row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "system_update.zip", 
                color = MaterialTheme.colorScheme.inverseOnSurface, 
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${(progress*100).toInt()}%", 
                color = MaterialTheme.colorScheme.tertiary, 
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.15f)
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            Surface(
                modifier = Modifier.clickable { },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.1f)
            ) {
                Icon(
                    Icons.Default.Close,
                    null,
                    modifier = Modifier.padding(6.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun MediaDemo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Album Art Placeholder
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PlayArrow, 
                    null, 
                    tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Song info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Blinding Lights", 
                color = MaterialTheme.colorScheme.inverseOnSurface, 
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "The Weeknd", 
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f), 
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        // Media controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous
            Icon(
                Icons.Default.KeyboardArrowLeft, 
                null, 
                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f), 
                modifier = Modifier.size(20.dp)
            )
            // Play/Pause
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow, 
                        null, 
                        tint = MaterialTheme.colorScheme.inverseSurface, 
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // Next
            Icon(
                Icons.Default.KeyboardArrowRight, 
                null, 
                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f), 
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TorchDemo() {
    var isOn by remember { mutableStateOf(true) }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Torch status text
        Column {
            Text(
                "Flashlight", 
                color = MaterialTheme.colorScheme.inverseOnSurface, 
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isOn) "On" else "Off", 
                color = if (isOn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f), 
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Toggle button
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable { isOn = !isOn },
            shape = CircleShape,
            color = if (isOn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.1f),
            border = if (!isOn) BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f)) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Simple dot indicator instead of icon
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOn) MaterialTheme.colorScheme.onTertiary 
                            else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

// ============== UTILS ==============

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(context.packageName) == true
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return flat?.contains(context.packageName) == true
}


