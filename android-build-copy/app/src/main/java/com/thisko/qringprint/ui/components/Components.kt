package com.thisko.qringprint.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.model.ConnState
import com.thisko.qringprint.model.HardwareState
import com.thisko.qringprint.model.PaperState
import com.thisko.qringprint.model.PrinterStatus
import com.thisko.qringprint.model.batteryLabel
import com.thisko.qringprint.model.connLabel
import com.thisko.qringprint.model.hardwareLabel
import com.thisko.qringprint.model.isHardwareUnknown
import com.thisko.qringprint.model.isPaperUnknown
import com.thisko.qringprint.model.paperLabel
import com.thisko.qringprint.ui.theme.Metrics
import com.thisko.qringprint.ui.theme.Palette
import com.thisko.qringprint.ui.theme.Typo
import com.thisko.qringprint.ui.glass.GlassTokens
import com.thisko.qringprint.ui.glass.glassLayer

/** 打印机状态卡 —— 渐变背景 + 四个小指标 + 设备名 + 状态徽标 */
@Composable
fun PrinterStatusCard(
    status: PrinterStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = status.connState == ConnState.CONNECTED
    val shape = RoundedCornerShape(Metrics.statusCardRadius.dp)
    val brush = if (connected) {
        Brush.linearGradient(
            listOf(
                Palette.cardGradStart.copy(alpha = 0.78f),
                Palette.cardGradMid.copy(alpha = 0.64f),
                Palette.cardGradEnd.copy(alpha = 0.72f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.32f),
                Color(0xFFD9C8FF).copy(alpha = 0.20f),
                Color(0xFFBFE7FF).copy(alpha = 0.14f),
            ),
        )
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(Metrics.statusCardHeight.dp)
            .padding(horizontal = Metrics.pagePadding.dp)
            .shadow(18.dp, shape, clip = false),
        shape = shape,
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .glassLayer(
                        blurRadius = GlassTokens.BlurPanel,
                        cornerRadius = Metrics.statusCardRadius.dp,
                        tint = if (connected) Palette.cardGradMid else Color.White,
                        specular = 0.86f,
                        dispersion = 0.016f,
                        sss = 0.68f,
                    )
                    .clip(shape)
                    .background(brush)
                    .border(1.dp, Color.White.copy(alpha = 0.52f), shape),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Metrics.statusCardPadding.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status.deviceName.ifEmpty { "未连接打印机" },
                        color = if (connected) Palette.textOnBrand else Color(0xFF20212A),
                        fontSize = Typo.deviceName.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(text = connLabel(status.connState), connected = connected)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusMetric(
                        icon = Icons.Filled.Bolt, label = "电量",
                        value = batteryLabel(status.batteryPercent), connected = connected
                    )
                    StatusMetric(
                        icon = Icons.Filled.Inbox, label = "纸仓",
                        value = paperLabel(status.paperState),
                        dim = isPaperUnknown(status.paperState), connected = connected
                    )
                    StatusMetric(
                        icon = Icons.Filled.Thermostat, label = "机器",
                        value = hardwareLabel(status.hardwareState),
                        dim = isHardwareUnknown(status.hardwareState), connected = connected
                    )
                    StatusMetric(
                        icon = Icons.Filled.Notifications, label = "提示",
                        value = status.lastError.ifEmpty { "无异常" },
                        dim = status.lastError.isEmpty(), connected = connected
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, connected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Metrics.statusPillRadius.dp))
            .background(if (connected) Color(0x33000000) else Color.White.copy(alpha = 0.42f))
            .border(0.6.dp, Color.White.copy(alpha = 0.46f), RoundedCornerShape(Metrics.statusPillRadius.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Metrics.statusDotSize.dp)
                    .clip(CircleShape)
                    .background(if (connected) Palette.online else Color(0xFF9298A5))
            )
            Spacer(Modifier.width(6.dp))
            Text(text, color = if (connected) Palette.textOnBrand else Color(0xFF30313A), fontSize = Typo.pill.sp)
        }
    }
}

@Composable
private fun StatusMetric(
    icon: ImageVector,
    label: String,
    value: String,
    dim: Boolean = false,
    connected: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (connected) Palette.onCardTile else Color.White.copy(alpha = 0.38f))
                .border(0.6.dp, Color.White.copy(alpha = 0.44f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (connected) Palette.textOnBrand else Color(0xFF555B68),
                modifier = Modifier.size(18.dp)
            )
        }
        // 值先出(数字感更强),标签垫底
        Text(
            value,
            color = if (connected) {
                if (dim) Palette.onCardMuted else Palette.onCardSubtitle
            } else {
                if (dim) Color(0xFF7F8490) else Color(0xFF343741)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            label,
            color = if (connected) Palette.onCardMuted else Color(0xFF737985),
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

/** 快捷打印方块,放在首页 */
@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tileColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Metrics.actionCardRadius.dp)
    Surface(
        modifier = modifier
            .height(Metrics.actionCardHeight.dp)
            .shadow(12.dp, shape, clip = false),
        shape = shape,
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .glassLayer(
                        blurRadius = GlassTokens.BlurCard,
                        cornerRadius = Metrics.actionCardRadius.dp,
                        tint = Color.White,
                        specular = 0.82f,
                        dispersion = 0.014f,
                        sss = 0.48f,
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.32f),
                                tileColor.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.12f),
                            ),
                        ),
                    )
                    .border(0.8.dp, Color.White.copy(alpha = 0.58f), shape),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(Metrics.tileSize.dp)
                        .clip(RoundedCornerShape(Metrics.tileRadius.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    tileColor.copy(alpha = 0.96f),
                                    tileColor.copy(alpha = 0.62f),
                                ),
                            ),
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.68f), RoundedCornerShape(Metrics.tileRadius.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Palette.tileIcon,
                        modifier = Modifier.size(Metrics.tileIcon.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontSize = Typo.actionTitle.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = Typo.actionSubtitle.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

