package com.thisko.qringprint.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thisko.qringprint.ui.pages.PreviewStage
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.TextAlign
import com.thisko.qringprint.bluetooth.TextRenderOptions
import com.thisko.qringprint.bluetooth.WIDTH_DOTS
import com.thisko.qringprint.bluetooth.bitmapToGrayRaw
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.measureTextContentWidth
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.renderTextToPixelMap
import com.thisko.qringprint.model.HIST_TYPE_TEXT
import com.thisko.qringprint.model.HistoryBus
import com.thisko.qringprint.model.addHistoryGray
import com.thisko.qringprint.model.loadDefaultThickness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thisko.qringprint.ui.motion.pageEnter

private enum class TextMode(val label: String) {
    Normal("正常竖排"),
    Horizontal("横排文字"),
}

private fun renderBitmapForMode(
    text: String,
    options: TextRenderOptions,
    mode: TextMode,
): android.graphics.Bitmap = when (mode) {
    TextMode.Normal -> renderTextToPixelMap(text, options)
    TextMode.Horizontal -> renderHorizontalTextBitmap(text, options)
}

private fun renderHorizontalTextBitmap(
    text: String,
    options: TextRenderOptions,
): android.graphics.Bitmap {
    // 横排：先按当前配置绘制，再顺时针旋转 90°
    val source = renderTextToPixelMap(text, options)
    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
    val rotated = android.graphics.Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (rotated !== source) source.recycle()
    val banner = android.graphics.Bitmap.createBitmap(WIDTH_DOTS, rotated.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(banner)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(rotated, ((banner.width - rotated.width) / 2f).coerceAtLeast(0f), 0f, null)
    if (rotated !== banner) rotated.recycle()
    return trimBitmapWhitespace(banner)
}

private fun trimBitmapWhitespace(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var firstInk = -1
    var lastInk = -1
    for (y in 0 until bitmap.height) {
        val rowStart = y * bitmap.width
        val hasInk = (0 until bitmap.width).any { x -> (pixels[rowStart + x] and 0x00FFFFFF) < 0x00E0E0E0 }
        if (hasInk) {
            if (firstInk < 0) firstInk = y
            lastInk = y
        }
    }
    if (firstInk < 0) return bitmap
    val top = (firstInk - 8).coerceAtLeast(0)
    val bottom = (lastInk + 9).coerceAtMost(bitmap.height)
    if (top == 0 && bottom == bitmap.height) return bitmap
    val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
    bitmap.recycle()
    return cropped
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextPrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val status by PrinterConnection.status.collectAsStateWithLifecycle()
    var text by remember {
        val restored = HistoryBus.consumeText()?.let { payload ->
            runCatching { org.json.JSONObject(payload).optString("text") }.getOrNull()
        }
        mutableStateOf(restored?.takeIf { it.isNotEmpty() } ?: "Hello, 浣熊快印!\n欢迎使用 QringPrint")
    }
    var mode by remember { mutableStateOf(TextMode.Normal) }
    var align by remember { mutableStateOf(TextAlign.LEFT) }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    var fontFamily by remember { mutableStateOf("sans-serif") }
    var fontSize by remember { mutableIntStateOf(24) }
    var letterSpacing by remember { mutableFloatStateOf(0f) }
    var lineSpacing by remember { mutableFloatStateOf(6f) }
    var thickness by remember { mutableStateOf(loadDefaultThickness(context)) }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val options by remember(mode, fontSize, fontFamily, bold, italic, underline, letterSpacing, lineSpacing, align) {
        derivedStateOf {
            TextRenderOptions(
                fontFamily = fontFamily,
                fontSize = fontSize,
                bold = bold,
                italic = italic,
                underline = underline,
                letterSpacing = letterSpacing,
                lineSpacing = lineSpacing,
                align = align,
            )
        }
    }

    BackHandler(enabled = true, onBack = onBack)

    LaunchedEffect(text, options, mode) {
        preview = withContext(Dispatchers.IO) { renderBitmapForMode(text, options, mode) }
    }

    Column(modifier = Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("文字打印", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("打印模式", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextMode.values().forEach { entry ->
                            FilterChip(
                                selected = mode == entry,
                                onClick = { mode = entry },
                                label = { Text(entry.label) },
                            )
                        }
                    }
                    if (mode == TextMode.Horizontal) {
                        Text(
                            "横排文字：先按当前字体 / 字号绘制，再整体顺时针旋转 90°，纸宽方向即横排方向。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("对齐", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val leftOn: () -> Unit = { align = TextAlign.LEFT }
                    val centerOn: () -> Unit = { align = TextAlign.CENTER }
                    val rightOn: () -> Unit = { align = TextAlign.RIGHT }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = align == TextAlign.LEFT,
                            onClick = leftOn,
                            shape = SegmentedButtonDefaults.itemShape(0, 3),
                            label = { Text("左对齐") },
                        )
                        SegmentedButton(
                            selected = align == TextAlign.CENTER,
                            onClick = centerOn,
                            shape = SegmentedButtonDefaults.itemShape(1, 3),
                            label = { Text("居中") },
                        )
                        SegmentedButton(
                            selected = align == TextAlign.RIGHT,
                            onClick = rightOn,
                            shape = SegmentedButtonDefaults.itemShape(2, 3),
                            label = { Text("右对齐") },
                        )
                    }
                }
            }

            PreviewStage(
                bitmap = preview,
                modifier = Modifier.fillMaxWidth(),
                filePrefix = "text_print",
            )

            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("字体", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val families = listOf(
                        "sans-serif" to "无衬线",
                        "serif" to "衬线",
                        "monospace" to "等宽",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        families.forEach { (key, label) ->
                            FilterChip(selected = fontFamily == key, onClick = { fontFamily = key }, label = { Text(label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text(if (bold) "✓ 加粗" else "加粗") })
                        FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text(if (italic) "✓ 斜体" else "斜体") })
                        FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text(if (underline) "✓ 下划线" else "下划线") })
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("字号：$fontSize sp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { fontSize = it.toInt().coerceIn(12, 48) },
                        valueRange = 12f..48f,
                    )
                    Text("字间宽度：${letterSpacing.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = letterSpacing,
                        onValueChange = { letterSpacing = it },
                        valueRange = 0f..8f,
                    )
                    Text("行间宽度：${lineSpacing.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = lineSpacing,
                        onValueChange = { lineSpacing = it },
                        valueRange = 0f..16f,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("打印浓度：$thickness", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { level ->
                            FilterChip(thickness == level, { thickness = level }, { Text("浓度$level") })
                        }
                    }
                }
            }

            message?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(it, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val w = measureTextContentWidth(text, options, WIDTH_DOTS)
                            message = "排版宽度: $w 点"
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("测宽度") }
                Button(
                    onClick = {
                        scope.launch {
                            val fault = PrinterConnection.preflightCheck()
                            if (fault != null) { message = fault; return@launch }
                            printing = true
                            try {
                                val bitmap = withContext(Dispatchers.IO) { renderBitmapForMode(text, options, mode) }
                                val gray = bitmapToGrayRaw(bitmap)
                                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                                val raster = packBinaryToRaster(binary, gray.width, gray.height)
                                PrintQueue.enqueue(PrintJob(title = "文字 ${mode.label}", raster = raster))
                                addHistoryGray(
                                    context,
                                    HIST_TYPE_TEXT,
                                    gray,
                                    "{\"text\":\"${text.take(200).replace("\"", "\\\"")}\",\"mode\":\"${mode.name}\"}",
                                )
                                message = "已提交到打印队列"
                            } catch (e: Throwable) {
                                message = "提交失败：${e.message}"
                            } finally {
                                printing = false
                            }
                        }
                    },
                    enabled = !printing,
                    modifier = Modifier.weight(2f),
                ) {
                    if (printing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Print, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("打印")
                    }
                }
            }
        }
    }
}

