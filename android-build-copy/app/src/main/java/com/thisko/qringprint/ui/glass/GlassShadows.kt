package com.thisko.qringprint.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 柔和双层阴影:
 *  - 第一层 (近, 浅): 模拟贴近玻璃的细腻投影
 *  - 第二层 (远, 极淡): 模拟环境光散射
 * 比单一 shadow(elevation) 更接近真实玻璃的"漂浮感", 不会出现硬边.
 */
fun Modifier.softShadow(
    radius: Dp = 18.dp,
    cornerRadius: Dp = 24.dp,
    shape: Shape? = null,
    dark: Boolean = false,
): Modifier = this
    .shadow(
        elevation = (radius.value * 0.6f).coerceAtLeast(1f).dp,
        shape = shape ?: RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = if (dark) Color(0x33000000) else Color(0x14000000),
        spotColor = if (dark) Color(0x33000000) else Color(0x1F000000),
    )
    .shadow(
        elevation = (radius.value * 0.25f).coerceAtLeast(1f).dp,
        shape = shape ?: RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = Color.Transparent,
        spotColor = if (dark) Color(0x1A000000) else Color(0x0A000000),
    )