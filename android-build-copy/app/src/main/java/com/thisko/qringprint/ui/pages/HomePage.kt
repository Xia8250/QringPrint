package com.thisko.qringprint.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.R
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.ui.components.DevicePickerSheet
import com.thisko.qringprint.ui.components.PrinterStatusCard
import com.thisko.qringprint.ui.components.QuickActionCard
import com.thisko.qringprint.ui.theme.Metrics
import com.thisko.qringprint.ui.theme.Palette
import com.thisko.qringprint.ui.theme.Typo

private data class QuickAction(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tileColor: Color,
)

@Composable
fun HomePage(
    modifier: Modifier = Modifier,
    onPrint: (String) -> Unit,
    onCustom: () -> Unit,
    onBatch: () -> Unit = {},
    onDiagnostic: () -> Unit = {},
) {
    val status by PrinterConnection.status.collectAsState()
    var showDevicePicker by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }

    val actions = listOf(
        QuickAction("image", "图片打印", "图片打印说明", Icons.Filled.Image, Palette.tileAmber),
        QuickAction("text", "文字打印", "文字打印说明", Icons.Filled.Description, Palette.tileMint),
        QuickAction("adbanner", "广告横带", "粗体 · 90°旋转", Icons.Filled.ViewStream, Palette.tileAmber),
        QuickAction("code", "条码打印", "条码打印说明", Icons.Filled.QrCode2, Palette.tileBlue),
        QuickAction("custom", "自定义打印", "自定义打印说明", Icons.Filled.Tune, Palette.tileLilac),
        QuickAction("table", "表格打印", "表格打印说明", Icons.Filled.TableView, Palette.tileMint),
        QuickAction("receipt", "小票打印", "小票打印说明", Icons.Filled.Receipt, Palette.tileAmber),
        QuickAction("time", "时间打印", "时间打印说明", Icons.Filled.Schedule, Palette.tileBlue),
        QuickAction("schedule", "课程表打印", "课程表打印说明", Icons.Filled.CalendarMonth, Palette.tileLilac),
        QuickAction("label", "标签打印", "标签打印说明", Icons.AutoMirrored.Filled.Label, Palette.tileAmber),
        QuickAction("ocr", "拍照转文字", "拍照转文字说明", Icons.Filled.CameraAlt, Palette.tileBlue),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn() + slideInVertically { -it / 5 },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Metrics.pagePadding.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(Metrics.brandBadgeSize.dp)
                        .clip(RoundedCornerShape(Metrics.brandBadgeRadius.dp)),
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "浣熊快印",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = Typo.appTitle.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "蓝牙打印",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = Typo.appSubtitle.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn() + slideInVertically { it / 6 },
        ) {
            PrinterStatusCard(
                status = status,
                onClick = { showDevicePicker = true },
            )
        }

        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn() + slideInVertically { it / 5 },
        ) {
            Column {
                Text(
                    text = "快速打印",
                    modifier = Modifier.padding(horizontal = Metrics.pagePadding.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = Typo.sectionTitle.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.padding(horizontal = Metrics.pagePadding.dp)) {
                    actions.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEachIndexed { i, action ->
                                QuickActionCard(
                                    title = action.title,
                                    subtitle = action.subtitle,
                                    icon = action.icon,
                                    tileColor = action.tileColor,
                                    onClick = {
                                        when (action.key) {
                                            "custom" -> onCustom()
                                            "ocr" -> onPrint("ocr")
                                            else -> onPrint(action.key)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (i == 0 && rowItems.size > 1) Spacer(Modifier.width(Metrics.gridGap.dp))
                            }
                        }
                        Spacer(Modifier.height(Metrics.gridGap.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "工具",
                    modifier = Modifier.padding(horizontal = Metrics.pagePadding.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = Typo.sectionTitle.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = Metrics.pagePadding.dp),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.gridGap.dp),
                ) {
                    QuickActionCard(
                        title = "批量打印",
                        subtitle = "批量打印说明",
                        icon = Icons.Filled.ViewList,
                        tileColor = Palette.tileBlue,
                        onClick = onBatch,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionCard(
                        title = "诊断",
                        subtitle = "诊断说明",
                        icon = Icons.Filled.Build,
                        tileColor = Palette.tileAmber,
                        onClick = onDiagnostic,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDevicePicker) {
        DevicePickerSheet(onDismiss = { showDevicePicker = false })
    }
}
