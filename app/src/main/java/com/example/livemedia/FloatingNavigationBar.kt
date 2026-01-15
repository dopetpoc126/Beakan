package com.example.livemedia

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun FloatingNavigationBar(
    currentDestination: AppDestination,
    onDestinationChanged: (AppDestination) -> Unit
) {
    // Floating Pill Container
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, // Slightly distinct from background
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(percent = 50), // Fully rounded pill
        modifier = Modifier
            .padding(bottom = 24.dp) // Floating margin reduced
            .height(56.dp) // Compact Expressive height
            .width(200.dp) // Fixed compact width
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home Item
            NavBarItem(
                selected = currentDestination == AppDestination.MAIN,
                onClick = { onDestinationChanged(AppDestination.MAIN) },
                icon = Icons.Default.Home,
                selectedIcon = Icons.Filled.Home,
                label = "Home"
            )

            // Settings Item
            NavBarItem(
                selected = currentDestination == AppDestination.SETTINGS,
                onClick = { onDestinationChanged(AppDestination.SETTINGS) },
                icon = Icons.Default.Settings,
                selectedIcon = Icons.Filled.Settings,
                label = "Settings"
            )
        }
    }
}

@Composable
fun RowScope.NavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String
) {
    val scale by animateFloatAsState(targetValue = if (selected) 1.2f else 1f, label = "scale")
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(48.dp)
            .weight(1f) // Equal distribution
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Custom indication? or default ripple
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Selection Indicator (Pill)
        if (selected) {
             Box(
                 modifier = Modifier
                     .width(64.dp)
                     .height(32.dp)
                     .clip(RoundedCornerShape(16.dp))
                     .background(containerColor)
                     .align(Alignment.Center)
             )
        }

        Icon(
            imageVector = if (selected) selectedIcon else icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
        )
    }
}
