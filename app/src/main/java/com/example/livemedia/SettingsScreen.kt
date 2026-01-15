package com.example.livemedia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Re-import icons needed for the original design
import androidx.compose.material.icons.filled.Warning 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    
    // Feature Toggles (Broad)
    var isMediaEnabled by remember { mutableStateOf(prefs.isMediaEnabled) }
    var isOtpEnabled by remember { mutableStateOf(prefs.isOtpEnabled) }
    var isDownloadEnabled by remember { mutableStateOf(prefs.isDownloadEnabled) }
    var isTorchEnabled by remember { mutableStateOf(prefs.isTorchEnabled) }
    

    // Detailed Media Settings
    var showAlbumArt by remember { mutableStateOf(prefs.showAlbumArt) }
    var showArtistName by remember { mutableStateOf(prefs.showArtistName) }
    var showAlbumName by remember { mutableStateOf(prefs.showAlbumName) }
    var showActionButtons by remember { mutableStateOf(prefs.showActionButtons) }
    var showProgress by remember { mutableStateOf(prefs.showProgress) }
    var showMusicProvider by remember { mutableStateOf(prefs.showMusicProvider) }
    var showTimestamps by remember { mutableStateOf(prefs.showTimestamps) }
    var hideOnQs by remember { mutableStateOf(prefs.hideOnQs) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // 1. GLOBAL FEATURES
            SettingsSection(title = "Core Features") {
                SettingSwitch(
                    title = "Media Controls",
                    subtitle = "Enable dynamic music island",
                    icon = Icons.Default.PlayArrow,
                    checked = isMediaEnabled,
                    onCheckedChange = { isMediaEnabled = it; prefs.isMediaEnabled = it }
                )
                SettingSwitch(
                    title = "OTP Extraction",
                    subtitle = "Auto-copy 2FA codes",
                    icon = Icons.Outlined.Lock,
                    checked = isOtpEnabled,
                    onCheckedChange = { isOtpEnabled = it; prefs.isOtpEnabled = it }
                )
                SettingSwitch(
                    title = "Downloads",
                    subtitle = "Track download progress",
                    icon = Icons.Default.KeyboardArrowDown,
                    checked = isDownloadEnabled,
                    onCheckedChange = { isDownloadEnabled = it; prefs.isDownloadEnabled = it }
                )
                SettingSwitch(
                    title = "Torch Status",
                    subtitle = "Flashlight indicator",
                    icon = Icons.Default.Star,
                    checked = isTorchEnabled,
                    onCheckedChange = { isTorchEnabled = it; prefs.isTorchEnabled = it }
                )
            }

            // 2. MEDIA DETAIL SETTINGS
            if (isMediaEnabled) {
                SettingsSection(title = "Media Notification") {
                    SettingSwitch(
                        title = "Show Album Art",
                        subtitle = "Display cover art",
                        icon = Icons.Rounded.DateRange, // Placeholder icon
                        checked = showAlbumArt,
                        onCheckedChange = { showAlbumArt = it; prefs.showAlbumArt = it }
                    )
                    SettingSwitch(
                        title = "Show Artist Name",
                        subtitle = "Include artist below title",
                        icon = Icons.Rounded.Person,
                        checked = showArtistName,
                        onCheckedChange = { showArtistName = it; prefs.showArtistName = it }
                    )
                    SettingSwitch(
                        title = "Show Album Name",
                        subtitle = "Display album name",
                        icon = Icons.Rounded.Menu,
                        checked = showAlbumName,
                        onCheckedChange = { showAlbumName = it; prefs.showAlbumName = it }
                    )
                    SettingSwitch(
                        title = "Show Action Buttons",
                        subtitle = "Play, Pause, Next controls",
                        icon = Icons.Rounded.PlayArrow,
                        checked = showActionButtons,
                        onCheckedChange = { showActionButtons = it; prefs.showActionButtons = it }
                    )
                    SettingSwitch(
                        title = "Show Progress",
                        subtitle = "Show song progress bar",
                        icon = Icons.Rounded.Refresh,
                        checked = showProgress,
                        onCheckedChange = { showProgress = it; prefs.showProgress = it }
                    )
                    SettingSwitch(
                        title = "Show Music Provider",
                        subtitle = "Show app icon (Spotify, etc)",
                        icon = Icons.Rounded.Info,
                        checked = showMusicProvider,
                        onCheckedChange = { showMusicProvider = it; prefs.showMusicProvider = it }
                    )
                     SettingSwitch(
                        title = "Show Timestamps",
                        subtitle = "Elapsed and total duration",
                        icon = Icons.Rounded.Refresh, // Placeholder
                        checked = showTimestamps,
                        onCheckedChange = { showTimestamps = it; prefs.showTimestamps = it }
                    )
                }
            }


            
             // Helpful Footer
            Text(
                "Changes apply immediately to new notifications.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Spacer for floating nav bar
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}


@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
            } else null
        )
    }
}

@Composable
fun PillOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
