// SettingsScreen.kt
package me.kavishdevar.openrgb.screens

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity

// --- Data helpers ----------------------------------------------------------------
data class VisualSettings(
    val backgroundMode: BackgroundMode = BackgroundMode.Gradient,
    val solidColorInt: Int = AndroidColor.BLACK,
    val gradientColorAInt: Int = AndroidColor.rgb(8, 2, 64),
    val gradientColorBInt: Int = AndroidColor.BLACK,
    val showSmallPillButtons: Boolean = true,
    val bottomNavOutlineColorInt: Int = AndroidColor.WHITE,
    val connectionBarColorConnectedInt: Int = AndroidColor.rgb(70, 130, 194),
    val connectionBarColorDisconnectedInt: Int = AndroidColor.rgb(42, 45, 62)
)

enum class BackgroundMode { Solid, Gradient }

// Convert helpers between Compose Color and Android color int
fun colorIntToCompose(@androidx.annotation.ColorInt i: Int): Color {
    val a = AndroidColor.alpha(i)
    val r = AndroidColor.red(i)
    val g = AndroidColor.green(i)
    val b = AndroidColor.blue(i)
    return Color(r / 255f, g / 255f, b / 255f, a / 255f)
}
fun composeColorToInt(c: Color): Int {
    val a = (c.alpha * 255).toInt().coerceIn(0, 255)
    val r = (c.red * 255).toInt().coerceIn(0, 255)
    val g = (c.green * 255).toInt().coerceIn(0, 255)
    val b = (c.blue * 255).toInt().coerceIn(0, 255)
    return AndroidColor.argb(a, r, g, b)
}

// Persist / load from SharedPreferences
private const val PREF_NAME = "visual_prefs"
private const val KEY_BG_MODE = "bg_mode"
private const val KEY_SOLID = "solid_color"
private const val KEY_GA = "grad_a"
private const val KEY_GB = "grad_b"
private const val KEY_SMALL_PILLS = "small_pills"
private const val KEY_BOTTOM_OUTLINE = "bottom_outline"
private const val KEY_CONN_CONNECTED = "conn_connected"
private const val KEY_CONN_DISCONNECTED = "conn_disconnected"

fun loadVisualSettings(context: Context): VisualSettings {
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val bgMode = when (prefs.getString(KEY_BG_MODE, "gradient")) {
        "solid" -> BackgroundMode.Solid
        else -> BackgroundMode.Gradient
    }
    return VisualSettings(
        backgroundMode = bgMode,
        solidColorInt = prefs.getInt(KEY_SOLID, AndroidColor.BLACK),
        gradientColorAInt = prefs.getInt(KEY_GA, AndroidColor.rgb(8, 2, 64)),
        gradientColorBInt = prefs.getInt(KEY_GB, AndroidColor.BLACK),
        showSmallPillButtons = prefs.getBoolean(KEY_SMALL_PILLS, true),
        bottomNavOutlineColorInt = prefs.getInt(KEY_BOTTOM_OUTLINE, AndroidColor.WHITE),
        connectionBarColorConnectedInt = prefs.getInt(KEY_CONN_CONNECTED, AndroidColor.rgb(70, 130, 194)),
        connectionBarColorDisconnectedInt = prefs.getInt(KEY_CONN_DISCONNECTED, AndroidColor.rgb(42, 45, 62))
    )
}

fun saveVisualSettings(context: Context, s: VisualSettings) {
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(KEY_BG_MODE, if (s.backgroundMode == BackgroundMode.Solid) "solid" else "gradient")
        .putInt(KEY_SOLID, s.solidColorInt)
        .putInt(KEY_GA, s.gradientColorAInt)
        .putInt(KEY_GB, s.gradientColorBInt)
        .putBoolean(KEY_SMALL_PILLS, s.showSmallPillButtons)
        .putInt(KEY_BOTTOM_OUTLINE, s.bottomNavOutlineColorInt)
        .putInt(KEY_CONN_CONNECTED, s.connectionBarColorConnectedInt)
        .putInt(KEY_CONN_DISCONNECTED, s.connectionBarColorDisconnectedInt)
        .apply()
}

// --- Small helper to convert HSV -> Compose Color using Android util
private fun hsvToComposeColor(h: Float, s: Float = 1f, v: Float = 1f): Color {
    val hsv = floatArrayOf(h, s, v)
    val argb = AndroidColor.HSVToColor(hsv)
    return colorIntToCompose(argb)
}

private fun composeToHsv(c: Color): FloatArray {
    val out = FloatArray(3)
    AndroidColor.colorToHSV(composeColorToInt(c), out)
    return out
}

