package com.thisko.qringprint.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** GlassDialog -- 弹窗. 用原生 Dialog 包一个 glass content. */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit = {},
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .shadow(GlassTokens.ShadowDialog, RoundedCornerShape(GlassTokens.RadiusDialog), clip = false)
                .glassLayer(GlassTokens.BlurDialog, GlassTokens.RadiusDialog, tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight, dark = dark)
                .border(0.8.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(GlassTokens.RadiusDialog))
                .padding(20.dp),
        ) {
            Column {
                Text(title, color = GlassTokens.TextPrimary(dark), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                content()
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        dismissText,
                        color = GlassTokens.TextSecondary(dark),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismissRequest,
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        confirmText,
                        color = GlassTokens.Brand(),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onConfirm,
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** GlassSheet -- 底部弹出面板. (简化: 用 Dialog 模拟) */
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismissRequest,
        ), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .shadow(GlassTokens.ShadowDialog, RoundedCornerShape(topStart = GlassTokens.RadiusSheet, topEnd = GlassTokens.RadiusSheet), clip = false)
                    .glassLayer(GlassTokens.BlurSheet, GlassTokens.RadiusSheet, tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight, dark = dark)
                    .border(0.8.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(topStart = GlassTokens.RadiusSheet, topEnd = GlassTokens.RadiusSheet))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* swallow */ },
                    )
                    .padding(20.dp),
            ) {
                Column { content() }
            }
        }
    }
}

/** GlassMenu -- 上下文菜单 (popup 风格). */
@Composable
fun GlassMenu(
    items: List<String>,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    dark: Boolean = false,
) {
    androidx.compose.ui.window.Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .shadow(GlassTokens.ShadowCard, RoundedCornerShape(GlassTokens.RadiusCard), clip = false)
                .glassLayer(GlassTokens.BlurCard, GlassTokens.RadiusCard, tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight, dark = dark)
                .border(0.8.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(GlassTokens.RadiusCard))
                .padding(6.dp),
        ) {
            items.forEachIndexed { i, label ->
                GlassMenuItem(text = label, onClick = { onItemClick(i); onDismiss() }, dark = dark)
            }
        }
    }
}

/** GlassMenuItem -- 菜单项. */
@Composable
fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dark: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = GlassTokens.IconNeutral(dark), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = GlassTokens.TextPrimary(dark), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}



