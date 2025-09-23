package me.kavishdevar.openrgb

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

import kotlinx.coroutines.*
import me.kavishdevar.openrgb.components.CompactHsvColorPicker
import me.kavishdevar.openrgb.models.*
import me.kavishdevar.openrgb.network.OpenRGBClient
import me.kavishdevar.openrgb.ui.theme.OpenRGBTheme
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.request.ImageRequest
import coil.size.Size
import coil.compose.AsyncImage

import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.font.FontWeight
import me.kavishdevar.openrgb.screens.LightshowScreen
import androidx.compose.foundation.LocalIndication


import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity


import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.NetworkInterface
import java.util.Collections

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import me.kavishdevar.openrgb.screens.BackgroundMode
import me.kavishdevar.openrgb.screens.SettingsScreen
import me.kavishdevar.openrgb.screens.VisualSettings
import me.kavishdevar.openrgb.screens.colorIntToCompose
import me.kavishdevar.openrgb.screens.composeColorToInt
import me.kavishdevar.openrgb.screens.loadVisualSettings


import java.net.Inet4Address

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import kotlinx.coroutines.withContext

import kotlinx.coroutines.sync.withPermit


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
            .permitAll()
            .build())

        val sharedPref = this.getSharedPreferences("servers", MODE_PRIVATE)
        val isFirstLaunch = sharedPref.getBoolean("first_launch", true)

        val launchCount = sharedPref.getInt("launch_count", 0) + 1
        sharedPref.edit().putInt("launch_count", launchCount).apply()
        val showRatingDialog = sharedPref.getBoolean("show_rating", true) && launchCount >= 5


        enableEdgeToEdge()
        setContent {
            OpenRGBTheme {
                MainApp(sharedPref,isFirstLaunch, showRatingDialog)
            }
        }
    }
}

enum class EffectsSubTab {
    DIRECT_CONTROL, EFFECTS
}


