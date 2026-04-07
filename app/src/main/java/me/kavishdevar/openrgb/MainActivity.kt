package me.kavishdevar.openrgb

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.*
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.network.OpenRGBClient
import java.net.InetSocketAddress
import java.net.Socket
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb

// UI Composables
import me.kavishdevar.openrgb.components.SmallPillIconButton
import me.kavishdevar.openrgb.screens.ProfileScreen
import me.kavishdevar.openrgb.components.DeviceControlOverlay
import me.kavishdevar.openrgb.components.RatingPromptDialog
import me.kavishdevar.openrgb.logic.LightshowCaller
import me.kavishdevar.openrgb.screens.MusicScreen
import me.kavishdevar.openrgb.screens.SpeedScreen
import me.kavishdevar.openrgb.components.ConnectionBar
import me.kavishdevar.openrgb.components.ServerSwitcher
import me.kavishdevar.openrgb.components.AddServerBottomCard
import me.kavishdevar.openrgb.components.ServerShutdownWarningCard
import me.kavishdevar.openrgb.components.ReconnectErrorCard
import me.kavishdevar.openrgb.components.WifiWarningCard
import me.kavishdevar.openrgb.network.scanNetworkForOpenRGB
import me.kavishdevar.openrgb.network.connectToServer
import me.kavishdevar.openrgb.network.connectToServerUnsafe
import me.kavishdevar.openrgb.network.disconnectFromServer
import me.kavishdevar.openrgb.network.isWifiConnected

// ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import me.kavishdevar.openrgb.ui_logic.MainViewModel
import me.kavishdevar.openrgb.ui_logic.MainViewModelFactory
import me.kavishdevar.openrgb.ui_logic.UiEvent
import kotlinx.coroutines.flow.collect
import androidx.core.content.edit
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build()
        )

        val sharedPref = this.getSharedPreferences("servers", MODE_PRIVATE)
        val isFirstLaunch = sharedPref.getBoolean("first_launch", true)

        val launchCount = sharedPref.getInt("launch_count", 0) + 1
        sharedPref.edit { putInt("launch_count", launchCount) }
        val showRatingDialog = sharedPref.getBoolean("show_rating", true) && launchCount >= 5

        enableEdgeToEdge()
        setContent {
            MainApp(sharedPref, isFirstLaunch, showRatingDialog)
        }
    }
}

