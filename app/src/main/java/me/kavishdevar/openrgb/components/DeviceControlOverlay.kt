package me.kavishdevar.openrgb.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kavishdevar.openrgb.logic.getDeviceIcon
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.network.OpenRGBClient
import me.kavishdevar.openrgb.ui_logic.MainViewModel

@Composable
fun DeviceControlOverlay(
    device: OpenRGBDevice,
    client: OpenRGBClient?,
    deviceIndex: Int,       // index of the device in the devices list
    viewModel: MainViewModel,
    startOffset: IntOffset,
    startSize: IntSize,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current

    // animation animatables for x,y,w,h (in px floats)
    val animX = remember { Animatable(startOffset.x.toFloat()) }
    val animY = remember { Animatable(startOffset.y.toFloat()) }
    val animW = remember { Animatable(startSize.width.toFloat()) }
    val animH = remember { Animatable(startSize.height.toFloat()) }

    // background alpha for fade in/out (0 -> 1)
    val bgAlpha = remember { Animatable(0f) }

    // content scale & alpha for subtle pop/fade
    val contentScale = remember { Animatable(0.99f) }
    val contentAlpha = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    // final geometry: keep the same width as the ModernDeviceCard and keep top aligned with card
    val finalX = startOffset.x.toFloat()            // match card left
    val finalW = startSize.width.toFloat()          // match card width
    val topY = startOffset.y.toFloat()              // card top Y
    // compute final height so the overlay expands downward but not off-screen
    val maxAvailableBelow = screenHeightPx - topY - with(density) { 24.dp.toPx() } // leave a small bottom margin
    val desiredH = screenHeightPx * 0.65f           // preferred expansion height
    val finalH = minOf(maxAvailableBelow, desiredH).coerceAtLeast(startSize.height.toFloat())

    // how dark scrim becomes at full bgAlpha
    val scrimMaxAlpha = 0.55f

    // Animate to final (top-left fixed horizontally)
    LaunchedEffect(Unit) {
        val duration = 420
        launch { bgAlpha.animateTo(1f, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
        launch { animX.animateTo(finalX, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
        launch { animW.animateTo(finalW, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
        launch { animY.animateTo(topY, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
        launch { animH.animateTo(finalH, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
        launch { contentScale.animateTo(1f, animationSpec = tween(durationMillis = duration / 2, easing = FastOutSlowInEasing)) }
        launch { contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = duration / 2, easing = FastOutSlowInEasing)) }
    }

    fun closeReverse() {
        scope.launch {
            val duration = 300
            launch { contentAlpha.animateTo(0f, animationSpec = tween(durationMillis = duration / 2)) }
            launch { contentScale.animateTo(0.99f, animationSpec = tween(durationMillis = duration / 2)) }
            launch { bgAlpha.animateTo(0f, animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
            // collapse height back to original card height
            launch { animH.animateTo(startSize.height.toFloat(), animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
            // animate x & w back to original (if necessary)
            launch { animX.animateTo(startOffset.x.toFloat(), animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
            launch { animW.animateTo(startSize.width.toFloat(), animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }
            launch { animY.animateTo(startOffset.y.toFloat(), animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)) }

            delay(duration.toLong() + 40)
            onDismiss()
        }
    }

    // IMPORTANT: use a full-screen Box (NOT Popup) so DropdownMenu popups appear above this overlay card
    Box(
        modifier = Modifier
            .fillMaxSize()
            // ensure this overlay is visually on top; call-site should place this composable after main UI
            .zIndex(100f)
    ) {
        // SCRIM (semi-transparent) that will close overlay on tap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimMaxAlpha * bgAlpha.value))
                .pointerInput(Unit) {
                    detectTapGestures { closeReverse() }
                }
        )

        // position the island at animX, animY and size animW x animH
        Box(
            modifier = Modifier
                .offset { IntOffset(animX.value.toInt(), animY.value.toInt()) }
                .size(with(density) { animW.value.toDp() }, with(density) { animH.value.toDp() })
                .zIndex(101f) // ensure island card itself is above scrim

        ) {
            // animate content scale+alpha
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = contentScale.value
                        scaleY = contentScale.value
                        alpha = contentAlpha.value
                    }
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = getDeviceIcon(device.type)),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color(0xFFCCCCCC)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(device.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        device.type.name.replace("DEVICE_TYPE_", "").lowercase()
                                            .replaceFirstChar { it.uppercaseChar() },
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            IconButton(onClick = { closeReverse() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Device controls content (uses the provided device/client and deviceIndex)
                        DeviceControlsContent(
                            device = device,
                            client = client,
                            deviceIndex = deviceIndex,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