@Composable
fun MainApp(sharedPref: SharedPreferences, isFirstLaunch: Boolean, showRatingDialog: Boolean) {

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Visual settings (load once on composition)
    val context = LocalContext.current
    val visualPrefs = remember { loadVisualSettings(context) }

    val backgroundModeState = remember { mutableStateOf(visualPrefs.backgroundMode) }
    val solidBgColorState = remember { mutableStateOf(colorIntToCompose(visualPrefs.solidColorInt)) }
    val gradientAState = remember { mutableStateOf(colorIntToCompose(visualPrefs.gradientColorAInt)) }
    val gradientBState = remember { mutableStateOf(colorIntToCompose(visualPrefs.gradientColorBInt)) }
    val showSmallPillButtonsState = remember { mutableStateOf(visualPrefs.showSmallPillButtons) }
    val bottomNavOutlineColorIntState = remember { mutableStateOf(visualPrefs.bottomNavOutlineColorInt) }
    val connBarConnectedColorIntState = remember { mutableStateOf(visualPrefs.connectionBarColorConnectedInt) }
    val connBarDisconnectedColorIntState = remember { mutableStateOf(visualPrefs.connectionBarColorDisconnectedInt) }

// state to show settings modal
    val showSettingsModal = remember { mutableStateOf(false) }



    val availableServers = remember { mutableStateOf(emptyList<String>()) }
    val devices = remember { mutableStateOf(emptyList<OpenRGBDevice>()) }
    val client = remember { mutableStateOf<OpenRGBClient?>(null) }
    val showDeviceDialog = remember { mutableStateOf(false) }
    val selectedDevice = remember { mutableStateOf<OpenRGBDevice?>(null) }
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val selectedSubTab = remember { mutableStateOf(EffectsSubTab.DIRECT_CONTROL) }
    val tabs = listOf("Effects", "Lightshow", "Music", "Speed")

    val shouldReconnect = remember { mutableStateOf(true) }
    val connected = remember { mutableStateOf(false) }
    val currentServer = remember { mutableStateOf("") }
    val showWarning = remember { mutableStateOf(false) }
    val showReconnectError = remember { mutableStateOf(false) }
    val showWifiWarning = remember { mutableStateOf(false) }
    val showServerShutdownWarning = remember { mutableStateOf(false) }
    val isWifiConnected = remember { mutableStateOf(true) }
    val isServerReachable = remember { mutableStateOf(true) }

    // overlay island states
    val overlayDevice = remember { mutableStateOf<OpenRGBDevice?>(null) }
    val overlayClient = remember { mutableStateOf<OpenRGBClient?>(null) } // set when opening
    val overlayStartOffset = remember { mutableStateOf<IntOffset?>(null) }
    val overlayStartSize = remember { mutableStateOf<IntSize?>(null) }
    val overlayVisible = remember { mutableStateOf(false) }
    // add this near the other overlay states in MainApp
    val overlayRootOffset = remember { mutableStateOf(IntOffset(0, 0)) }





    val showServerSwitcher = remember { mutableStateOf(false) }
    val dragOffsetY = remember { mutableFloatStateOf(0f) }
    val dragThreshold = 80f // how far the user must drag down to trigger

    val isRefreshing = remember { mutableStateOf(false) }
    val scanJob = remember { mutableStateOf<Job?>(null) }

    val showManualServerSheet = remember { mutableStateOf(false) }







    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val pos = coords.localToWindow(Offset.Zero)
                    overlayRootOffset.value = IntOffset(pos.x.toInt(), pos.y.toInt())
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()




            // ✅ Fullscreen radial gradient background (blue centered at bottom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (backgroundModeState.value) {
                            BackgroundMode.Solid -> Brush.linearGradient(
                                colors = listOf(solidBgColorState.value, solidBgColorState.value)
                            )
                            BackgroundMode.Gradient -> Brush.radialGradient(
                                colors = listOf(gradientAState.value, gradientBState.value),
                                center = Offset(widthPx / 2f, heightPx),
                                radius = heightPx * 1.2f
                            )
                        }
                    )
            )

            // ✅ Transparent scaffold so background gradient shows through
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        ConnectionBar(
                            client = client,
                            connected = connected,
                            currentServer = currentServer.value,
                            devices = devices,
                            protocolVersion = client.value?.protocolVersion ?: 0,
                            onRefresh = { scope.launch { availableServers.value = scanNetworkForOpenRGB() } },
                            onServerSelected = { server ->
                                currentServer.value = server
                                connectToServer(server, client, connected, devices, snackbarHostState, scope, sharedPref, shouldReconnect)
                            },
                            availableServers = availableServers.value,
                            onDragPull = { showServerSwitcher.value = true },
                            dragOffsetY = dragOffsetY,
                            shouldReconnect = shouldReconnect,
                            dragThreshold = 80f,
                            sharedPref = sharedPref,
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                            currentServerState = currentServer,
                            connectionBarColorConnected = Color(connBarConnectedColorIntState.value),
                            connectionBarColorDisconnected = Color(connBarDisconnectedColorIntState.value),
                            onOpenSettings = { showSettingsModal.value = true } // opens modal
                        )

                    }
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp) // Increased height to accommodate the dog
                    ) {
                        // Dog image positioned above the navigation (100x100)
                        Image(
                            painter = painterResource(id = R.drawable.dog_over_fence),
                            contentDescription = "Dog",
                            modifier = Modifier
                                .size(100.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-20).dp, y = (-72).dp) // Adjust position
                                .zIndex(1f), // Make sure dog appears above the navigation
                            contentScale = ContentScale.Fit
                        )

                        // Existing navigation bar
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                .background(Color.Black)
                                .drawBehind {
                                    val strokeWidth = 3.dp.toPx()
                                    val cornerRadius = 28.dp.toPx()

                                    val path = Path().apply {
                                        moveTo(0f, size.height)
                                        lineTo(0f, cornerRadius)
                                        arcTo(
                                            rect = Rect(0f, 0f, cornerRadius * 2, cornerRadius * 2),
                                            startAngleDegrees = 180f,
                                            sweepAngleDegrees = 90f,
                                            forceMoveTo = false
                                        )
                                        lineTo(size.width - cornerRadius, 0f)
                                        arcTo(
                                            rect = Rect(size.width - cornerRadius * 2, 0f, size.width, cornerRadius * 2),
                                            startAngleDegrees = 270f,
                                            sweepAngleDegrees = 90f,
                                            forceMoveTo = false
                                        )
                                        lineTo(size.width, size.height)
                                    }

                                    drawPath(
                                        path = path,
                                        color = Color(bottomNavOutlineColorIntState.value),
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                                .align(Alignment.BottomCenter)
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                    .background(Color.Black)
                                    .drawBehind {
                                        val strokeWidth = 3.dp.toPx()
                                        val cornerRadius = 28.dp.toPx()
                                        val path = Path().apply {
                                            moveTo(0f, size.height)
                                            lineTo(0f, cornerRadius)
                                            arcTo(
                                                rect = Rect(0f, 0f, cornerRadius * 2, cornerRadius * 2),
                                                startAngleDegrees = 180f,
                                                sweepAngleDegrees = 90f,
                                                forceMoveTo = false
                                            )
                                            lineTo(size.width - cornerRadius, 0f)
                                            arcTo(
                                                rect = Rect(size.width - cornerRadius * 2, 0f, size.width, cornerRadius * 2),
                                                startAngleDegrees = 270f,
                                                sweepAngleDegrees = 90f,
                                                forceMoveTo = false
                                            )
                                            lineTo(size.width, size.height)
                                        }

                                        drawPath(
                                            path = path,
                                            color = Color(bottomNavOutlineColorIntState.value),
                                            style = Stroke(width = strokeWidth)
                                        )
                                    }
                            ) {
                                val tabCount = tabs.size
                                val tabWidth = constraints.maxWidth.toFloat() / tabCount
                                val indicatorOffset = remember { Animatable(0f) }

                                LaunchedEffect(selectedTabIndex.intValue) {
                                    indicatorOffset.animateTo(
                                        targetValue = selectedTabIndex.intValue * tabWidth,
                                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                                    )
                                }

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                ) {
                                    val indicatorWidth = 32.dp.toPx()
                                    val indicatorHeight = 5.dp.toPx()
                                    val yOffset = 20.dp.toPx()

                                    drawRoundRect(
                                        color = Color(0xFF00BFFF),
                                        topLeft = Offset(
                                            x = indicatorOffset.value + (tabWidth - indicatorWidth) / 2f,
                                            y = yOffset
                                        ),
                                        size = androidx.compose.ui.geometry.Size(
                                            indicatorWidth,
                                            indicatorHeight
                                        ),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 18.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = LocalIndication.current
                                                ) {
                                                    selectedTabIndex.intValue = index
                                                }
                                        ) {
                                            Image(
                                                painter = painterResource(
                                                    id = when (title) {
                                                        "Effects" -> R.drawable.effect
                                                        "Lightshow" -> R.drawable.lightshow
                                                        "Music" -> R.drawable.music_sync
                                                        "Speed" -> R.drawable.speed
                                                        else -> R.drawable.effect
                                                    }
                                                ),
                                                contentDescription = title,
                                                modifier = Modifier.size(32.dp),
                                                colorFilter = ColorFilter.tint(
                                                    if (selectedTabIndex.intValue == index) Color.White else Color.LightGray
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    when (selectedTabIndex.intValue) {
                        0 -> ProfileScreen(
                            devices = devices.value,
                            onClick = { device ->
                                selectedDevice.value = device
                                showDeviceDialog.value = true
                            },
                            selectedSubTab = selectedSubTab.value,
                            onSubTabSelected = { selectedSubTab.value = it },
                            client = client.value,
                            connected = connected.value,
                            onDevicesRefreshRequested = {
                                // keep your existing refresh logic here (same as before)
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val c = client.value ?: return@launch
                                        val count = try {
                                            c.getControllerCount()
                                        } catch (e: Exception) {
                                            Log.e("OpenRGB", "Failed to get controller count", e)
                                            return@launch
                                        }

                                        val refreshed = mutableListOf<OpenRGBDevice>()
                                        for (i in 0 until count) {
                                            try {
                                                val dev = c.getDeviceController(i)
                                                refreshed.add(dev)
                                            } catch (e: Exception) {
                                                Log.w("OpenRGB", "Failed to fetch device $i", e)
                                            }
                                        }

                                        withContext(Dispatchers.Main) {
                                            devices.value = refreshed
                                        }
                                    } catch (e: Exception) {
                                        Log.e("OpenRGB", "Error while refreshing devices after profile load", e)
                                    }
                                }
                            },

                            // <-- NEW: pass the overlay state objects from MainApp
                            overlayDevice = overlayDevice,
                            overlayClient = overlayClient,
                            overlayStartOffset = overlayStartOffset,
                            overlayStartSize = overlayStartSize,
                            overlayVisible = overlayVisible,
                            overlayRootOffset = overlayRootOffset // <-- pass it here
                        )


                        1 -> LightshowCaller(client = client.value,
                            devices = devices.value,
                            connected = connected.value)
                        2 -> MusicScreen()
                        3 -> SpeedScreen()
                    }
                }
            }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(3000)
                    val wifiStatus = isWifiConnected(context)

                    // Update WiFi state
                    if (isWifiConnected.value != wifiStatus) {
                        isWifiConnected.value = wifiStatus

                        if (!wifiStatus) {
                            // WiFi disconnected
                            showWifiWarning.value = true
                            connected.value = false
                            showServerShutdownWarning.value = false
                        } else {
                            // WiFi reconnected
                            showWifiWarning.value = false
                            if (shouldReconnect.value && !connected.value) {
                                val lastServer = sharedPref.getString("last_connected_server", "")
                                if (!lastServer.isNullOrEmpty()) {
                                    currentServer.value = lastServer
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
                        }
                    }



                    // Only check server if WiFi is connected
                    if (wifiStatus && connected.value) {
                        try {
                            val ip = currentServer.value.split(":")[0]
                            val port = currentServer.value.split(":").getOrNull(1)?.toIntOrNull() ?: 6742

                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, port), 1000)
                            }
                            isServerReachable.value = true
                        } catch (e: Exception) {
                            Log.w("OpenRGB", "Server unreachable")
                            if (isWifiConnected.value) { // Only show server error if WiFi is connected
                                connected.value = false
                                showServerShutdownWarning.value = true
                            }
                            isServerReachable.value = false
                        }
                    }
                }
            }


            LaunchedEffect(Unit) {
                if (!isFirstLaunch) {
                    val lastServer = sharedPref.getString("last_connected_server", "")
                    if (!lastServer.isNullOrEmpty()) {
                        try {
                            withTimeout(5000) {
                                val (newClient, devicesList) = connectToServerUnsafe(lastServer, sharedPref)
                                client.value = newClient
                                connected.value = true
                                devices.value = devicesList
                                currentServer.value = lastServer
                            }
                        } catch (e: Exception) {
                            Log.e("OpenRGB", "Reconnect failed: ${e.message}", e)
                            showReconnectError.value = true
                        }
                    }
                }

            }
            LaunchedEffect(currentServer.value) {
                if (!isFirstLaunch && currentServer.value.isNotBlank()) {
                    while (true) {
                        delay(3000)

                        if (connected.value) {
                            try {
                                val ip = currentServer.value.split(":")[0]
                                val port = currentServer.value.split(":").getOrNull(1)?.toIntOrNull() ?: 6742

                                Socket().use { socket ->
                                    socket.connect(InetSocketAddress(ip, port), 1000)
                                }
                            } catch (e: Exception) {
                                Log.d("OpenRGB", "Lost connection to server")
                                connected.value = false
                                showWarning.value = true
                            }
                        }
                    }
                }
            }
            LaunchedEffect(connected.value) {
                while (connected.value) {
                    delay(3000)

                    try {
                        val ip = currentServer.value.split(":")[0]
                        val port = currentServer.value.split(":").getOrNull(1)?.toIntOrNull() ?: 6742

                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 1000)
                        }
                    } catch (e: Exception) {
                        Log.w("OpenRGB", "Server unreachable")
                        connected.value = false
                        showServerShutdownWarning.value = true
                        break
                    }
                }
            }



            if (showSmallPillButtonsState.value) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 3.dp, bottom = 250.dp), // Position just above bottom nav
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {

                val premiumComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.premium_gold))
                val premiumProgress by animateLottieCompositionAsState(
                    composition = premiumComposition,
                    iterations = LottieConstants.IterateForever
                )

                LottieAnimation(
                    composition = premiumComposition,
                    progress = { premiumProgress },
                    modifier = Modifier
                        .size(42.dp)                // Match other buttons
                        .scale(1.35f)               // Zoom in (trim transparent padding)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current
                        ) {
                            /* handle click */
                        }
                )





                SmallPillIconButton(
                    iconRes = R.drawable.heart,
                    onClick = { /* TODO: Handle Donate action */ }
                )
                SmallPillIconButton(
                    iconRes = R.drawable.discord,
                    onClick = { /* TODO: Handle Join Us / Discord */ }
                )


            }}






        }


        if (showReconnectError.value) {
            ReconnectErrorCard(
                onRescan = {
                    showReconnectError.value = false
                    scope.launch {
                        availableServers.value = scanNetworkForOpenRGB()
                        showServerSwitcher.value = true
                    }
                }
                ,
                onDismiss = {
                    showReconnectError.value = false
                }
            )
        }


    }

    if (overlayVisible.value && overlayDevice.value != null && overlayStartOffset.value != null && overlayStartSize.value != null) {
        DeviceControlOverlay(
            device = overlayDevice.value!!,
            client = overlayClient.value,
            deviceIndex = devices.value.indexOf(overlayDevice.value!!), // now passed here
            startOffset = overlayStartOffset.value!!,
            startSize = overlayStartSize.value!!,
            onDismiss = {
                overlayVisible.value = false
                overlayDevice.value = null
                overlayStartOffset.value = null
                overlayStartSize.value = null
            }
        )
    }







    if (showWifiWarning.value) {
        WifiWarningCard(
            onTurnOnWifi = {
                showWifiWarning.value = false
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                context.startActivity(intent)
            },
            onDismiss = {
                showWifiWarning.value = false
            }
        )
    }

    if (showServerShutdownWarning.value) {
        ServerShutdownWarningCard(
            onRetry = {
                showServerShutdownWarning.value = false
                scope.launch {
                    val lastServer = sharedPref.getString("last_connected_server", "")
                    if (!lastServer.isNullOrEmpty() && isWifiConnected.value) {
                        try {
                            val (newClient, devicesList) = connectToServerUnsafe(lastServer, sharedPref)
                            client.value = newClient
                            connected.value = true
                            devices.value = devicesList
                            currentServer.value = lastServer
                        } catch (e: Exception) {
                            showServerShutdownWarning.value = true
                        }
                    }
                }
            },

            )
    }






    if (showServerSwitcher.value) {
        ServerSwitcherBottomSheet(
            currentServer = currentServer.value,
            availableServers = availableServers.value,
            sharedPref = sharedPref,
            onDismiss = { showServerSwitcher.value = false },
            onSwitch = { newServer ->
                if (connected.value) {
                    disconnectFromServer(client, connected, devices, shouldReconnect, userInitiated = true)
                }
                currentServer.value = newServer
                connectToServer(
                    newServer,
                    client,
                    connected,
                    devices,
                    snackbarHostState,
                    scope,
                    sharedPref,
                    shouldReconnect
                )
            },
            onRefresh = {
                isRefreshing.value = true
                availableServers.value = emptyList() // Clear the list

                // Cancel any ongoing scan
                scanJob.value?.cancel()

                // Start new scan
                scanJob.value = scope.launch {
                    availableServers.value = scanNetworkForOpenRGB()
                    isRefreshing.value = false
                }
            },
            isRefreshing = isRefreshing.value,
            onAddManual = { showManualServerSheet.value = true }
        )
    }

    if (showManualServerSheet.value) {
        AddServerBottomCard(
            onDismiss = { showManualServerSheet.value = false },
            onAdd = { fullAddress ->
                availableServers.value += fullAddress
                showManualServerSheet.value = false
            }
        )
    }

    val showRating = remember { mutableStateOf(showRatingDialog) }

    if (showRating.value) {
        RatingPromptDialog(
            onDismiss = {
                showRating.value = false
                sharedPref.edit().putBoolean("show_rating", false).apply()
            },
            onRate = {
                showRating.value = false
                sharedPref.edit().putBoolean("show_rating", false).apply()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=${context.packageName}")
                    setPackage("com.android.vending")
                }
                context.startActivity(intent)
            }
        )
    }
    if (showSettingsModal.value) {
        // show SettingsScreen as a modal overlay
        SettingsScreen(
            onClose = {
                // reload persisted values (they'll be applied next app launch; but update UI state so current session can show changes immediately if you want)
                val reloaded = loadVisualSettings(context)
                backgroundModeState.value = reloaded.backgroundMode
                solidBgColorState.value = colorIntToCompose(reloaded.solidColorInt)
                gradientAState.value = colorIntToCompose(reloaded.gradientColorAInt)
                gradientBState.value = colorIntToCompose(reloaded.gradientColorBInt)
                showSmallPillButtonsState.value = reloaded.showSmallPillButtons
                bottomNavOutlineColorIntState.value = reloaded.bottomNavOutlineColorInt
                connBarConnectedColorIntState.value = reloaded.connectionBarColorConnectedInt
                connBarDisconnectedColorIntState.value = reloaded.connectionBarColorDisconnectedInt

                showSettingsModal.value = false
            },
            initial = VisualSettings(
                backgroundMode = backgroundModeState.value,
                solidColorInt = composeColorToInt(solidBgColorState.value),
                gradientColorAInt = composeColorToInt(gradientAState.value),
                gradientColorBInt = composeColorToInt(gradientBState.value),
                showSmallPillButtons = showSmallPillButtonsState.value,
                bottomNavOutlineColorInt = bottomNavOutlineColorIntState.value,
                connectionBarColorConnectedInt = connBarConnectedColorIntState.value,
                connectionBarColorDisconnectedInt = connBarDisconnectedColorIntState.value
            )
        )
    }









}

