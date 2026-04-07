package me.kavishdevar.openrgb.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import me.kavishdevar.openrgb.components.ModernDeviceCard
import me.kavishdevar.openrgb.models.OpenRGBDevice

@Composable
fun DirectControlScreen(
    devices: List<OpenRGBDevice>,
    onClick: (OpenRGBDevice, IntOffset, IntSize) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(devices) { device ->
            ModernDeviceCard(
                device = device,
                onClick = { /* handled in measured card below */ },
                onPositionedClick = { offset, size ->
                    onClick(device, offset, size)
                }
            )
        }
    }
}
