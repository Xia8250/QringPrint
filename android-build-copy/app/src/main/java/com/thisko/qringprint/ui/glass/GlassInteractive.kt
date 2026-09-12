package com.thisko.qringprint.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** GlassButton -- 主按钮 (含可选 leading icon). */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    dark: Boolean = false,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val tint = if (primary) GlassTokens.Brand().copy(alpha = 0.55f)
               else if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    val textColor = if (primary) GlassTokens.OnBrand() else GlassTokens.TextPrimary(dark)
    val activeTint = when {
        !enabled -> tint.copy(alpha = 0.18f)
        pressed -> tint.copy(alpha = (tint.alpha + 0.18f).coerceAtMost(0.8f))
        else -> tint
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .softShadow(radius = GlassTokens.ShadowButton, cornerRadius = GlassTokens.RadiusButton)
            .glassLayer(GlassTokens.BlurButton, GlassTokens.RadiusButton, tint = activeTint, dark = dark)
            .clickable(
                interactionSource = src,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = textColor, maxLines = 1, fontSize = 15.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** GlassIconButton -- 圆形图标按钮. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    primary: Boolean = false,
    dark: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val tint = if (primary) GlassTokens.Brand().copy(alpha = 0.55f)
               else if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    val iconColor = if (primary) GlassTokens.OnBrand() else GlassTokens.TextPrimary(dark)
    val activeTint = if (pressed) tint.copy(alpha = (tint.alpha + 0.18f).coerceAtMost(0.8f)) else tint

    Box(
        modifier = modifier
            .size(size)
            .softShadow(radius = GlassTokens.ShadowButton, cornerRadius = 50.dp)
            .glassLayer(GlassTokens.BlurButton, 50.dp, tint = activeTint, dark = dark)
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconColor, modifier = Modifier.size(size * 0.5f))
    }
}

/** GlassChip -- 标签/分类. */
@Composable
fun GlassChip(
    text: String,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val tint = if (selected) GlassTokens.Brand().copy(alpha = 0.55f)
               else if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    val textColor = if (selected) GlassTokens.OnBrand() else GlassTokens.TextPrimary(dark)
    val mod = modifier
        .height(32.dp)
        .glassLayer(GlassTokens.BlurChip, GlassTokens.RadiusChip, tint = tint, dark = dark)
    val clickMod = if (onClick != null) {
        mod.clickable(
            interactionSource = src,
            indication = null,
            onClick = onClick,
        )
    } else mod

    Box(
        modifier = clickMod.border(
            BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f)),
            RoundedCornerShape(GlassTokens.RadiusChip),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** GlassSwitch -- 开关 (液态玻璃, knob 独立 glass). */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val onTint = GlassTokens.Brand().copy(alpha = 0.65f)
    val offTint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    val tint = if (checked) onTint else offTint
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .softShadow(radius = GlassTokens.ShadowButton, cornerRadius = 15.dp)
            .glassLayer(GlassTokens.BlurChip, 15.dp, tint = tint, dark = dark)
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        val knobX = if (checked) 26.dp else 4.dp
        Box(
            modifier = Modifier
                .padding(start = knobX, top = 4.dp)
                .size(22.dp)
                .shadow(4.dp, RoundedCornerShape(11.dp), clip = false)
                .glassLayer(GlassTokens.BlurChip, 11.dp, tint = Color.White, dark = false)
                .border(0.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(11.dp)),
        )
    }
}

/** GlassSegmentedControl -- 多选一 (内嵌 RowScope.weight 真实工作). */
@Composable
fun GlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Row(
        modifier = modifier
            .height(40.dp)
            .glassLayer(GlassTokens.BlurChip, 20.dp, tint = tint, dark = dark)
            .padding(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            val segTint = if (sel) GlassTokens.Brand().copy(alpha = 0.55f) else Color.Transparent
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .glassLayer(GlassTokens.BlurChip, 18.dp, tint = segTint, dark = dark)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelected(i) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) GlassTokens.OnBrand() else GlassTokens.TextPrimary(dark),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}