@Composable
fun SmallPillIconButton(
    iconRes: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier
            .size(35.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E3B50) // or use MaterialTheme.colorScheme.surfaceVariant
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(25.dp)
        )
    }
}




@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ProfileScreen(
    devices: List<OpenRGBDevice>,
    onClick: (OpenRGBDevice) -> Unit,
    selectedSubTab: EffectsSubTab,
    onSubTabSelected: (EffectsSubTab) -> Unit,
    client: OpenRGBClient?,
    connected: Boolean,
    onDevicesRefreshRequested: () -> Unit,
    overlayDevice: MutableState<OpenRGBDevice?>,
    overlayClient: MutableState<OpenRGBClient?>,
    overlayStartOffset: MutableState<IntOffset?>,
    overlayStartSize: MutableState<IntSize?>,
    overlayVisible: MutableState<Boolean>,
    overlayRootOffset: MutableState<IntOffset>
) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf<List<OpenRGBProfile>>(emptyList()) }
    var localProfiles by remember { mutableStateOf(LocalProfileStorage.loadProfiles(context)) }

    var isLoading by remember { mutableStateOf(false) }

    var creationStep by remember { mutableStateOf(0) }
    var selectedDeviceIndex by remember { mutableStateOf<Int?>(null) }
    var selectedMode by remember { mutableStateOf<OpenRGBMode?>(null) }
    var currentColor by remember { mutableStateOf(Color.White) }
    var pendingSpeed by remember { mutableStateOf(0f) }
    var pendingBrightness by remember { mutableStateOf(100f) }
    var newEffectName by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load server profiles when connected
    LaunchedEffect(connected) {
        if (connected && client != null) {
            isLoading = true
            try {
                profiles = withContext(Dispatchers.IO) {
                    client.getProfileList().map { name -> OpenRGBProfile(name) }
                }
            } catch (e: Exception) {
                Log.e("OpenRGB", "Failed to load profiles", e)
                Toast.makeText(context, "Failed to load profiles", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        EffectsSubTabBar(selectedTab = selectedSubTab, onTabSelected = onSubTabSelected)

        when (selectedSubTab) {
            EffectsSubTab.DIRECT_CONTROL -> DirectControlScreen(
                devices = devices,
                onClick = { device, startOffset, startSize ->
                    // open overlay
                    overlayDevice.value = device
                    overlayClient.value = client
                    overlayStartOffset.value = startOffset
                    overlayStartSize.value = startSize
                    overlayVisible.value = true
                }
            )

            EffectsSubTab.EFFECTS -> {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            GradientButton(
                                text = "New Effect",
                                iconRes = R.drawable.add,
                                onClick = { creationStep = 1 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
                        }
                        items(profiles) { profile ->
                            val local = localProfiles.find { it.name == profile.name }
                            ProfileCard(
                                profile = profile,
                                deviceName = local?.let { devices.getOrNull(it.deviceIndex)?.name },
                                modeName = local?.mode?.name,
                                onPlayClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            client?.loadProfile(profile.name)
                                            // Tell the host to refresh devices (parent should re-query client and update `devices`)
                                            withContext(Dispatchers.Main) {
                                                onDevicesRefreshRequested()
                                                profiles = profiles.map {
                                                    it.copy(isActive = it.name == profile.name)
                                                }
                                                Toast.makeText(context, "Profile loaded", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("OpenRGB", "Failed to load profile", e)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            client?.deleteProfile(profile.name)
                                            profiles = profiles.filter { it.name != profile.name }
                                            localProfiles = localProfiles.filter { it.name != profile.name }
                                            LocalProfileStorage.saveProfiles(context, localProfiles)
                                        } catch (e: Exception) {
                                            Log.e("OpenRGB", "Failed to delete profile", e)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }

    // Bottom sheet for creation
    if (creationStep in 1..3) {
        ModalBottomSheet(
            onDismissRequest = { creationStep = 0 },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E2E)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                StepProgressBar(
                    currentStep = creationStep,
                    totalSteps = 3,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AnimatedContent(
                    targetState = creationStep,
                    transitionSpec = {
                        val enter = fadeIn(tween(350, easing = LinearOutSlowInEasing)) +
                                slideInVertically(
                                    initialOffsetY = { it / 6 },
                                    animationSpec = tween(350, easing = LinearOutSlowInEasing)
                                )
                        val exit = fadeOut(tween(300, easing = LinearOutSlowInEasing)) +
                                slideOutVertically(
                                    targetOffsetY = { it / 10 },
                                    animationSpec = tween(300, easing = LinearOutSlowInEasing)
                                )
                        enter with exit using SizeTransform(clip = false)
                    },
                    label = "StepTransition"
                ) { step ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (step) {
                            1 -> {
                                Text("Select Device", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                devices.forEachIndexed { idx, dev ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = LocalIndication.current
                                            ) {
                                                selectedDeviceIndex = idx
                                                val mode = dev.modes.getOrNull(dev.activeMode) ?: dev.modes.first()
                                                selectedMode = mode
                                                pendingSpeed = mode.speed.toFloat()
                                                pendingBrightness = mode.brightness.toFloat()
                                                currentColor = mode.colors.firstOrNull()?.let {
                                                    Color(it.red / 255f, it.green / 255f, it.blue / 255f)
                                                } ?: Color.White
                                                creationStep = 2
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(id = getDeviceIcon(dev.type)),
                                                contentDescription = null,
                                                tint = Color(0xFFCCCCCC),
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(dev.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    dev.type.name.replace("DEVICE_TYPE_", "")
                                                        .lowercase().replaceFirstChar { it.uppercaseChar() },
                                                    color = Color.LightGray,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Text("Configure Mode", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                val dev = selectedDeviceIndex?.let { devices[it] }
                                FancyDropdownMenu(
                                    modes = dev?.modes ?: emptyList(),
                                    selectedMode = selectedMode,
                                    onModeSelected = {
                                        selectedMode = it
                                        pendingSpeed = it.speed.toFloat()
                                        pendingBrightness = it.brightness.toFloat()
                                        currentColor = it.colors.firstOrNull()?.let { c ->
                                            Color(c.red / 255f, c.green / 255f, c.blue / 255f)
                                        } ?: Color.White
                                    }
                                )

                                CompactHsvColorPicker(
                                    initialColor = currentColor,
                                    onColorChange = { currentColor = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                )

                                if (selectedMode?.hasSpeed() == true) {
                                    Text("Speed: ${pendingSpeed.toInt()}", color = Color.White)
                                    Slider(
                                        value = pendingSpeed,
                                        onValueChange = { pendingSpeed = it.coerceIn(
                                            selectedMode!!.speedMin.toFloat(),
                                            selectedMode!!.speedMax.toFloat()
                                        ) },
                                        valueRange = selectedMode!!.speedMin.toFloat()..selectedMode!!.speedMax.toFloat(),
                                        onValueChangeFinished = {
                                            pendingSpeed = pendingSpeed.toInt().toFloat()
                                        },
                                        steps = (selectedMode!!.speedMax - selectedMode!!.speedMin - 1).coerceAtLeast(0)
                                    )
                                }

                                if (selectedMode?.hasBrightness() == true) {
                                    Text("Brightness: ${pendingBrightness.toInt()}", color = Color.White)
                                    Slider(
                                        value = pendingBrightness,
                                        onValueChange = { pendingBrightness = it.coerceIn(
                                            selectedMode!!.brightnessMin.toFloat(),
                                            selectedMode!!.brightnessMax.toFloat()
                                        ) },
                                        valueRange = selectedMode!!.brightnessMin.toFloat()..selectedMode!!.brightnessMax.toFloat(),
                                        onValueChangeFinished = {
                                            pendingBrightness = pendingBrightness.toInt().toFloat()
                                        },
                                        steps = (selectedMode!!.brightnessMax - selectedMode!!.brightnessMin - 1).coerceAtLeast(0)
                                    )
                                }

                                Button(
                                    onClick = { creationStep = 3 },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "Next",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                            3 -> {
                                Text("Name and Save", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = newEffectName,
                                    onValueChange = { newEffectName = it },
                                    label = { Text("Profile Name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (newEffectName.isNotBlank() && selectedDeviceIndex != null && selectedMode != null) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                // inside the coroutine already launched with Dispatchers.IO:
                                                try {
                                                    val devIndex = selectedDeviceIndex!!
                                                    val device = devices[devIndex]
                                                    val modeIndex = device.modes.indexOfFirst {
                                                        it.name == selectedMode!!.name && it.value == selectedMode!!.value
                                                    }.takeIf { it != -1 } ?: device.activeMode

                                                    // Create updated mode
                                                    val updatedMode = selectedMode!!.copy(
                                                        speed = pendingSpeed.toInt(),
                                                        brightness = pendingBrightness.toInt(),
                                                        colors = listOf(OpenRGBColor(
                                                            (currentColor.red * 255).toInt(),
                                                            (currentColor.green * 255).toInt(),
                                                            (currentColor.blue * 255).toInt()
                                                        ))
                                                    )

                                                    // Update and save the mode on the device (sends UPDATE_MODE and SAVEMODE payloads)
                                                    client?.saveMode(devIndex, modeIndex, updatedMode)

                                                    // Save the profile on the server (REQUEST_SAVE_PROFILE with profile name)
                                                    // Protocol requires a null-terminated ASCII name; saveProfile already does that.
                                                    client?.saveProfile(newEffectName)

                                                    // Refresh server profile list so UI shows the new profile
                                                    val serverProfiles = client?.getProfileList()?.map { name -> OpenRGBProfile(name) } ?: emptyList()

                                                    // Save locally unchanged logic:
                                                    val newLocalProfile = LocalProfile(
                                                        name = newEffectName,
                                                        deviceIndex = devIndex,
                                                        mode = updatedMode
                                                    )
                                                    localProfiles = localProfiles + newLocalProfile
                                                    LocalProfileStorage.saveProfiles(context, localProfiles)

                                                    // Update UI on main thread
                                                    withContext(Dispatchers.Main) {
                                                        profiles = profiles + OpenRGBProfile(newEffectName)
                                                        // Or replace with serverProfiles if you prefer authoritative list:
                                                        // profiles = serverProfiles
                                                        creationStep = 0
                                                        newEffectName = ""
                                                        Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "Failed to save profile: ${e.message}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }

                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save & Apply")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlsContent(
    device: OpenRGBDevice,
    client: OpenRGBClient?,
    deviceIndex: Int,                     // pass devices.indexOf(device)
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Track current mode and its properties
    var currentMode by remember {
        mutableStateOf(device.modes.getOrNull(device.activeMode) ?: device.modes.firstOrNull())
    }

    // Track color separately
    var currentColor by remember {
        mutableStateOf(
            device.colors.firstOrNull()?.let { color ->
                Color(color.red / 255f, color.green / 255f, color.blue / 255f)
            } ?: Color.Red
        )
    }

    // Track mode properties
    var pendingSpeed by remember {
        mutableStateOf(currentMode?.speed?.toFloat() ?: 0f)
    }
    var pendingBrightness by remember {
        mutableStateOf(currentMode?.brightness?.toFloat() ?: 100f)
    }

    // Track if we're updating (kept for internal logic, but no "Applying..." UI)
    var isUpdating by remember { mutableStateOf(false) }

    // updateMode - sends mode update to server via client
    fun updateMode(newMode: OpenRGBMode? = null) {
        val modeToUpdate = newMode ?: currentMode ?: return
        val c = client
        if (c == null) {
            Toast.makeText(context, "Not connected to server", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modeIndex = device.modes.indexOfFirst {
                    it.name == modeToUpdate.name && it.value == modeToUpdate.value
                }.takeIf { it != -1 } ?: device.activeMode

                val updatedMode = modeToUpdate.copy(
                    speed = pendingSpeed.toInt(),
                    brightness = pendingBrightness.toInt(),
                    colors = if (modeToUpdate.hasModeSpecificColor()) {
                        listOf(
                            OpenRGBColor(
                                (currentColor.red * 255).toInt(),
                                (currentColor.green * 255).toInt(),
                                (currentColor.blue * 255).toInt()
                            )
                        )
                    } else modeToUpdate.colors
                )

                c.updateMode(deviceIndex, modeIndex, updatedMode)

                withContext(Dispatchers.Main) {
                    currentMode = updatedMode
                    // no "Applying" popup — keep only a toast on success
                    Toast.makeText(context, "Mode updated", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to update mode: ${e.message?.take(80)}", Toast.LENGTH_LONG).show()
                    Log.e("OpenRGB", "Mode update error", e)
                }
            } finally {
                isUpdating = false
            }
        }
    }

    // updateColor - sends color updates appropriately
    fun updateColor(newColor: Color) {
        val c = client
        if (c == null) {
            Toast.makeText(context, "Not connected to server", Toast.LENGTH_SHORT).show()
            return
        }

        val rgbColor = OpenRGBColor(
            (newColor.red * 255).toInt(),
            (newColor.green * 255).toInt(),
            (newColor.blue * 255).toInt()
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                currentMode?.let { mode ->
                    if (mode.hasPerLedColor()) {
                        device.leds.forEachIndexed { index, _ ->
                            c.updateLed(deviceIndex, index, rgbColor)
                        }
                    } else if (mode.hasModeSpecificColor()) {
                        updateMode(mode.copy(colors = listOf(rgbColor)))
                    }
                }

                withContext(Dispatchers.Main) {
                    currentColor = newColor
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Color update failed", Toast.LENGTH_SHORT).show()
                    Log.e("OpenRGB", "Color update error", e)
                }
            }
        }
    }

    // UI
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // NOTE: removed the previous "Applying..." UI per request.
        // isUpdating is still tracked internally but not shown.

        // Mode selector: use the same FancyDropdownMenu style as Configure Mode in profile creation
        FancyDropdownMenu(
            modes = device.modes,
            selectedMode = currentMode,
            onModeSelected = { mode ->
                currentMode = mode
                pendingSpeed = mode.speed.toFloat()
                pendingBrightness = mode.brightness.toFloat()
                // apply immediately (if desired)
                updateMode(mode)
            }
        )

        Spacer(Modifier.height(12.dp))

        // Compact HSV Picker
        CompactHsvColorPicker(
            initialColor = currentColor,
            onColorChange = { updateColor(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Speed slider if applicable
        currentMode?.takeIf { it.hasSpeed() }?.let { mode ->
            Column {
                Text("Speed: ${pendingSpeed.toInt()}", color = Color.White)
                Slider(
                    value = pendingSpeed,
                    onValueChange = {
                        pendingSpeed = it.coerceIn(mode.speedMin.toFloat(), mode.speedMax.toFloat())
                        // update continuously (you can change to onValueChangeFinished if preferred)
                        updateMode(mode)
                    },
                    valueRange = mode.speedMin.toFloat()..mode.speedMax.toFloat(),
                    steps = (mode.speedMax - mode.speedMin - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        // Brightness slider if applicable
        currentMode?.takeIf { it.hasBrightness() }?.let { mode ->
            Column {
                Text("Brightness: ${pendingBrightness.toInt()}%", color = Color.White)
                Slider(
                    value = pendingBrightness,
                    onValueChange = {
                        pendingBrightness = it.coerceIn(mode.brightnessMin.toFloat(), mode.brightnessMax.toFloat())
                        updateMode(mode)
                    },
                    valueRange = mode.brightnessMin.toFloat()..mode.brightnessMax.toFloat(),
                    steps = (mode.brightnessMax - mode.brightnessMin - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Yellow,
                        activeTrackColor = Color.Yellow
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
        }


    }
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancyDropdownMenu(
    modes: List<OpenRGBMode>,
    selectedMode: OpenRGBMode?,
    onModeSelected: (OpenRGBMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color.Cyan, Color.Magenta)),
                RoundedCornerShape(12.dp)
            )
            .background(Color(0xFF121212))
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedMode?.name ?: "Select Mode",
            onValueChange = {},

            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A1A1A))
        ) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Cyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.name, color = Color.White)
                        }
                    },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun StepProgressBar(
    currentStep: Int,
    totalSteps: Int = 3,
    modifier: Modifier = Modifier
) {
    val segmentWidth = 1f / totalSteps
    val animatedProgress by animateFloatAsState(
        targetValue = currentStep.toFloat() / totalSteps,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val fullWidth = size.width
        val segmentSize = fullWidth / totalSteps

        // Draw background segments
        for (i in 0 until totalSteps) {
            drawRect(
                color = Color(0xFF0D1B2A), // dark background
                topLeft = Offset(x = i * segmentSize, y = 0f),
                size = androidx.compose.ui.geometry.Size(width = segmentSize, height = size.height)
            )
            if (i < totalSteps - 1) {
                // separator
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x = (i + 1) * segmentSize - 1.dp.toPx(), y = 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width = 2.dp.toPx(),
                        height = size.height
                    )
                )
            }
        }

        // Draw progress
        val progressWidth = fullWidth * animatedProgress
        drawRect(
            color = Color(0xFF00BFFF), // neon blue
            size = androidx.compose.ui.geometry.Size(width = progressWidth, height = size.height)
        )
    }
}




@Composable
fun GradientButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 🔄 GIF image loader with GIF decoder
    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(GifDecoder.Factory())
        }
        .build()

    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current
            ) {
                onClick()
            }
    ) {
        // 🎞️ Animated GIF background using Coil
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(R.raw.new_effect_gradient)
                .size(Size.ORIGINAL)
                .build(),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )

        // Foreground: icon + text
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

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



@Composable
fun EffectsSubTabBar(
    selectedTab: EffectsSubTab,
    onTabSelected: (EffectsSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = EffectsSubTab.values()
    val selectedIndex = tabs.indexOf(selectedTab)
    val animIndex = remember { Animatable(selectedIndex.toFloat()) }

    // Animate tracker movement
    LaunchedEffect(selectedIndex) {
        animIndex.animateTo(
            selectedIndex.toFloat(),
            animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // Reduced height
            .padding(horizontal = 24.dp) // Slightly less padding than before
    ) {
        val widthPerTab = constraints.maxWidth.toFloat() / tabs.size
        val heightPx = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineHeight = 2.dp.toPx()
            val tabTop = 4.dp.toPx()
            val tabBottom = size.height - lineHeight
            val tabWidth = widthPerTab
            val x = animIndex.value * tabWidth
            val cornerRadius = 8.dp.toPx()

            val path = Path().apply {
                moveTo(0f, size.height - lineHeight)
                lineTo(x, size.height - lineHeight)
                lineTo(x, tabTop + cornerRadius)
                arcTo(
                    rect = Rect(x, tabTop, x + cornerRadius * 2, tabTop + cornerRadius * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(x + tabWidth - cornerRadius, tabTop)
                arcTo(
                    rect = Rect(x + tabWidth - cornerRadius * 2, tabTop, x + tabWidth, tabTop + cornerRadius * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(x + tabWidth, size.height - lineHeight)
                lineTo(size.width, size.height - lineHeight)
            }

            drawPath(
                path = path,
                color = Color(0xFF4A90E2),
                style = Stroke(width = lineHeight)
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current
                        ) {
                            onTabSelected(tab)
                        }
                        .padding(vertical = 8.dp), // Reduced vertical padding
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (tab) {
                            EffectsSubTab.DIRECT_CONTROL -> "Direct"
                            EffectsSubTab.EFFECTS -> "Effects"
                        },
                        color = if (selectedIndex == index) Color.White else Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium, // Smaller text size
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun RatingPromptDialog(
    onDismiss: () -> Unit,
    onRate: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(300.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            ) {
                // ⭐ STAR inside card, aligned to right, clipped left
                Image(
                    painter = painterResource(id = R.drawable.big_star),
                    contentDescription = "Star",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd) // Right half visible
                        .offset(x = 20.dp) // shift slightly more right if needed
                        .rotate(15f),
                    alpha = 0.25f
                )

                // Foreground content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Enjoying the app?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onRate,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD700)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Rate on Play Store", color = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Maybe later", color = Color.Gray)
                    }
                }
            }
        }
    }
}






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

@Composable
fun DeviceControlOverlay(
    device: OpenRGBDevice,
    client: OpenRGBClient?,
    deviceIndex: Int,             // index of the device in the devices list
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}








@Composable
fun ModeSelector(
    device: OpenRGBDevice,
    onModeSelected: (OpenRGBMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(device.modes[device.activeMode]) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedMode.name)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            device.modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.name) },
                    onClick = {
                        selectedMode = mode
                        onModeSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LightshowCaller(
    client: OpenRGBClient?,
    devices: List<OpenRGBDevice>,
    connected: Boolean
) {


    LightshowScreen(
        client = client,
        devices = devices,
        connected = connected
    )
}


@Composable
fun MusicScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Music Sync Screen (Coming Soon)", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SpeedScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Speed Screen (Coming Soon)", style = MaterialTheme.typography.titleLarge)
    }
}



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
    connectionBarColorDisconnected: androidx.compose.ui.graphics.Color,
    onOpenSettings: () -> Unit
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
                Spacer(modifier = Modifier.width(180.dp))
                Image(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "Settings",
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current
                        ) {
                            onOpenSettings()
                        },
                    colorFilter = ColorFilter.tint(
                        if (connected.value) Color.Black else Color.White
                    )
                )


            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSwitcherBottomSheet(
    currentServer: String,
    availableServers: List<String>,
    sharedPref: SharedPreferences,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onRefresh: () -> Unit, // Add this parameter
    isRefreshing: Boolean,
    onAddManual: () -> Unit // ✅ Add this

) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black.copy(alpha = 0.9f),
        dragHandle = null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Refresh button in top-right corner
            if(!isRefreshing){ IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.refresh),
                    contentDescription = "Refresh servers",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }}


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Available Servers",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                if (isRefreshing) {
                    // Show Lottie animation while refreshing
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.RawRes(R.raw.kudygdpu2j) // Convert your GIF to Lottie JSON
                        )
                        val progress by animateLottieCompositionAsState(
                            composition = composition,
                            iterations = LottieConstants.IterateForever
                        )

                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.size(200.dp)
                        )
                    }
                } else {
                    // Current server card
                    ModernServerCard(
                        server = currentServer,
                        isCurrent = true,
                        onClick = {},
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Available servers
                    availableServers.filter { it != currentServer }.forEach { server ->
                        ModernServerCard(
                            server = server,
                            isCurrent = false,
                            onClick = {
                                onSwitch(server)
                                onDismiss()
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }

                    IconButton(
                        onClick = onAddManual,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.add_server),
                            contentDescription = "Add Server",
                            modifier = Modifier.size(48.dp)
                        )
                    }


                }
            }
        }
    }
}

@Composable
fun ModernServerCard(
    server: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                Color(0xFF4682C2) else
                Color(0xFF2A2D3E)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrent)
                Color(0xFF6AB7FF) else
                Color(0xFF44475A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = if (isCurrent) "Connected" else "Disconnected",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = server.split(":").first(),
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                )
                Text(
                    text = if (isCurrent) "Connected" else "Tap to connect",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f))
                )
            }

            if (isCurrent) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Current",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerBottomCard(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var serverInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black.copy(alpha = 0.9f),
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Add New Server",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = serverInput,
                onValueChange = { serverInput = it },
                label = { Text("IP Address", color = Color.White) },
                placeholder = { Text("e.g. 192.168.1.105", color = Color.LightGray) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                )
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val trimmed = serverInput.trim()
                    val ipRegex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

                    if (trimmed.isNotBlank() && ipRegex.matches(trimmed)) {
                        val fullAddress = "$trimmed:6742"
                        onAdd(fullAddress)
                    } else {
                        Toast.makeText(
                            context,
                            "Please enter a valid IP address (e.g. 192.168.1.105)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6AB7F1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add", color = Color.White)
            }
        }
    }
}




@Composable
fun ServerShutdownWarningCard(
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000A2E).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101B49))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Server off",
                    tint = Color(0xFFFF0026),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Server Unreachable", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The OpenRGB server seems to be shut down or disconnected from the network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6AB7F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Okay", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ReconnectErrorCard(
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000A2E).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101B49))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Reconnect Error",
                    tint = Color(0xFFFFA726), // orange tint
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Reconnect Failed", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "We tried to reconnect to your last known PC, but it's offline or its IP has changed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onRescan,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6AB7F1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Rescan", color = Color.White)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            }
        }
    }
}
@Composable
fun WifiWarningCard(
    onTurnOnWifi: () -> Unit,
    onDismiss: () -> Unit
) {
    Log.d("OpenRGB", "📡 Showing WifiWarningCard")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000A2E).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101B49))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.no_wifi),
                    contentDescription = "WiFi off",
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Wi-Fi Disconnected", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You're not connected to Wi-Fi. OpenRGB requires a local network connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onTurnOnWifi) {
                        Text("Turn on Wi-Fi")
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            }
        }
    }
}