enum class EffectsSubTab {
    DIRECT_CONTROL, EFFECTS
}
enum class BackgroundMode {
    Solid,
    Gradient
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun MainApp(sharedPref: SharedPreferences, isFirstLaunch: Boolean, showRatingDialog: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val vm: MainViewModel = viewModel(factory = MainViewModelFactory(app, sharedPref, isFirstLaunch))

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // UI-only visual settings
    val backgroundModeState = remember { mutableStateOf(BackgroundMode.Gradient) }
    val solidBgColorState = remember { mutableStateOf(Color(0xFF0E1621)) }
    val gradientAState = remember { mutableStateOf(Color(0xFF1B2A41)) }
    val gradientBState = remember { mutableStateOf(Color(0xFF0A0F1C)) }
    val showSmallPillButtonsState = remember { mutableStateOf(false) }

    // Connection bar colors etc. (UI-only)
    val bottomNavOutlineColorIntState = remember { mutableIntStateOf(Color.White.toArgb()) }
    val connBarConnectedColorIntState = remember { mutableIntStateOf(Color(0xFF2586F1).toArgb()) }
    val connBarDisconnectedColorIntState = remember { mutableIntStateOf(Color(0xFFFF5252).toArgb()) }

    // UI-only states retained from original MainApp
    remember { mutableStateOf<OpenRGBDevice?>(null) }
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val selectedSubTab = remember { mutableStateOf(EffectsSubTab.DIRECT_CONTROL) }
    val tabs = listOf("Effects", "Lightshow", "Music", "Speed")

    val showServerSwitcher = remember { mutableStateOf(false) }
    val dragOffsetY = remember { mutableFloatStateOf(0f) }

    val showManualServerSheet = remember { mutableStateOf(false) }

    // overlay root offset is layout-dependent — keep in UI
    val overlayRootOffset = remember { mutableStateOf(IntOffset(0, 0)) }

    // collect one-off events from VM
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                UiEvent.ShowReconnectError -> {
                    vm.showReconnectErrorState.value = true
                }
                UiEvent.ShowWifiWarning -> {
                    // UI will read vm.showWifiWarningState
                }
                UiEvent.ShowServerShutdownWarning -> {
                    // UI will read vm.showServerShutdownWarningState
                }
            }
        }
    }

    // Local aliases for convenience (VM exposes MutableState objects)
    val availableServers = vm.availableServersState
    val devices = vm.devicesState
    val client = vm.clientState
    val connected = vm.connectedState
    val currentServer = vm.currentServerState
    val isRefreshing = vm.isRefreshingState

    // overlay states from VM
    val overlayDevice = vm.overlayDeviceState
    val overlayClient = vm.overlayClientState
    val overlayStartOffset = vm.overlayStartOffsetState
    val overlayStartSize = vm.overlayStartSizeState
    val overlayVisible = vm.overlayVisibleState

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

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column(modifier = Modifier.padding(top = 25.dp)) {
                        ConnectionBar(
                            client = client,
                            connected = connected,
                            currentServer = currentServer.value,
                            devices = devices,
                            protocolVersion = client.value?.protocolVersion ?: 0,
                            onRefresh = { vm.scanForServers() },
                            onServerSelected = { server ->
                                currentServer.value = server

                                vm.connectTo(server)
                            },
                            availableServers = availableServers.value,
                            onDragPull = { showServerSwitcher.value = true },
                            dragOffsetY = dragOffsetY,
                            shouldReconnect = mutableStateOf(true), // preserve original behavior or wire to settings
                            dragThreshold = 80f,
                            sharedPref = sharedPref,
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                            currentServerState = currentServer,
                            connectionBarColorConnected = Color(connBarConnectedColorIntState.value),
                            connectionBarColorDisconnected = Color(connBarDisconnectedColorIntState.value),
                        )
                    }
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.dog_over_fence),
                            contentDescription = "Dog",
                            modifier = Modifier
                                .size(100.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-20).dp, y = (-72).dp)
                                .zIndex(1f),
                            contentScale = ContentScale.Fit
                        )

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
                                // route to overlay by updating VM overlay state
                                vm.overlayDeviceState.value = device
                                vm.overlayClientState.value = client.value
                                vm.overlayVisibleState.value = true
                            },
                            selectedSubTab = selectedSubTab.value,
                            onSubTabSelected = { selectedSubTab.value = it },
                            client = client.value,
                            connected = connected.value,
                            onDevicesRefreshRequested = { vm.refreshDevicesFromClient() },

                            // pass overlay state objects (these are MutableState objects from VM)
                            overlayDevice = overlayDevice,
                            overlayClient = overlayClient,
                            overlayStartOffset = overlayStartOffset,
                            overlayStartSize = overlayStartSize,
                            overlayVisible = overlayVisible,
                            overlayRootOffset = overlayRootOffset
                        )

                        1 -> LightshowCaller(client = client.value, devices = devices.value, connected = connected.value)
                        2 -> MusicScreen()
                        3 -> SpeedScreen()
                    }
                }
            }

            // small pill buttons UI (UI only)
            if (showSmallPillButtonsState.value) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp, bottom = 250.dp),
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
                            .size(42.dp)
                            .scale(1.35f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current
                            ) {

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
                }
            }
        }

        // Reconnect error card (UI reads VM state)
        if (vm.showReconnectErrorState.value) {
            ReconnectErrorCard(
                onRescan = {
                    vm.showReconnectErrorState.value = false
                    scope.launch { vm.scanForServers(); showServerSwitcher.value = true }
                },
                onDismiss = {
                    vm.showReconnectErrorState.value = false
                }
            )
        }
    }

    // Device control overlay (UI-driven using VM overlay state)
    if (overlayVisible.value && overlayDevice.value != null && overlayStartOffset.value != null && overlayStartSize.value != null) {
        DeviceControlOverlay(
            device = overlayDevice.value!!,
            client = overlayClient.value,
            deviceIndex = devices.value.indexOf(overlayDevice.value!!),
            startOffset = overlayStartOffset.value!!,
            startSize = overlayStartSize.value!!,
            viewModel = vm,
            onDismiss = {
                overlayVisible.value = false
                overlayDevice.value = null
                overlayStartOffset.value = null
                overlayStartSize.value = null
            }
        )
    }

    // Wifi warning UI (VM-driven)
    if (vm.showWifiWarningState.value) {
        WifiWarningCard(
            onTurnOnWifi = {
                vm.showWifiWarningState.value = false
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                context.startActivity(intent)
            },
            onDismiss = {
                vm.showWifiWarningState.value = false
            }
        )
    }

    // Server shutdown warning UI (VM-driven)
    if (vm.showServerShutdownWarningState.value) {
        ServerShutdownWarningCard(
            onRetry = {
                vm.showServerShutdownWarningState.value = false
                scope.launch {
                    val lastServer = sharedPref.getString("last_connected_server", "")
                    if (!lastServer.isNullOrEmpty() && vm.isWifiConnectedState.value) {
                        try {
                            val (newClient, devicesList) = withContext(Dispatchers.IO) {
                                connectToServerUnsafe(lastServer, sharedPref)
                            }
                            vm.clientState.value = newClient
                            vm.devicesState.value = devicesList
                            vm.connectedState.value = true
                            vm.currentServerState.value = lastServer
                        } catch (e: Exception) {
                            vm.showServerShutdownWarningState.value = true
                        }
                    }
                }
            },
        )
    }

    // Server switcher sheet (UI)
    // Server switcher sheet (UI)
    if (showServerSwitcher.value) {
        ServerSwitcher(
            currentServer = currentServer.value,
            availableServers = availableServers.value,
            sharedPref = sharedPref,
            onDismiss = { showServerSwitcher.value = false },
            onSwitch = { newServer ->
                if (connected.value) {
                    vm.disconnect(userInitiated = true)
                }
                currentServer.value = newServer
                vm.connectTo(newServer)
            },
            onRefresh = {
                isRefreshing.value = true
                availableServers.value = emptyList()
                vm.scanForServers()
            },
            isRefreshing = isRefreshing.value,
            onAddManual = {
                // Only trigger showing the manual server sheet here
                showManualServerSheet.value = true
            }
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

    // Rating dialog UI (unchanged)
    val showRating = remember { mutableStateOf(showRatingDialog) }

    if (showRating.value) {
        RatingPromptDialog(
            onDismiss = {
                showRating.value = false
                sharedPref.edit { putBoolean("show_rating", false) }
            },
            onRate = {
                showRating.value = false
                sharedPref.edit { putBoolean("show_rating", false) }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = "market://details?id=${context.packageName}".toUri()
                    setPackage("com.android.vending")
                }
                context.startActivity(intent)
            }
        )
    }
}

