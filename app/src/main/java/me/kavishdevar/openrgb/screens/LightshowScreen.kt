@file:OptIn(ExperimentalMaterial3Api::class)

package me.kavishdevar.openrgb.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.kavishdevar.openrgb.models.LocalLightshowStorage
import me.kavishdevar.openrgb.models.LocalProfileStorage
import me.kavishdevar.openrgb.R
import me.kavishdevar.openrgb.logic.LightshowPlayer
import me.kavishdevar.openrgb.models.*
import me.kavishdevar.openrgb.network.OpenRGBClient
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import me.kavishdevar.openrgb.components.GradientButton
import me.kavishdevar.openrgb.logic.getDeviceIcon
import kotlin.math.pow

/* ------------------------------------------------------------------
   Small drag state used for simple "profile -> track" drops
------------------------------------------------------------------ */
private data class DragInfo(
    val name: String,
    val position: Offset // in root coordinates
)

// --- Zoom utilities: normalized zoom [0..1] -> pixels per second (dp per second effectively)
private const val MIN_PPS = 0.5f   // far outzoom: ~0.5dp per second (≈ 30+ minutes on 1080dp)
private const val MAX_PPS = 480f   // far zoom: fine detail (clip editing)

private fun zoomToPixelsPerSecond(z: Float): Float {
    // Exponential curve for smooth feel across huge span
    val ratio = (MAX_PPS / MIN_PPS)
    return MIN_PPS * ratio.pow(z)
}

// Choose tick spacing so timeline header ~every 70–110dp
private fun chooseTickStepSeconds(pxPerSec: Float): Int {
    val targetPx = 90f
    val steps = intArrayOf(1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600)
    return steps.firstOrNull { it * pxPerSec >= targetPx } ?: steps.last()
}





/* ------------------------------------------------------------------
   1.  Main entry composable
------------------------------------------------------------------ */
@Composable
fun LightshowScreen(
    client: OpenRGBClient? = null,
    devices: List<OpenRGBDevice> = emptyList(),
    connected: Boolean = false
) {
    val context = LocalContext.current

    var lightshows by remember { mutableStateOf(LocalLightshowStorage.loadLightshows(context)) }
    var selectedLightshow by remember { mutableStateOf<Lightshow?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        if (isCreating || selectedLightshow != null) {
            LightshowEditor(
                lightshow = selectedLightshow ?: Lightshow(),
                devices = devices,
                client = client,
                connected = connected,
                onSave = { show ->
                    lightshows = if (selectedLightshow != null) {
                        lightshows.map { if (it.id == show.id) show else it }
                    } else lightshows + show
                    LocalLightshowStorage.saveLightshows(context, lightshows)
                    selectedLightshow = null
                    isCreating = false
                },
                onBack = {
                    selectedLightshow = null
                    isCreating = false
                }
            )
        } else {
            LightshowBrowser(
                lightshows = lightshows,
                onCreateNew = { isCreating = true },
                onEdit = { selectedLightshow = it },
                onDelete = {
                    lightshows = lightshows.filter { l -> l.id != it.id }
                    LocalLightshowStorage.saveLightshows(context, lightshows)
                }
            )
        }
    }
}

