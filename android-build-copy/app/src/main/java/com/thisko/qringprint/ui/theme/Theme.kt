package com.thisko.qringprint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.model.ThemeState
import com.thisko.qringprint.ui.glass.GlassTokens

/**
 * 兼容原 token 命名: 所有现有页面用的 Palette.xxx / Metrics.xxx / Typo.xxx
 * 都映射到新的 GlassTokens 上. 11 个 Page 文件不动, 自动获得液态玻璃调色.
 */

object Palette {
    val brand = Color(0xFF7C5CE6)
    val brandPressed = Color(0xFF6A4BD4)
    val cardGradStart = Color(0xFF6E4CE8)
    val cardGradMid = Color(0xFF8B5CF6)
    val cardGradEnd = Color(0xFFA855F7)
    val cardGradOffStart = Color(0xFF9AA0AC)
    val cardGradOffEnd = Color(0xFFB4B9C4)
    fun pageBg(dark: Boolean) = if (dark) Color(0xFF212224) else Color(0xFFF2F3F5)
    fun surface(dark: Boolean) = if (dark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    fun surfaceSunken(dark: Boolean) = if (dark) Color(0xFF2A2A2A) else Color(0xFFF2F3F5)
    fun pressed(dark: Boolean) = if (dark) Color(0xFF383838) else Color(0xFFEDEEF0)
    fun textPrimary(dark: Boolean) = if (dark) Color(0xFFE6E6E6) else Color(0xFF1A1A1A)
    fun textTitle(dark: Boolean) = if (dark) Color(0xFFF0F0F0) else Color(0xFF1F1F1F)
    fun textSecondary(dark: Boolean) = if (dark) Color(0xFF9AA0AC) else Color(0xFF9A9AA0)
    val textOnBrand = Color(0xFFFFFFFF)
    fun iconNeutral(dark: Boolean) = if (dark) Color(0xFFB0B0B8) else Color(0xFF5A5A63)
    val onCardTile = Color(0x38FFFFFF)
    val onCardSubtitle = Color(0xBFFFFFFF)
    val onCardDivider = Color(0x2EFFFFFF)
    val onCardMuted = Color(0x73FFFFFF)
    val online = Color(0xFF3DDC84)
    fun offline(dark: Boolean) = if (dark) Color(0xFF3A3A3A) else Color(0xFFD8DCE3)
    val warning = Color(0xFFFFB020)
    val danger = Color(0xFFFF4D4F)
    val tileAmber = Color(0xFFF7C873)
    val tileMint = Color(0xFF8FD9B6)
    val tileBlue = Color(0xFF9CC4EF)
    val tileLilac = Color(0xFFBDA8F0)
    val tileIcon = Color(0xFFFFFFFF)
    val actionBlue = Color(0xFF3A7BFF)
    fun paper(dark: Boolean) = if (dark) Color(0xFF2E2E2E) else Color(0xFFFFFFFF)
    fun trashColor(dark: Boolean) = if (dark) Color(0xFFFFFFFF) else Color(0xFF1A1A1A)
    fun paperEdge(dark: Boolean) = if (dark) Color(0xFF3D3D3D) else Color(0xFFD8DCE3)
    val selectOutline = Color(0xFF7C5CE6)
    fun elementHint(dark: Boolean) = if (dark) Color(0x33FFFFFF) else Color(0x33000000)
    val handleFill = Color(0xFF7C5CE6)
    val handleEdge = Color(0xFFFFFFFF)
}

object Metrics {
    val pagePadding = 16
    val topBarHeight = 56
    val tabBarHeight = 60
    val tabBarRadius = 24
    val brandBadgeSize = 40
    val brandBadgeRadius = 12
    val brandBadgeIcon = 22
    val topActionSize = 36
    val topActionIcon = 18
    val redDotSize = 7
    val statusCardHeight = 132
    val statusCardRadius = 20
    val statusCardPadding = 16
    val statusTileSize = 48
    val statusTileRadius = 12
    val statusTileIcon = 24
    val statusPillRadius = 14
    val statusDotSize = 6
    val statusMetricIcon = 14
    val actionCardHeight = 152
    val actionCardRadius = 16
    val gridGap = 12
    val tileSize = 56
    val tileRadius = 16
    val tileIcon = 26
    val tabIcon = 22
    val handleSize = 14
    val handleTouch = 34
    val editorToolHeight = 40
}

object Typo {
    val appTitle = 17
    val appSubtitle = 11
    val sectionTitle = 17
    val deviceName = 20
    val deviceSubtitle = 13
    val pill = 12
    val metric = 12
    val actionTitle = 15
    val actionSubtitle = 12
    val tabLabel = 10
}

private val LightColors = lightColorScheme(
    primary = GlassTokens.Brand(),
    onPrimary = GlassTokens.OnBrand(),
    secondary = GlassTokens.BrandPressed(),
    onSecondary = GlassTokens.OnBrand(),
    background = Color(0x00F2F3F5),
    onBackground = GlassTokens.TextPrimary(false),
    surface = Color(0x33FFFFFF),
    onSurface = GlassTokens.TextPrimary(false),
    surfaceVariant = Color(0x22FFFFFF),
    onSurfaceVariant = GlassTokens.TextSecondary(false),
    error = GlassTokens.Danger(),
    onError = GlassTokens.OnBrand(),
)

private val DarkColors = darkColorScheme(
    primary = GlassTokens.Brand(),
    onPrimary = GlassTokens.OnBrand(),
    secondary = GlassTokens.BrandPressed(),
    onSecondary = GlassTokens.OnBrand(),
    background = Color(0x00212529),
    onBackground = GlassTokens.TextPrimary(true),
    surface = Color(0x22FFFFFF),
    onSurface = GlassTokens.TextPrimary(true),
    surfaceVariant = Color(0x14FFFFFF),
    onSurfaceVariant = GlassTokens.TextSecondary(true),
    error = GlassTokens.Danger(),
    onError = GlassTokens.OnBrand(),
)

private val GlassTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 10.sp),
)

@Composable
fun QringPrintTheme(
    content: @Composable () -> Unit,
) {
    val darkOverride = ThemeState.darkOverride.collectAsState().value
    val useDark = darkOverride ?: isSystemInDarkTheme()
    val colors = if (useDark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = GlassTypography,
        content = content,
    )
}

@Composable
fun isAppDark(): Boolean {
    val darkOverride = ThemeState.darkOverride.collectAsState().value
    return darkOverride ?: isSystemInDarkTheme()
}
