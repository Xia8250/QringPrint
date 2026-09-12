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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 单个 tab item, label/icon/onClick 三元组. */
data class GlassBottomItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** GlassAppBar -- 顶部应用栏. */
@Composable
fun GlassAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: List<Pair<ImageVector, () -> Unit>> = emptyList(),
    dark: Boolean = false,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .softShadow(radius = GlassTokens.ShadowBar, cornerRadius = 0.dp)
            .glassLayer(GlassTokens.BlurBar, 0.dp, tint = tint, dark = dark)
            .border(0.5.dp, Color.White.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null && onNavigationClick != null) {
                GlassIconButton(
                    icon = navigationIcon,
                    contentDescription = null,
                    onClick = onNavigationClick,
                    size = 40.dp,
                    dark = dark,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                color = GlassTokens.TextPrimary(dark),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions.forEach { (icon, onClick) ->
                GlassIconButton(icon = icon, contentDescription = null, onClick = onClick, size = 40.dp, dark = dark)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/** GlassBottomBar -- 底部导航栏. */
@Composable
fun GlassBottomBar(
    items: List<GlassBottomItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .softShadow(radius = GlassTokens.ShadowBar, cornerRadius = GlassTokens.RadiusBar),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassLayer(GlassTokens.BlurBar, GlassTokens.RadiusBar, tint = tint, dark = dark)
                .background(
                    if (dark) Color(0x33232936) else Color(0x33F5F2FF),
                    RoundedCornerShape(GlassTokens.RadiusBar),
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.58f), RoundedCornerShape(GlassTokens.RadiusBar)),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val sel = i == selectedIndex
                val activeColor = if (sel) GlassTokens.OnBrand() else GlassTokens.TextSecondary(dark)
                val activeBg = if (sel) GlassTokens.Brand().copy(alpha = 0.6f) else Color.Transparent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = item.onClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp, 28.dp)
                                .background(activeBg, RoundedCornerShape(14.dp))
                                .border(
                                    if (sel) 0.6.dp else 0.dp,
                                    if (sel) Color.White.copy(alpha = 0.48f) else Color.Transparent,
                                    RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon, contentDescription = null, tint = if (sel) GlassTokens.OnBrand() else GlassTokens.IconNeutral(dark), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            color = activeColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** GlassTabBar -- 顶部/中部 tab. */
@Composable
fun GlassTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .glassLayer(GlassTokens.BlurChip, 22.dp, tint = tint, dark = dark)
            .padding(2.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, label ->
                val sel = i == selectedIndex
                val segTint = if (sel) GlassTokens.Brand().copy(alpha = 0.55f) else Color.Transparent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .glassLayer(GlassTokens.BlurChip, 20.dp, tint = segTint, dark = dark)
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
}

/** GlassSideBar -- 侧边栏. */
@Composable
fun GlassSideBar(
    items: List<Pair<String, ImageVector>>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .softShadow(radius = GlassTokens.ShadowBar, shape = RoundedCornerShape(topEnd = GlassTokens.RadiusBar, bottomEnd = GlassTokens.RadiusBar))
            .glassLayer(GlassTokens.BlurBar, GlassTokens.RadiusBar, tint = tint, dark = dark)
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(topEnd = GlassTokens.RadiusBar, bottomEnd = GlassTokens.RadiusBar))
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val sel = i == selectedIndex
            val activeBg = if (sel) GlassTokens.Brand().copy(alpha = 0.55f) else Color.Transparent
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelected(i) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 28.dp)
                        .glassLayer(GlassTokens.BlurChip, 14.dp, tint = activeBg, dark = dark),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = if (sel) GlassTokens.OnBrand() else GlassTokens.IconNeutral(dark), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    color = if (sel) GlassTokens.OnBrand() else GlassTokens.TextSecondary(dark),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** GlassToolbar -- 工具栏 (按钮组). */
@Composable
fun GlassToolbar(
    actions: List<Triple<ImageVector, String, () -> Unit>>,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val tint = if (dark) GlassTokens.TintDark else GlassTokens.TintLight
    Row(
        modifier = modifier
            .height(48.dp)
            .glassLayer(GlassTokens.BlurChip, 24.dp, tint = tint, dark = dark)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (icon, _, onClick) ->
            GlassIconButton(icon = icon, contentDescription = null, onClick = onClick, size = 40.dp, dark = dark)
        }
    }
}



