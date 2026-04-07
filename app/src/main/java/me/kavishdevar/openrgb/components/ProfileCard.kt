package me.kavishdevar.openrgb.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.kavishdevar.openrgb.R
import me.kavishdevar.openrgb.models.OpenRGBProfile

@Composable
fun ProfileCard(
    profile: OpenRGBProfile,
    deviceName: String?,
    modeName: String?,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lockOffset = 200f
    var showConfirmDialog by remember { mutableStateOf(false) }
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(profile.name) {
        offsetX.snapTo(0f)
        showConfirmDialog = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            offsetX.animateTo(lockOffset, tween(200))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showConfirmDialog = true
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Red),
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(start = 20.dp)
                    .size(32.dp)
            )
        }

        Card(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    deviceName?.let { Text("Device: $it", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
                    modeName?.let { Text("Mode: $it", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
                }
                IconButton(onClick = { if (!profile.isActive) onPlayClick() }) {
                    Icon(
                        painter = painterResource(
                            id = if (profile.isActive) R.drawable.pause else R.drawable.play
                        ),
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                scope.launch { offsetX.animateTo(0f, tween(200)) }
            },
            title = { Text("Delete Profile?", color = Color.White) },
            text = { Text("Are you sure you want to delete this profile?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClick()
                        showConfirmDialog = false
                        scope.launch { offsetX.snapTo(0f) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Confirm", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmDialog = false
                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                    }
                ) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
