package com.thisko.qringprint.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 内部: 玻璃输入框底座. */
@Composable
private fun GlassInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String?,
    leading: ImageVector?,
    trailing: (@Composable () -> Unit)?,
    visualTransformation: VisualTransformation,
    keyboardType: KeyboardType,
    singleLine: Boolean,
    minHeight: Dp,
    dark: Boolean,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    val textColor = GlassTokens.TextPrimary(dark)
    val placeholderColor = GlassTokens.TextSecondary(dark)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .softShadow(radius = GlassTokens.ShadowButton, cornerRadius = GlassTokens.RadiusButton)
            .glassLayer(GlassTokens.BlurButton, GlassTokens.RadiusButton, tint = tint, dark = dark)
            .border(
                BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f)),
                RoundedCornerShape(GlassTokens.RadiusButton),
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = GlassTokens.IconNeutral(dark), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = placeholderColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = 14.sp),
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(GlassTokens.Brand()),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** GlassTextField -- 单行输入. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    dark: Boolean = false,
) {
    GlassInputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leading = leading,
        trailing = trailing,
        visualTransformation = VisualTransformation.None,
        keyboardType = KeyboardType.Text,
        singleLine = true,
        minHeight = 48.dp,
        dark = dark,
    )
}

/** GlassPasswordField -- 密码输入 (带可见性切换). */
@Composable
fun GlassPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    dark: Boolean = false,
    visible: Boolean = false,
    onToggleVisible: () -> Unit = {},
) {
    GlassInputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leading = null,
        trailing = {
            androidx.compose.material3.IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Filled.Add
                    else Icons.Filled.Check,
                    contentDescription = null,
                    tint = GlassTokens.IconNeutral(dark),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardType = KeyboardType.Password,
        singleLine = true,
        minHeight = 48.dp,
        dark = dark,
    )
}

/** GlassTextArea -- 多行输入. */
@Composable
fun GlassTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    dark: Boolean = false,
    minHeight: Dp = 120.dp,
) {
    GlassInputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = minHeight),
        placeholder = placeholder,
        leading = null,
        trailing = null,
        visualTransformation = VisualTransformation.None,
        keyboardType = KeyboardType.Text,
        singleLine = false,
        minHeight = minHeight,
        dark = dark,
    )
}

/** GlassSearchBar -- 搜索条 (左侧放大镜 icon, 整体玻璃). */
@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    dark: Boolean = false,
    onSearch: (() -> Unit)? = null,
) {
    GlassInputBox(
        value = value,
        onValueChange = onValueChange,
        modifier = if (onSearch != null) modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onSearch,
        ) else modifier,
        placeholder = placeholder,
        leading = Icons.Filled.Menu,
        trailing = null,
        visualTransformation = VisualTransformation.None,
        keyboardType = KeyboardType.Text,
        singleLine = true,
        minHeight = 44.dp,
        dark = dark,
    )
}

/** GlassPicker -- 滚轮选择器 (简化为"下拉式", 用 Click 触发). */
@Composable
fun GlassPicker(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .softShadow(radius = GlassTokens.ShadowButton, cornerRadius = GlassTokens.RadiusButton)
            .glassLayer(GlassTokens.BlurButton, GlassTokens.RadiusButton, tint = tint, dark = dark)
            .border(
                BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f)),
                RoundedCornerShape(GlassTokens.RadiusButton),
            )
            .clickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = GlassTokens.TextSecondary(dark), fontSize = 13.sp, modifier = Modifier.width(96.dp))
            Text(value, color = GlassTokens.TextPrimary(dark), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = GlassTokens.IconNeutral(dark),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** GlassFormField -- 带 label + 错误提示的表单域. */
@Composable
fun GlassFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    dark: Boolean = false,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(label, color = GlassTokens.TextSecondary(dark), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            dark = dark,
        )
        if (error != null) {
            Text(error, color = GlassTokens.Danger(), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}



