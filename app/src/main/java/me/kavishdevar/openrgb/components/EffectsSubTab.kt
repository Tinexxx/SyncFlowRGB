package me.kavishdevar.openrgb.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kavishdevar.openrgb.EffectsSubTab

@Composable
fun EffectsSubTab(
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