fun getDeviceIcon(type: DeviceType): Int {
    return when {

        type.name.contains("MOTHERBOARD") -> R.drawable.motherboard
        type.name.contains("GAMEPAD") -> R.drawable.gamepad
        type.name.contains("KEYBOARD") -> R.drawable.keyboard
        type.name.contains("MOUSE") -> R.drawable.mouse
        else -> R.drawable.device
    }
}

// helper: returns local IPv4 address string like "192.168.2.45" or null if not found
fun getLocalIPv4Address(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in Collections.list(interfaces)) {
            for (addr in Collections.list(intf.inetAddresses)) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress // e.g. "192.168.2.45"
                }
            }
        }
    } catch (e: Exception) {
        Log.w("OpenRGB", "Failed to enumerate network interfaces: ${e.message}")
    }
    return null
}

/**
 * Scans the local /24 subnet derived from the phone's IPv4 address.
 * - Limits concurrent TCP tests to `maxConcurrency` (100)
 * - Tests port 6742 (OpenRGB SDK default)
 * - Uses `timeoutMs` for socket connect timeout (tweak as needed)
 */
suspend fun scanNetworkForOpenRGB(
    port: Int = 6742,
    maxConcurrency: Int = 100,
    timeoutMs: Int = 500  // increase if you see false negatives on slow networks
): List<String> {
    val foundServers = Collections.synchronizedList(mutableListOf<String>())

    // Determine local IP and subnet prefix (xxx.xxx.xxx)
    val localIp = getLocalIPv4Address()
    val subnetPrefix = localIp?.substringBeforeLast(".") ?: run {
        Log.w("OpenRGB", "Could not determine local IP, falling back to 192.168.1")
        "192.168.1"
    }

    // scan hosts 1..254 (skip .0 and .255)
    val hosts = (1..254).toList()

    // semaphore to limit concurrency
    val semaphore = Semaphore(maxConcurrency)

    // coroutineScope ensures cancellation propagates when scanJob is cancelled elsewhere
    coroutineScope {
        val jobs = hosts.map { h ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val ip = "$subnetPrefix.$h"
                    try {
                        Socket().use { sock ->
                            sock.soTimeout = timeoutMs
                            sock.connect(InetSocketAddress(ip, port), timeoutMs)
                            if (sock.isConnected) {
                                Log.d("OpenRGB", "Found server at $ip:$port")
                                foundServers.add("$ip:$port")
                                sock.close()
                            }
                        }
                    } catch (e: Exception) {
                        // silent fail - logging for debug
                        Log.d("OpenRGB", "Connection test failed for $ip:$port: ${e.message}")
                    }
                }
            }
        }
        // wait for all attempts to finish (or job cancellation)
        jobs.awaitAll()
    }

    // optionally sort/unique
    return foundServers.toSet().sorted()
}

