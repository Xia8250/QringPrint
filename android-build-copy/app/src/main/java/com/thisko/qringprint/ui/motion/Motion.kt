package com.thisko.qringprint.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Modifier.pageEnter(delayMillis: Int = 0): Modifier {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f),
        label = "page-alpha",
    )
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = spring(stiffness = 360f, dampingRatio = 0.82f),
        label = "page-translation",
    )
    return graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY.dp.toPx()
    }
}

@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.965f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}
