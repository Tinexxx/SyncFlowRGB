package me.kavishdevar.openrgb.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun CompactHsvColorPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val controller = rememberColorPickerController()

    HsvColorPicker(
        modifier = modifier
            .width(150.dp)
            .height(150.dp)
            .padding(8.dp),
        controller = controller,
        initialColor = initialColor,
        onColorChanged = { envelope ->
            onColorChange(envelope.color)
        }
    )
}