fun connectToServer(
    server: String,
    client: MutableState<OpenRGBClient?>,
    connected: MutableState<Boolean>,
    devices: MutableState<List<OpenRGBDevice>>,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    sharedPref: SharedPreferences,
    shouldReconnect: MutableState<Boolean>
) {
    scope.launch {
        // Disconnect first if already connected
        if (connected.value && client.value != null) {
            client.value?.disconnect()
            connected.value = false
            devices.value = emptyList()
        }

        try {
            val parts = server.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid server format - expected 'ip:port'")
            }

            val ip = parts[0]
            val port = parts[1].toIntOrNull() ?: 6742

            Log.d("OpenRGB", "Attempting to connect to $ip:$port")

            val newClient = OpenRGBClient(ip, port, "OpenRGB Android")

            withContext(Dispatchers.IO) {
                try {
                    newClient.connect()
                    val count = newClient.getControllerCount()
                    Log.d("OpenRGB", "Found $count controllers")

                    val deviceList = mutableListOf<OpenRGBDevice>()
                    for (i in 0 until count) {
                        try {
                            val device = newClient.getDeviceController(i)
                            deviceList.add(device)
                            Log.d("OpenRGB", "Loaded device: ${device.name}")
                        } catch (e: Exception) {
                            Log.e("OpenRGB", "Error loading device $i", e)
                            withContext(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("Error loading device $i: ${e.message?.take(50)}...")
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        client.value = newClient
                        connected.value = true
                        devices.value = deviceList
                        shouldReconnect.value = true // Enable reconnection for this server
                        snackbarHostState.showSnackbar(
                            "Connected to $server (v${newClient.protocolVersion})"
                        )

                        sharedPref.edit()
                            .putBoolean("first_launch", false)
                            .putString("last_connected_server", server)
                            .apply()
                    }
                } catch (e: Exception) {
                    Log.e("OpenRGB", "Connection error", e)
                    withContext(Dispatchers.Main) {
                        connected.value = false
                        devices.value = emptyList()
                        shouldReconnect.value = false // Disable reconnection if failed
                        val errorMsg = when {
                            e is IOException && e.message?.contains("ECONNREFUSED") == true ->
                                "Connection refused. Is OpenRGB server running?"
                            e is SocketTimeoutException ->
                                "Connection timeout. Check server and network."
                            e is IllegalArgumentException ->
                                "Invalid server address: ${e.message}"
                            else -> "Connection failed: ${e.message?.take(50)}..."
                        }
                        snackbarHostState.showSnackbar(errorMsg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OpenRGB", "Initial setup error", e)
            withContext(Dispatchers.Main) {
                connected.value = false
                devices.value = emptyList()
                shouldReconnect.value = false
                snackbarHostState.showSnackbar("Setup error: ${e.message?.take(50)}...")
            }
        }
    }
}

fun connectToServerUnsafe(
    server: String,
    sharedPref: SharedPreferences
): Pair<OpenRGBClient, List<OpenRGBDevice>> {
    val parts = server.split(":")
    if (parts.size != 2) throw IllegalArgumentException("Invalid server format")

    val ip = parts[0]
    val port = parts[1].toIntOrNull() ?: 6742

    val newClient = OpenRGBClient(ip, port, "OpenRGB Android")
    newClient.connect()

    val count = newClient.getControllerCount()
    val devices = mutableListOf<OpenRGBDevice>()

    for (i in 0 until count) {
        devices.add(newClient.getDeviceController(i))
    }

    sharedPref.edit()
        .putBoolean("first_launch", false)
        .putString("last_connected_server", server)
        .apply()

    return newClient to devices
}


fun disconnectFromServer(
    client: MutableState<OpenRGBClient?>,
    connected: MutableState<Boolean>,
    devices: MutableState<List<OpenRGBDevice>>,
    shouldReconnect: MutableState<Boolean>,
    userInitiated: Boolean = false
) {
    client.value?.disconnect()
    client.value = null
    connected.value = false
    devices.value = emptyList()

    if (userInitiated) {
        shouldReconnect.value = false
    }
}
fun isWifiConnected(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}





