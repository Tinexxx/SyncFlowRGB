package me.kavishdevar.openrgb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.kavishdevar.openrgb.logic.getDeviceIcon
import me.kavishdevar.openrgb.models.OpenRGBDevice


@Composable
fun ModernDeviceCard(
    device: OpenRGBDevice,
    onClick: () -> Unit,
    onPositionedClick: (IntOffset, IntSize) -> Unit
) {
    var lastOffset by remember { mutableStateOf(IntOffset(0,0)) }
    var lastSize by remember { mutableStateOf(IntSize(0,0)) }

    Card(
        onClick = {
            // Use last measured values to open overlay
            onPositionedClick(lastOffset, lastSize)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.Transparent)
            .onGloballyPositioned { coords ->
                val pos = coords.localToWindow(Offset(0f, 0f))
                // convert to IntOffset in window coords
                lastOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                lastSize = IntSize(coords.size.width, coords.size.height)
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = getDeviceIcon(device.type)),
                contentDescription = device.type.name,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFCCCCCC)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD7D6D6)
                )
                Text(
                    text = device.type.name.replace("DEVICE_TYPE_", "").lowercase().replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBBBBBB)
                )
            }
        }
    }
}