package me.kavishdevar.openrgb.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kavishdevar.openrgb.screens.DirectControlScreen
import me.kavishdevar.openrgb.EffectsSubTab
import me.kavishdevar.openrgb.components.DropdownMenu
import me.kavishdevar.openrgb.components.GradientButton
import me.kavishdevar.openrgb.components.ProfileCard
import me.kavishdevar.openrgb.R
import me.kavishdevar.openrgb.components.StepProgressBar
import me.kavishdevar.openrgb.components.CompactHsvColorPicker
import me.kavishdevar.openrgb.logic.getDeviceIcon
import me.kavishdevar.openrgb.models.LocalProfile
import me.kavishdevar.openrgb.models.LocalProfileStorage
import me.kavishdevar.openrgb.models.OpenRGBColor
import me.kavishdevar.openrgb.models.OpenRGBDevice
import me.kavishdevar.openrgb.models.OpenRGBMode
import me.kavishdevar.openrgb.models.OpenRGBProfile
import me.kavishdevar.openrgb.network.OpenRGBClient
import me.kavishdevar.openrgb.components.EffectsSubTab

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
        EffectsSubTab(selectedTab = selectedSubTab, onTabSelected = onSubTabSelected)

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
                                DropdownMenu(
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

                                if (selectedMode?.hasSpeed() == true && selectedMode!!.speedMax>selectedMode!!.speedMin) {

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