/* ------------------------------------------------------------------
   2.  Browser screen
------------------------------------------------------------------ */
@Composable
private fun LightshowBrowser(
    lightshows: List<Lightshow>,
    onCreateNew: () -> Unit,
    onEdit: (Lightshow) -> Unit,
    onDelete: (Lightshow) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lightshows", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            GradientButton(
                text = "New Lightshow",
                iconRes = R.drawable.add,
                onClick = onCreateNew,
                modifier = Modifier.width(200.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(lightshows.size) { index ->
                val show = lightshows[index]
                LightshowCard(
                    lightshow = show,
                    onEdit = { onEdit(show) },
                    onDelete = { onDelete(show) } // (Lightshow) -> Unit
                )
            }
        }

        if (lightshows.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.lightshow),
                        null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Text("No lightshows yet", color = Color.Gray)
                    GradientButton("Create First Lightshow", R.drawable.add, onCreateNew)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------
   3.  Editor screen
------------------------------------------------------------------ */
@Composable
private fun LightshowEditor(
    lightshow: Lightshow,
    devices: List<OpenRGBDevice>,
    client: OpenRGBClient?,
    connected: Boolean,
    onSave: (Lightshow) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var currentShow by remember { mutableStateOf(lightshow) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    // normalized zoom 0..1 (0 = far out, 1 = far in)
    var zoom by remember { mutableStateOf(0.5f) }
    val pixelsPerSecond = zoomToPixelsPerSecond(zoom)

    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(0.0) }

    // --- simple drag state: profile name being dragged + pointer position in root ---
    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    // Track bounds in root coordinates (for drop-hit testing)
    val trackBounds = remember { mutableStateMapOf<String, Rect>() }
    var showTrackSelector by remember { mutableStateOf(false) }

    var selectedClip by remember { mutableStateOf<Clip?>(null) }


    /* profiles local + server */
    val localProfiles = LocalProfileStorage.loadProfiles(context)
    val serverProfiles = remember(connected) {
        derivedStateOf {
            if (!connected) emptyList()
            else runBlocking(Dispatchers.IO) {
                kotlin.runCatching {
                    client?.getProfileList()?.map {
                        LocalProfile(name = it, deviceIndex = -1, mode = OpenRGBMode())
                    } ?: emptyList()
                }.getOrDefault(emptyList())
            }
        }
    }
    val allProfiles = remember(localProfiles, serverProfiles.value) {
        localProfiles + serverProfiles.value
    }
    val filteredProfiles = remember(selectedTrack, allProfiles) {
        if (selectedTrack != null)
            allProfiles.filter { it.deviceIndex == selectedTrack?.deviceIndex || it.deviceIndex == -1 }
        else allProfiles
    }

    /* playback */
    val player = remember(client, connected) {
        if (client != null && connected) LightshowPlayer(client, devices) else null
    }
    DisposableEffect(Unit) { onDispose { player?.stop() } }

    val onPlayPause: () -> Unit = {
        isPlaying = !isPlaying
        if (isPlaying) player?.play(currentShow) { currentTime = it } else player?.stop()
    }

    /* add clip helper */
    val onAddClip: (Track, String) -> Unit = { track, name ->
        val new = Clip(
            profileName = name,
            deviceIndex = track.deviceIndex,
            startSeconds = currentTime.coerceAtLeast(0.0),
            durationSeconds = 5.0
        )
        currentShow = currentShow.copy(
            tracks = currentShow.tracks.map { t ->
                if (t.id == track.id) t.copy(clips = (t.clips + new).toMutableList()) else t
            }
        )
    }

    // --- drop resolution when drag ends ---
    fun resolveDropAndAddClip() {
        val info = dragInfo ?: return
        val pos = info.position
        val targetEntry = trackBounds.entries.firstOrNull { (_, rect) ->
            pos.x in rect.left..rect.right && pos.y in rect.top..rect.bottom
        } ?: run { dragInfo = null; return }

        val targetTrack = currentShow.tracks.firstOrNull { it.id.toString() == targetEntry.key }
        if (targetTrack != null) {
            // Find the end of the last clip
            val lastEnd = targetTrack.clips.maxOfOrNull { it.startSeconds + it.durationSeconds } ?: 0.0
            // Snap start to the end of last clip
            val snappedStart = lastEnd

            val newClip = Clip(
                profileName = info.name,
                deviceIndex = targetTrack.deviceIndex,
                startSeconds = snappedStart,
                durationSeconds = 5.0
            )
            currentShow = currentShow.copy(
                tracks = currentShow.tracks.map { t ->
                    if (t.id == targetTrack.id) t.copy(clips = (t.clips + newClip).toMutableList()) else t
                }
            )
        }
        dragInfo = null
    }


    Column(Modifier.fillMaxSize()) {
        LightshowTopBar(
            title = currentShow.name,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause, // () -> Unit exact match
            onBack = onBack,
            onSave = { onSave(currentShow) }
        )

        Box(Modifier.fillMaxSize().weight(1f)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { selectedClip = null })
            }


        ) {
            Column {
                ProfileListSection(
                    profiles = filteredProfiles,
                    currentShow = currentShow,
                    // wire drag control into profile cards
                    onStartDrag = { name, startInRoot -> dragInfo = DragInfo(name, startInRoot) },
                    onDragBy = { delta -> dragInfo = dragInfo?.copy(position = dragInfo!!.position + delta) },
                    onEndDrag = { resolveDropAndAddClip() }
                )
                TimelineSection(
                    tracks = currentShow.tracks,
                    zoom= zoom,
                    pxPerSecond = pixelsPerSecond,
                    currentTime = currentTime,
                    selectedTrack = selectedTrack,
                    onTrackSelected = { selectedTrack = it },
                    selectedClip = selectedClip,
                    onClipSelected = { selectedClip = it },
                    onZoomChanged = { zoom = it },
                    onAddTrack = { showTrackSelector = true },
                    onAddClip = onAddClip,
                    currentShow = currentShow,
                    dragInfo = dragInfo,
                    onTrackBounds = { trackId, rect -> trackBounds[trackId] = rect },
                    onUpdateShow = { updated -> currentShow = updated }
                )
            }

            if (showTrackSelector) {
                TrackSelectorDialog(
                    devices = devices,
                    onDismiss = { showTrackSelector = false },
                    onAddTrack = { deviceIndex, deviceName ->
                        val newTrack = Track(
                            deviceIndex = deviceIndex,
                            name = deviceName
                        )
                        currentShow = currentShow.copy(
                            tracks = currentShow.tracks + newTrack
                        )
                        showTrackSelector = false
                    }
                )
            }

            CurrentTimeIndicator(currentTime, pixelsPerSecond)

            // Drag ghost overlay
            val di = dragInfo
            if (di != null) {
                Box(
                    Modifier
                        .offset { IntOffset(di.position.x.roundToInt(), di.position.y.roundToInt()) }
                        .background(Color(0x883B82F6), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .zIndex(20f)
                ) {
                    Text(di.name, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }



        }
        Text("Zoom = $zoom", color = Color.White)
    }
}




/* ------------------------------------------------------------------
   4.  Helper composables
------------------------------------------------------------------ */
@Composable
private fun ProfileListSection(
    profiles: List<LocalProfile>,
    currentShow: Lightshow,
    onStartDrag: (String, Offset) -> Unit,
    onDragBy: (Offset) -> Unit,
    onEndDrag: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFF1E293B))
            .padding(horizontal = 16.dp)
    ) {
        Text("All Profiles", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles.size) { index ->
                val profile = profiles[index]
                DraggableProfileCard(
                    profile = profile,
                    onStartDrag = onStartDrag,
                    onDragBy = onDragBy,
                    onEndDrag = onEndDrag
                )
            }
        }
    }
}

