package com.thisko.qringprint.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass 物理参数集中处.
 * 改一个值, 全 app 同步生效. 不要在组件里散落硬编码.
 *
 * 兼容原 Palette 命名 (小写), 11 个现有 Page 文件无需改动.
 */
object GlassTokens {

    // === 物理参数 ===
    const val Ior: Float = 1.45f
    const val FresnelPower: Float = 4.0f
    const val Dispersion: Float = 0.012f
    const val Specular: Float = 0.85f
    const val SSS: Float = 0.55f
    const val Grain: Float = 0.012f

    // === Blur ===
    val BlurCard = 18.dp
    val BlurPanel = 28.dp
    val BlurBar = 24.dp
    val BlurDialog = 36.dp
    val BlurSheet = 32.dp
    val BlurChip = 14.dp
    val BlurButton = 10.dp

    // === 半径 (原 Palette 用 dp 值, 这里也保持 dp) ===
    val RadiusCard = 24.dp
    val RadiusPanel = 32.dp
    val RadiusButton = 18.dp
    val RadiusChip = 14.dp
    val RadiusBar = 28.dp
    val RadiusSheet = 32.dp
    val RadiusDialog = 28.dp
    val RadiusPill = 999.dp

    // === 描边 ===
    val StrokeWidth = 0.8.dp
    val StrokeColorInner = Color(0x66FFFFFF)
    val StrokeColorOuter = Color(0x33FFFFFF)

    // === 玻璃基色 (tint) ===
    val TintLight = Color(0xFFF7F5FF)
    val TintDark = Color(0xFF2A2F38)
    val TintAlphaLight: Float = 0.30f
    val TintAlphaDark: Float = 0.32f

    // === 阴影 ===
    val ShadowColor = Color(0x38000000)
    val ShadowCard = 10.dp
    val ShadowBar = 8.dp
    val ShadowDialog = 18.dp
    val ShadowButton = 4.dp

    // === 弹性动效 ===
    const val SpringStiffness: Float = 380f
    const val SpringDamping: Float = 0.72f

    // === 背景 aurora 配色 ===
    fun auroraHues(dark: Boolean): FloatArray = if (dark) floatArrayOf(
        0.74f, 0.58f, 0.92f,
    ) else floatArrayOf(
        0.62f, 0.78f, 0.04f,
    )

    // === 原 Palette 字段 (小写) ===
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

    // === 语义色 (Liquid 玻璃上) ===
    @JvmName("brandOf")
    fun Brand(): Color = brand
    @JvmName("brandPressedOf")
    fun BrandPressed(): Color = brandPressed
    @JvmName("onBrandOf")
    fun OnBrand(): Color = textOnBrand
    fun Warning(): Color = warning
    fun Danger(): Color = danger
    fun Online() = online
    fun Offline(dark: Boolean) = offline(dark)
    fun TextPrimary(dark: Boolean) = textPrimary(dark)
    fun TextSecondary(dark: Boolean) = textSecondary(dark)
    fun IconNeutral(dark: Boolean) = iconNeutral(dark)
}



