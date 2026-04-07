package me.kavishdevar.openrgb.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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