@Composable
private fun TimelineSection(
    tracks: List<Track>,
    zoom: Float,
    pxPerSecond: Float,
    currentTime: Double,
    selectedTrack: Track?,
    onTrackSelected: (Track) -> Unit,
    selectedClip: Clip?,                    // NEW
    onClipSelected: (Clip?) -> Unit,        // changed: nullable
    onZoomChanged: (Float) -> Unit,
    onAddTrack: () -> Unit,
    onAddClip: (Track, String) -> Unit,
    currentShow: Lightshow,
    dragInfo: DragInfo?,
    onTrackBounds: (trackId: String, rect: Rect) -> Unit,
    onUpdateShow: (Lightshow) -> Unit

) {


    Column(
        Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(Color(0xFF0F172A))
            .pointerInput(zoom) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    // Apply a sensitivity factor (tweak this value, 0.5f = 50% as sensitive)
                    val sensitivity = 0.5f
                    val scaleFactor = 1f + (zoomChange - 1f) * sensitivity

                    // Update zoom
                    var newZoom = (zoom * scaleFactor).coerceIn(0f, 1f)

                    // Round to 3 decimals to avoid twitching
                    newZoom = (newZoom * 1000).toInt() / 1000f

                    onZoomChanged(newZoom)
                }
            }




    ) {

        TimelineHeader(pxPerSecond)

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(tracks.size) { idx ->
                val track = tracks[idx]
                TrackRow(
                    track = track,
                    isSelected = track == selectedTrack,
                    pxPerSecond = pxPerSecond,
                    onTrackClick = {
                        onTrackSelected(track)
                        onClipSelected(null)   // clicking a track background deselects the clip
                    },
                    onClipClick = { onClipSelected(it) },
                    onClipResizeRight = { clip, newDuration ->    // was onClipResize
                        val updated = currentShow.copy(
                            tracks = currentShow.tracks.map { t ->
                                if (t.clips.any { c -> c.id == clip.id }) {
                                    t.copy(
                                        clips = t.clips.map { c ->
                                            if (c.id == clip.id) c.copy(durationSeconds = newDuration) else c
                                        }.toMutableList()
                                    )
                                } else t
                            }
                        )
                        onUpdateShow(updated)
                    },
                    onClipResizeLeft = { clip, newStart, newDuration ->  // NEW: left handle
                        val updated = currentShow.copy(
                            tracks = currentShow.tracks.map { t ->
                                if (t.clips.any { c -> c.id == clip.id }) {
                                    t.copy(
                                        clips = t.clips.map { c ->
                                            if (c.id == clip.id) c.copy(
                                                startSeconds = newStart,
                                                durationSeconds = newDuration
                                            ) else c
                                        }.toMutableList()
                                    )
                                } else t
                            }
                        )
                        onUpdateShow(updated)
                    },
                    selectedClip = selectedClip,
                    onAddClip = onAddClip,
                    currentShow = currentShow,
                    dragInfo = dragInfo,
                    onBounds = { rect -> onTrackBounds(track.id.toString(), rect) }
                )

            }
            item { AddTrackButton(onAddTrack) }
        }
    }
}




