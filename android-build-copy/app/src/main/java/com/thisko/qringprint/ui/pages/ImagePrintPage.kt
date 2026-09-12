package com.thisko.qringprint.ui.pages

import android.util.Log
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.IMAGE_PRESETS
import com.thisko.qringprint.bluetooth.ImagePreset
import com.thisko.qringprint.bluetooth.ImageProcessOptions
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.decodeImageToPrintWidth
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.processGray
import com.thisko.qringprint.ui.pages.PreviewStage
import com.thisko.qringprint.model.HIST_TYPE_IMAGE
import com.thisko.qringprint.model.HistoryBus
import com.thisko.qringprint.model.loadDefaultThickness
import com.thisko.qringprint.model.addHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thisko.qringprint.ui.motion.pageEnter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by PrinterConnection.status.collectAsStateWithLifecycle()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var processed by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var rotation by remember { mutableStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }

    // 选中的预设 key("custom" 表示用户在高级区手动调过,脱离预设)
    var presetKey by remember { mutableStateOf(IMAGE_PRESETS.first().key) }
    // 完整图像处理选项
    var options by remember { mutableStateOf(IMAGE_PRESETS.first().options) }
    // 高级区是否展开
    var advanced by remember { mutableStateOf(false) }
    var thickness by remember { mutableStateOf(loadDefaultThickness(context)) }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val historyUri = remember {
        HistoryBus.consumeImage()?.let { payload ->
            runCatching { org.json.JSONObject(payload).optString("uri") }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    // 拦截系统返回键 —— 用户按返回不退出 app
    BackHandler(enabled = true, onBack = onBack)

    // 选预设 -> 覆盖 options
    fun applyPreset(preset: ImagePreset) {
        presetKey = preset.key
        options = preset.options
    }

    // 切到自定义(用户在高级区手动调过任意滑块都切到 custom)
    fun markCustom() {
        if (presetKey != "custom") presetKey = "custom"
    }

    // 实时预览 —— 源图/任一选项变了就重做增强 + 抖动
    LaunchedEffect(source, options) {
        val src = source
        if (src == null) {
            processed = null
            return@LaunchedEffect
        }
        var p = withContext(Dispatchers.IO) {
            val raw = bitmapToGray(src)
            val enhanced = processGray(raw, options)
            val binary = ditherToBinary(enhanced, options.ditherMode, options.threshold)
            binaryToPreviewBitmap(binary, enhanced.width, enhanced.height)
        }
        if (rotation != 0 || flipH || flipV) {
            val m = android.graphics.Matrix()
            if (rotation != 0) m.postRotate(rotation.toFloat())
            if (flipH) m.postScale(-1f, 1f)
            if (flipV) m.postScale(1f, -1f)
            p = android.graphics.Bitmap.createBitmap(p, 0, 0, p.width, p.height, m, true)
        }
        processed = p
    }
    LaunchedEffect(historyUri) {
        if (source == null && historyUri != null) {
            val uri = Uri.parse(historyUri)
            sourceUri = uri
            source = withContext(Dispatchers.IO) {
                runCatching { decodeImageToPrintWidth(context.contentResolver, uri) }.getOrNull()
            }
            if (source == null) message = "原图片已不可访问，请重新选择"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sourceUri = uri
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeImageToPrintWidth(context.contentResolver, uri) }
            source = bmp
        }
    }

    Column(modifier = Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("\u56fe\u7247\u6253\u5370", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
                }
            },
            actions = {
                IconButton(onClick = { advanced = !advanced }) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "\u9ad8\u7ea7",
                        tint = if (advanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // 预览
            Surface(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                color = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    val bmp = processed ?: source
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "\u6253\u5370\u9884\u89c8",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("\u9009\u62e9\u4e00\u5f20\u56fe\u7247", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 选择图片
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (sourceUri == null) "\u9009\u62e9\u56fe\u7247" else "\u66f4\u6362\u56fe\u7247")
            }
            Spacer(Modifier.height(16.dp))

            // 预设模板行(横滚)
            Text("\u9884\u8bbe\u6a21\u677f", fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(IMAGE_PRESETS.size) { idx ->
                    val p = IMAGE_PRESETS[idx]
                    FilterChip(
                        selected = presetKey == p.key,
                        onClick = { applyPreset(p) },
                        label = { Text(p.label, fontSize = 12.sp) },
                    )
                }
                // 自定义(高级区被改过后,这个高亮)
                item {
                    FilterChip(
                        selected = presetKey == "custom",
                        onClick = { /* 仅标记,真实调参在高级区 */ },
                        label = { Text("\u81ea\u5b9a\u4e49", fontSize = 12.sp) },
                        enabled = false,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val activePreset = IMAGE_PRESETS.firstOrNull { it.key == presetKey }
            Text(
                activePreset?.hint ?: "\u9ad8\u7ea7\u533a\u4e2d\u624b\u52a8\u8c03\u8282\u4eae\u5ea6/\u5bf9\u6bd4\u5ea6/\u4f50\u739b\u7b49",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // 高级调节(可折叠)
            if (advanced) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 亮度
                        Text("\u4eae\u5ea6 ${options.brightness}", fontSize = 12.sp)
                        Slider(
                            value = options.brightness.toFloat(),
                            onValueChange = {
                                options = options.copy(brightness = it.toInt())
                                markCustom()
                            },
                            valueRange = -100f..100f,
                            steps = 39,
                        )
                        // 对比度
                        Text(
                            "\u5bf9\u6bd4\u5ea6 ${"%.2f".format(options.contrast)}",
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = options.contrast,
                            onValueChange = {
                                options = options.copy(contrast = it)
                                markCustom()
                            },
                            valueRange = 0.5f..2.0f,
                        )
                        // 伽马
                        Text(
                            "\u4f50\u739b ${"%.2f".format(options.gamma)}",
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = options.gamma,
                            onValueChange = {
                                options = options.copy(gamma = it)
                                markCustom()
                            },
                            valueRange = 0.5f..2.0f,
                        )
                        // 突加(unsharp mask)—— 提升高频细节,让二值化后边缘明显剑
                        Text(
                            "突加 ${"%.2f".format(options.sharpen)}",
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = options.sharpen,
                            onValueChange = {
                                options = options.copy(sharpen = it)
                                markCustom()
                            },
                            valueRange = 0f..2f,
                        )
                        // 阈值(仅 NONE 生效,但 UI 一直显示让用户看到当前值)
                        Text(
                            "\u786c\u9608\u503c ${options.threshold} ${if (options.ditherMode != DitherMode.NONE) "\u00b7 \u4ec5\u786c\u9608\u503c\u6a21\u5f0f\u751f\u6548" else ""}",
                            fontSize = 12.sp,
                        )
                        Slider(
                            value = options.threshold.toFloat(),
                            onValueChange = {
                                options = options.copy(threshold = it.toInt())
                                markCustom()
                            },
                            valueRange = 1f..254f,
                        )
                        // 抖动算法(紧凑行)
                        Spacer(Modifier.height(4.dp))
                        Text("\u6296\u52a8\u7b97\u6cd5", fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DitherMode.values().forEach { m ->
                                FilterChip(
                                    selected = options.ditherMode == m,
                                    onClick = {
                                        options = options.copy(ditherMode = m)
                                        markCustom()
                                    },
                                    label = { Text(when (m) {
                                        DitherMode.NONE -> "\u65e0"
                                        DitherMode.FLOYD_STEINBERG -> "Floyd"
                                        DitherMode.ATKINSON -> "Atkinson"
                                    }, fontSize = 11.sp) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // 自动对比度 / 反色
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = options.autoContrast,
                                onClick = {
                                    options = options.copy(autoContrast = !options.autoContrast)
                                    markCustom()
                                },
                                label = { Text("\u81ea\u52a8\u5bf9\u6bd4\u5ea6", fontSize = 12.sp) },
                            )
                            FilterChip(
                                selected = options.invert,
                                onClick = {
                                    options = options.copy(invert = !options.invert)
                                    markCustom()
                                },
                                label = { Text("\u53cd\u8272", fontSize = 12.sp) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 浓度(热敏头加热时长)
            Text("\u6d53\u5ea6 $thickness", fontSize = 13.sp)
            Slider(
                value = thickness.toFloat(),
                onValueChange = { thickness = it.toInt() },
                valueRange = 1f..3f,
                steps = 1,
            )
        }

        message?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) { Text(it, modifier = Modifier.padding(12.dp), fontSize = 13.sp) }
            Spacer(Modifier.height(8.dp))
        }

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Button(
                onClick = {
                    scope.launch {
                        val fault = PrinterConnection.preflightCheck()
                        if (fault != null) { message = fault; return@launch }
                        val src = source ?: run { message = "\u8bf7\u5148\u9009\u62e9\u56fe\u7247"; return@launch }
                        printing = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val raw = bitmapToGray(src)
                                val enhanced = processGray(raw, options)
                                val binary = ditherToBinary(enhanced, options.ditherMode, options.threshold)
                                val raster = packBinaryToRaster(binary, enhanced.width, enhanced.height)
                                PrinterConnection.printRaster(raster, thickness)
                            }
                            message = result.message
                            if (result.ok) {
                                val historyPayload = org.json.JSONObject()
                                    .put("uri", sourceUri?.toString().orEmpty())
                                    .put("dither", options.ditherMode.value)
                                    .put("b", options.brightness)
                                    .put("c", options.contrast)
                                addHistory(context, HIST_TYPE_IMAGE, src, historyPayload.toString())
                            }
                        } finally { printing = false }
                    }
                },
                enabled = !printing && source != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (printing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("\u6253\u5370")
                }
            }
        }
    }
}

// items helper for LazyRow used in presets
private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, block: @Composable (Int) -> Unit) {
    repeat(count) { idx -> item { block(idx) } }
}