// --- Linear hue line picker (single-line, tap + drag) ---------------------------
@Composable
fun HueLinePicker(
    initialColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    onColorChange: (Color) -> Unit
) {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(1f) }
    val initialHsv = remember { composeToHsv(initialColor) }
    var hue by remember { mutableStateOf(initialHsv[0]) }

    // build gradient colors (coarse steps are fine and inexpensive)
    val gradientColors = remember {
        (0..360 step 6).map { step -> hsvToComposeColor(step.toFloat(), 1f, 1f) }
    }

    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                widthPx = coords.size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val x = offset.x.coerceIn(0f, widthPx)
                        hue = (x / widthPx) * 360f
                        onColorChange(hsvToComposeColor(hue))
                    },
                    onDrag = { change, _ ->
                        val x = change.position.x.coerceIn(0f, widthPx)
                        hue = (x / widthPx) * 360f
                        onColorChange(hsvToComposeColor(hue))
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // draw the hue gradient
            drawRoundRect(
                brush = Brush.horizontalGradient(gradientColors),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                size = size
            )

            // thumb position
            val cx = (hue / 360f) * size.width
            val cy = size.height / 2f
            val thumbRadius = (height.toPx() / 2.2f).coerceAtLeast(6f)

            // thumb background (color) and white outline
            drawCircle(color = hsvToComposeColor(hue), radius = thumbRadius, center = Offset(cx, cy))
            drawCircle(color = Color.White, radius = thumbRadius, center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

// --- Settings UI -----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    // optional initial settings param (if you already loaded one)
    initial: VisualSettings? = null
) {
    val ctx = LocalContext.current
    val loaded = remember { initial ?: loadVisualSettings(ctx) }

    // local ui state
    var bgMode by remember { mutableStateOf(loaded.backgroundMode) }
    var solidColor by remember { mutableStateOf(colorIntToCompose(loaded.solidColorInt)) }
    var gradA by remember { mutableStateOf(colorIntToCompose(loaded.gradientColorAInt)) }
    var gradB by remember { mutableStateOf(colorIntToCompose(loaded.gradientColorBInt)) }
    var showSmallPills by remember { mutableStateOf(loaded.showSmallPillButtons) }
    var bottomOutline by remember { mutableStateOf(colorIntToCompose(loaded.bottomNavOutlineColorInt)) }
    var connConnected by remember { mutableStateOf(colorIntToCompose(loaded.connectionBarColorConnectedInt)) }
    var connDisconnected by remember { mutableStateOf(colorIntToCompose(loaded.connectionBarColorDisconnectedInt)) }

    // Show as a modal dialog so pointer events are correctly captured.
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1724))
        ) {
            Column(Modifier.padding(16.dp)) {
                // Top row: title + Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClose) {
                        Text("Close", color = Color.LightGray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Background mode toggles
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { bgMode = BackgroundMode.Solid },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bgMode == BackgroundMode.Solid) Color(0xFF2E3B50) else Color(0xFF10141A)
                        )
                    ) { Text("Solid", color = Color.White) }
                    Button(
                        onClick = { bgMode = BackgroundMode.Gradient },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bgMode == BackgroundMode.Gradient) Color(0xFF2E3B50) else Color(0xFF10141A)
                        )
                    ) { Text("Gradient", color = Color.White) }
                }

                Spacer(Modifier.height(12.dp))

                // Color controls
                if (bgMode == BackgroundMode.Solid) {
                    Text("Solid background color", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    HueLinePicker(
                        initialColor = solidColor,
                        modifier = Modifier.fillMaxWidth(),
                        onColorChange = { solidColor = it }
                    )
                } else {
                    Text("Gradient color A", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    HueLinePicker(
                        initialColor = gradA,
                        modifier = Modifier.fillMaxWidth(),
                        onColorChange = { gradA = it }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Gradient color B", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    HueLinePicker(
                        initialColor = gradB,
                        modifier = Modifier.fillMaxWidth(),
                        onColorChange = { gradB = it }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Small pill toggle
                Row(verticalAlignment = CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Show small pill buttons", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = showSmallPills, onCheckedChange = { showSmallPills = it })
                }

                Spacer(Modifier.height(12.dp))

                // Bottom outline color
                Text("Bottom nav outline color", color = Color.White)
                Spacer(Modifier.height(8.dp))
                HueLinePicker(
                    initialColor = bottomOutline,
                    modifier = Modifier.fillMaxWidth(),
                    onColorChange = { bottomOutline = it }
                )

                Spacer(Modifier.height(12.dp))

                // Connection bar colors
                Text("Connection bar - Connected", color = Color.White)
                Spacer(Modifier.height(8.dp))
                HueLinePicker(
                    initialColor = connConnected,
                    modifier = Modifier.fillMaxWidth(),
                    onColorChange = { connConnected = it }
                )
                Spacer(Modifier.height(8.dp))
                Text("Connection bar - Disconnected", color = Color.White)
                Spacer(Modifier.height(8.dp))
                HueLinePicker(
                    initialColor = connDisconnected,
                    modifier = Modifier.fillMaxWidth(),
                    onColorChange = { connDisconnected = it }
                )

                Spacer(Modifier.height(18.dp))

                // Actions row
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onClose) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        // persist
                        val toSave = VisualSettings(
                            backgroundMode = bgMode,
                            solidColorInt = composeColorToInt(solidColor),
                            gradientColorAInt = composeColorToInt(gradA),
                            gradientColorBInt = composeColorToInt(gradB),
                            showSmallPillButtons = showSmallPills,
                            bottomNavOutlineColorInt = composeColorToInt(bottomOutline),
                            connectionBarColorConnectedInt = composeColorToInt(connConnected),
                            connectionBarColorDisconnectedInt = composeColorToInt(connDisconnected)
                        )
                        saveVisualSettings(ctx, toSave)
                        // close and let the caller reload/apply
                        onClose()
                    }) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }
    }
}
