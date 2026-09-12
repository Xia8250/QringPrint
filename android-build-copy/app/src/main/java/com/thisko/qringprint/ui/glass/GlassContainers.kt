package com.thisko.qringprint.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thisko.qringprint.ui.glass.GlassTokens

/** 内部辅助: 叠加 1px 内白描边 + 阴影 + 内容. 跟 glassLayer 组合使用 */
@Composable
internal fun GlassFrame(
    modifier: Modifier = Modifier,
    cornerRadius: Dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(
                BorderStroke(
                    GlassTokens.StrokeWidth,
                    GlassTokens.StrokeColorInner,
                ),
                RoundedCornerShape(cornerRadius),
            )
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/** GlassContainer -- 基础容器 (无阴影, 无描边). 用作 group 容器. */
@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassTokens.RadiusCard,
    dark: Boolean = false,
    tint: Color = if (dark) GlassTokens.TintDark else GlassTokens.TintLight,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val mod = modifier
        .glassLayer(GlassTokens.BlurCard, cornerRadius, tint = tint, dark = dark)
    val withClick = if (onClick != null) {
        mod.then(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        )
    } else mod
    GlassFrame(
        modifier = withClick,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
    ) { content() }
}

/** GlassCard -- 带阴影的卡片. 用于内容分组, 浮在背景上. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassTokens.RadiusCard,
    dark: Boolean = false,
    tint: Color = if (dark) GlassTokens.TintDark else GlassTokens.TintLight,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val mod = modifier
        .softShadow(radius = GlassTokens.ShadowCard, cornerRadius = cornerRadius)




        .glassLayer(GlassTokens.BlurCard, cornerRadius, tint = tint, dark = dark)
    val withClick = if (onClick != null) {
        mod.then(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        )
    } else mod
    GlassFrame(
        modifier = withClick,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
    ) { content() }
}

/** GlassPanel -- 大块主面板, blur 强, 圆角更大. 用于首页 printer status card 等主区域. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassTokens.RadiusPanel,
    dark: Boolean = false,
    tint: Color = if (dark) GlassTokens.TintDark else GlassTokens.TintLight,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val mod = modifier
        .softShadow(radius = GlassTokens.ShadowCard, cornerRadius = cornerRadius)




        .glassLayer(GlassTokens.BlurPanel, cornerRadius, tint = tint, dark = dark)
    val withClick = if (onClick != null) {
        mod.then(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        )
    } else mod
    GlassFrame(
        modifier = withClick,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
    ) { content() }
}



