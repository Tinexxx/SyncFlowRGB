package me.kavishdevar.openrgb.components

import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import me.kavishdevar.openrgb.network.connectToServer
import me.kavishdevar.openrgb.network.disconnectFromServer
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.network.OpenRGBClient

@Composable
fun ConnectionBar(
    client: MutableState<OpenRGBClient?>,
    connected: MutableState<Boolean>,
    currentServer: String,
    devices: MutableState<List<OpenRGBDevice>>,
    protocolVersion: Int,
    onRefresh: () -> Unit,
    onServerSelected: (String) -> Unit,
    availableServers: List<String>,
    onDragPull: () -> Unit,
    dragOffsetY: MutableFloatState,
    shouldReconnect: MutableState<Boolean>,
    dragThreshold: Float,

    sharedPref: SharedPreferences,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    currentServerState: MutableState<String>,

    connectionBarColorConnected: androidx.compose.ui.graphics.Color,
    connectionBarColorDisconnected: androidx.compose.ui.graphics.Color
) {
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Animate the drag offset
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetY.floatValue.coerceAtMost(100f),
        animationSpec = spring(dampingRatio = 0.5f)
    )

    if (showDisconnectDialog) {
        Dialog(onDismissRequest = { showDisconnectDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101B49))
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Disconnect?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Are you sure you want to disconnect from $currentServer?",
                        style = MaterialTheme.typography.bodyMedium, color = Color.LightGray, textAlign = TextAlign.Center)

                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = { showDisconnectDialog = false },
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = {
                                showDisconnectDialog = false
                                disconnectFromServer(client, connected, devices, shouldReconnect, userInitiated = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5555)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Yes", color = Color.White)
                        }
                        Spacer(modifier = Modifier.weight(1f))





                    }
                }
            }
        }

    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .offset(y = animatedOffset.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragOffsetY.floatValue > dragThreshold) { // Now using the parameter
                                onDragPull()
                            }
                            dragOffsetY.floatValue = 0f
                        },
                        onDrag = { _, dragAmount ->
                            if (dragAmount.y > 0f) {
                                dragOffsetY.floatValue += dragAmount.y
                            }
                        }
                    )
                }

                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current
                ) {
                    if (connected.value) { showDisconnectDialog = true }
                    else {
                        val lastServer = sharedPref.getString("last_connected_server", "")
                        if (!lastServer.isNullOrEmpty()) {
                            currentServerState.value = lastServer
                            connectToServer(
                                lastServer,
                                client,
                                connected,
                                devices,
                                snackbarHostState,
                                scope,
                                sharedPref,
                                shouldReconnect
                            )
                        }
                    }
                }    ,





            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (connected.value) connectionBarColorConnected else connectionBarColorDisconnected
            ),
            elevation = CardDefaults.cardElevation(4.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (connected.value) connectionBarColorConnected.copy(alpha = 0.85f)
                else connectionBarColorDisconnected.copy(alpha = 0.9f)
            )

        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (connected.value) Color(0xFF00E676) // Green when connected
                            else Color(0xFFFF5252) // Red when disconnected
                        )
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = currentServer.split(":").firstOrNull() ?: "Not connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (connected.value) "Connected • v$protocolVersion"
                        else "Tap to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

            }
        }
    }
}