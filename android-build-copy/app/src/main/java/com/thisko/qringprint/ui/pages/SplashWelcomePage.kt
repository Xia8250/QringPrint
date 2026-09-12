package com.thisko.qringprint.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 苹果式"欢迎"启动页:
 *  - 浅色: 白底 + 浅色 aurora
 *  - 深色: 黑底 + 极光紫蓝
 *  中央显示品牌名"浣熊快印"逐字缩放进入, 下方"欢迎使用浣熊快印"淡入,
 *  整个动效与系统启动间隔重叠, 让等待变成动画.
 */
@Composable
fun SplashWelcomePage(onFinished: () -> Unit) {
    val chars = listOf("浣", "熊", "快", "印")
    val anims = remember { chars.map { Animatable(0f) } }
    val subtitle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 启动入场
    LaunchedEffect(Unit) {
        // 标题逐字弹入: 苹果"Hello"那种节奏
        chars.forEachIndexed { i, _ ->
            scope.launch {
                anims[i].animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 520, delayMillis = i * 90, easing = FastOutSlowInEasing),
                )
            }
        }
        // 副标题淡入
        delay(420L)
        subtitle.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        // 保持显示一会儿, 然后让出
        delay(900L)
        onFinished()
    }

    val bg = if (isAppDarkForSplash()) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF1E1B2E),
                Color(0xFF120F1C),
                Color(0xFF08070D),
                Color(0xFF000000),
            ),
            center = Offset(540f, 1100f),
            radius = 1600f,
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFFF7F3FF),
                Color(0xFFEFE9FF),
                Color(0xFFFFFFFF),
                Color(0xFFFFFFFF),
            ),
            center = Offset(540f, 1100f),
            radius = 1600f,
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 浣熊快印 - 逐字
            Row(verticalAlignment = Alignment.CenterVertically) {
                chars.forEachIndexed { i, ch ->
                    val a = anims[i]
                    Text(
                        text = ch,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAppDarkForSplash()) Color(0xFFF5F5F7) else Color(0xFF1D1D1F),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .scale(0.6f + 0.4f * a.value)
                            .alpha(a.value),
                        style = TextStyle.Default,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .size(width = 0.dp, height = 0.dp)
            )
            Text(
                text = "欢迎使用浣熊快印",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = if (isAppDarkForSplash()) Color(0xCCEFEFF4) else Color(0xFF6E6E73),
                modifier = Modifier
                    .alpha(subtitle.value)
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun isAppDarkForSplash(): Boolean {
    return try {
        com.thisko.qringprint.model.ThemeState.darkOverride.value ?: false
    } catch (_: Throwable) { false }
}