@Composable
private fun TrackRow(
    track: Track,
    isSelected: Boolean,
    pxPerSecond: Float,
    onTrackClick: () -> Unit,
    onClipClick: (Clip) -> Unit,
    onClipResizeRight: (Clip, Double) -> Unit,                 // renamed
    onClipResizeLeft: (Clip, Double, Double) -> Unit,          // NEW
    selectedClip: Clip?,
    onAddClip: (Track, String) -> Unit,
    currentShow: Lightshow,
    dragInfo: DragInfo?,
    onBounds: (Rect) -> Unit
) {
    var rowRect by remember { mutableStateOf<Rect?>(null) }
    val isDragOver: Boolean = remember(dragInfo, rowRect) {
        val di = dragInfo
        val r = rowRect
        di != null && r != null &&
                di.position.x in r.left..r.right &&
                di.position.y in r.top..r.bottom
    }

    Row(
        Modifier
            .onGloballyPositioned { coords ->
                val topLeft = coords.positionInRoot()
                val size: Size = coords.size.toSize()
                val rect = Rect(topLeft, size)
                rowRect = rect
                onBounds(rect)
            }
            .fillMaxWidth()
            .height(80.dp)
            .background(if (isSelected) Color(0xFF1E40AF) else Color.Transparent)
            .border(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFF374151))
            .background(if (isDragOver) Color(0x663B82F6) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current
            ) {
                onTrackClick()
            }
    ) {
        Box(
            Modifier
                .width(100.dp)
                .fillMaxHeight()
                .background(Color(0xFF111827))
                .padding(8.dp),
            Alignment.CenterStart
        ) {
            Text(
                track.name,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1F2937))
        ) {
            track.clips.sortedBy { it.startSeconds }.forEach { clip ->
                ClipBlock(
                    clip = clip,
                    pxPerSecond = pxPerSecond,
                    isSelected = clip == selectedClip,
                    onClick = { onClipClick(clip) },
                    onResizeRight = { newDur -> onClipResizeRight(clip, newDur) },
                    onResizeLeft = { newStart, newDur -> onClipResizeLeft(clip, newStart, newDur) }
                )

            }
        }
    }
}


@Composable
private fun DraggableProfileCard(
    profile: LocalProfile,
    onStartDrag: (String, Offset) -> Unit,
    onDragBy: (Offset) -> Unit,
    onEndDrag: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var cardTopLeft by remember { mutableStateOf(Offset.Zero) }

    Card(
        Modifier
            .width(120.dp)
            .height(80.dp)
            .onGloballyPositioned { coords -> cardTopLeft = coords.positionInRoot() }
            .pointerInput(profile.name) {
                detectDragGestures(
                    onDragStart = { pressOffsetInCard ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Convert to root coordinates for consistent hit-testing
                        onStartDrag(profile.name, cardTopLeft + pressOffsetInCard)
                    },
                    onDragEnd = { onEndDrag() },
                    onDragCancel = { onEndDrag() }
                ) { change, dragAmount ->
                    change.consume()
                    onDragBy(dragAmount)
                }
            }
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF374151)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(profile.name, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}
@Composable
private fun ClipBlock(
    clip: Clip,
    pxPerSecond: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    onResizeRight: (Double) -> Unit,
    onResizeLeft: (Double, Double) -> Unit
) {
    // Animate the clip’s start position and width for smoother resize
    val animatedStart by animateDpAsState(
        targetValue = (clip.startSeconds * pxPerSecond).dp,
        label = "clipStartAnim"
    )
    val animatedWidth by animateDpAsState(
        targetValue = (clip.durationSeconds * pxPerSecond).dp,
        label = "clipWidthAnim"
    )

    // Handle metrics
    val handleWidth = 20.dp
    val handleCorner = 8.dp
    val handleVPad = 2.dp
    val minDuration = 0.5

    Box(
        Modifier
            .offset(x = animatedStart)
            .width(animatedWidth)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .background(Color(0xFF3B82F6), RoundedCornerShape(4.dp))
            .then(if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current
            ) { onClick() }
            .padding(4.dp),
        Alignment.Center
    ) {
        Text(
            clip.profileName,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )

        if (isSelected) {
            // LEFT handle
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = -handleWidth)
                    .width(handleWidth)
                    .fillMaxHeight()
                    .padding(top = handleVPad, bottom = handleVPad)
                    .background(Color.White, RoundedCornerShape(handleCorner))
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                // Wait for first down
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                // Drag loop until up/cancel
                                var pointer = down.id
                                var prevX = down.position.x

                                drag(pointer) { change ->
                                    change.consume()
                                    val dx = change.position.x - prevX
                                    prevX = change.position.x

                                    val deltaSec = (dx / pxPerSecond).toDouble()
                                    val newStart = (clip.startSeconds + deltaSec).coerceAtLeast(0.0)
                                    val newDuration = (clip.durationSeconds - deltaSec).coerceAtLeast(minDuration)
                                    onResizeLeft(newStart, newDuration)
                                }
                                // When drag ends (up/cancel), loop restarts, waiting for next down
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.arrow_left),
                    contentDescription = null,
                    tint = Color(0xFF111827)
                )
            }

            // RIGHT handle
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = handleWidth)
                    .width(handleWidth)
                    .fillMaxHeight()
                    .padding(top = handleVPad, bottom = handleVPad)
                    .background(Color.White, RoundedCornerShape(handleCorner))
                    .zIndex(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                var pointer = down.id
                                var prevX = down.position.x

                                drag(pointer) { change ->
                                    change.consume()
                                    val dx = change.position.x - prevX
                                    prevX = change.position.x

                                    val deltaSec = (dx / pxPerSecond).toDouble()
                                    val newDuration = (clip.durationSeconds + deltaSec).coerceAtLeast(minDuration)
                                    onResizeRight(newDuration)
                                }
                            }
                        }
                    }

                ,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.arrow_right),
                    contentDescription = null,
                    tint = Color(0xFF111827)
                )
            }
        }
    }
}









@Composable
private fun AddTrackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current
            ) {
                onClick()
            }
            .background(Color(0xFF111827)),
        Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.add_track),
                null,
                tint = Color(0xFF6B7280)
            )
            Text("Add Device Track", color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun CurrentTimeIndicator(currentTime: Double, pxPerSecond: Float) {
    val deviceNameColumnWidth = 100.dp
    val offset by animateDpAsState(
        targetValue = deviceNameColumnWidth + (currentTime.toFloat() * pxPerSecond).dp,
        label = "current-time-indicator"
    )
    Box(
        Modifier
            .offset(x = offset)
            .width(2.dp)
            .fillMaxHeight()
            .background(Color(0xFFFF5252), RoundedCornerShape(1.dp))
            .zIndex(10f)
    )
}




@Composable
private fun TimelineHeader(pxPerSecond: Float) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Color(0xFF111827))
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val visibleSeconds = (widthPx / pxPerSecond).toInt().coerceAtLeast(0)
        val step = chooseTickStepSeconds(pxPerSecond)

        for (sec in 0..visibleSeconds step step) {
            val x = with(density) { (sec * pxPerSecond).toDp() }
            Box(
                Modifier
                    .offset(x = x)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF374151))
            ) {
                Text(
                    "$sec s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }
}


@Composable
private fun LightshowTopBar(
    title: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit, // <-- FIXED: exact () -> Unit
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSave) { Icon(Icons.Default.Lock, null, tint = Color.White) }
            Spacer(Modifier.width(16.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF3B82F6), CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                    null,
                    tint = Color.White
                )
            }
        }
    }
}

/* ------------------------------------------------------------------
   5.  Card for the browser
------------------------------------------------------------------ */
@Composable
private fun LightshowCard(
    lightshow: Lightshow,
    onEdit: () -> Unit,
    onDelete: (Lightshow) -> Unit // <-- FIXED signature
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(lightshow.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(
                    "${lightshow.tracks.size} tracks • ${lightshow.lengthSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Lightshow?") },
            text = { Text("Are you sure you want to delete \"${lightshow.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(lightshow) // <-- FIXED call
                        showDeleteDialog = false
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
@Composable
private fun TrackSelectorDialog(
    devices: List<OpenRGBDevice>,
    onDismiss: () -> Unit,
    onAddTrack: (Int, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Device") },
        text = {
            LazyColumn {
                items(devices) { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(getDeviceIcon(device.type)),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current,
                            onClick = {
                                onAddTrack(
                                    devices.indexOf(device),
                                    device.name
                                )
                            }
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF0F172A)
    )
}
