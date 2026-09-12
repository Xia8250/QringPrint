package com.thisko.qringprint.ui.pages

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.TextRenderOptions
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.bitmapToGrayRaw
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.ocr.OcrEngineSelector
import com.thisko.qringprint.bluetooth.ocr.OcrImagePreprocessor
import com.thisko.qringprint.bluetooth.ocr.ImageUriLoader
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.renderTextToPixelMap
import com.thisko.qringprint.model.HIST_TYPE_TEXT
import com.thisko.qringprint.model.addHistoryGray
import com.thisko.qringprint.model.loadDefaultThickness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.ByteArrayOutputStream
import com.thisko.qringprint.ui.motion.pageEnter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val status by PrinterConnection.status.collectAsStateWithLifecycle()
    val engineSelector = remember { OcrEngineSelector(context) }
    val ocrClient = engineSelector.baidu  // 兼容旧字段，仍指向百度客户端

    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var printing by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(20f) }
    var thickness by remember { mutableStateOf(loadDefaultThickness(context)) }
    var showConfig by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiKey = ocrClient.apiKey ?: ""
        secretKey = ocrClient.secretKey ?: ""
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            message = "已取消选择"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            recognizing = true
            message = "正在处理图片..."
            runCatching {
                val raw = withContext(Dispatchers.IO) { ImageUriLoader.load(context, uri) }
                var processed = withContext(Dispatchers.Default) { OcrImagePreprocessor.preprocess(raw) }
                // 防御：极少数 ROM/驱动下预处理可能返回共享 buffer 的视图，
                // 此时 raw.recycle() 会让 processed 后续 getPixels 报 'recycled bitmap'。
                if (processed.isRecycled) {
                    Log.w("OcrPrintPage", "processed bitmap was recycled, re-copy from raw")
                    processed = if (!raw.isRecycled) raw.copy(Bitmap.Config.ARGB_8888, false) else raw
                }
                preview = processed
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { bitmapToJpegBytes(processed, maxBytes = 3 * 1024 * 1024) }.getOrElse { e ->
                        // 极端兜底：processed 可能仍和 raw 共享 buffer，回收 raw 后压缩失败。
                        // 此时再造一份独立 copy 再试。
                        Log.w("OcrPrintPage", "jpeg compress failed, retry with fresh copy: " + e.message)
                        val fresh = processed.copy(Bitmap.Config.ARGB_8888, false)
                        val out = bitmapToJpegBytes(fresh, maxBytes = 3 * 1024 * 1024)
                        if (fresh !== processed) fresh.recycle()
                        out
                    }
                }
                Log.d("OcrPrintPage", "OCR image from gallery: " + processed.width + "x" + processed.height + " size=" + bytes.size)
                // 压缩成功后再回收 raw，确保 processed 不会因共享 buffer 被一起释放
                if (!raw.isRecycled && processed !== raw) raw.recycle()
                val result = engineSelector.pick().recognize(bytes)
                if (!result.error_msg.isNullOrBlank()) {
                    message = "识别失败：\n" + result.error_msg
                } else {
                    text = result.text
                    message = if (text.isNotEmpty()) "识别成功，共 " + text.length + " 个字符" else "未识别到文字"
                }
            }.onFailure { e ->
                message = "识别失败：" + e.message
            }
            recognizing = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            message = "已取消拍照"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            recognizing = true
            message = "正在处理图片..."
            runCatching {
                val processed = withContext(Dispatchers.Default) { OcrImagePreprocessor.preprocess(bitmap) }
                // TakePicturePreview 返回的 bitmap 由系统持有，不能 recycle
                preview = processed
                val bytes = withContext(Dispatchers.IO) { bitmapToJpegBytes(processed, maxBytes = 3 * 1024 * 1024) }
                Log.d("OcrPrintPage", "OCR image: " + processed.width + "x" + processed.height + " size=" + bytes.size)
                val result = engineSelector.pick().recognize(bytes)
                if (!result.error_msg.isNullOrBlank()) {
                    message = "识别失败：\n" + result.error_msg
                } else {
                    text = result.text
                    message = if (text.isNotEmpty()) "识别成功，共 " + text.length + " 个字符" else "未识别到文字"
                }
            }.onFailure { e ->
                message = "识别失败：" + e.message
            }
            recognizing = false
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                                cameraLauncher.launch(null)
            } catch (e: Exception) {
                Log.e("OcrPrintPage", "Failed to launch camera", e)
                message = "启动相机失败：" + e.message
            }
        } else {
            message = "未授予相机权限"
        }
    }

    val options = TextRenderOptions(fontSize = fontSize.toInt(), lineSpacing = 4f, margin = 10)
    val canPrint = text.isNotBlank()

    LaunchedEffect(text, fontSize, thickness) {
        if (!canPrint) {
            preview = null
            return@LaunchedEffect
        }
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val source = renderTextToPixelMap(text, options)
                val gray = bitmapToGray(source)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                binaryToPreviewBitmap(binary, gray.width, gray.height)
            }.getOrNull()
        }
    }

    BackHandler(enabled = true, onBack = onBack)
    Column(Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("拍照转文字打印", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { showConfig = true }) {
                    Icon(Icons.Filled.Settings, "配置")
                }
            }
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ConnectionBanner()
            Spacer(Modifier.height(16.dp))

            if (!ocrClient.isConfigured()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "请先配置百度 OCR API Key",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.CameraAlt, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (recognizing) "识别中…" else "拍照（先拍到相册）", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Image, null)
                    Spacer(Modifier.width(4.dp))
                    Text("从相册选（推荐）", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
Spacer(Modifier.width(8.dp))
                Text("高准确度模式（accurate_basic，识别慢但更准）", fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))

            Text("识别结果（可编辑）", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
            )

            Spacer(Modifier.height(12.dp))
            Text("字号：" + fontSize.toInt() + "sp")
            Slider(fontSize, { fontSize = it }, valueRange = 14f..28f)
            Text("打印浓度：" + thickness)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { level ->
                    FilterChip(thickness == level, { thickness = level }, { Text("浓度" + level) })
                }
            }

            Spacer(Modifier.height(16.dp))
            PreviewCard(preview, Modifier.fillMaxWidth().heightIn(min = 200.dp))
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    PrinterConnection.preflightCheck()?.let { fault -> message = fault; return@launch }
                    printing = true
                    try {
                        val bitmap = withContext(Dispatchers.IO) { renderTextToPixelMap(text, options) }
                        val gray = bitmapToGrayRaw(bitmap)
                        val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                        val raster = packBinaryToRaster(binary, gray.width, gray.height)
                        PrintQueue.enqueue(PrintJob(title = "OCR 文字", raster = raster))
                        addHistoryGray(context, HIST_TYPE_TEXT, gray, "{\"kind\":\"OCR\"}")
                        message = "已提交到打印队列"
                    } catch (e: Throwable) {
                        message = "提交失败：" + e.message
                    } finally { printing = false }
                }
            },
            enabled = !printing && canPrint,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            if (printing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else { Icon(Icons.Filled.Print, null); Spacer(Modifier.width(8.dp)); Text("打印") }
        }
    }
    if (showConfig) {
        AlertDialog(
            onDismissRequest = { showConfig = false },
            title = { Text("OCR 引擎配置") },
            text = {
                Column {
                    Text("识别引擎", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("当前使用：百度云 OCR", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("百度云 OCR 配置", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("请访问 https://console.bce.baidu.com/ai 创建应用获取", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        apiKey, { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        secretKey, { secretKey = it },
                        label = { Text("Secret Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saved) {
                        Spacer(Modifier.height(8.dp))
                        Text("已保存配置", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ocrClient.saveCredentials(apiKey, secretKey)
                    saved = true
                    message = "已保存百度云 OCR 配置"
                }) { Text("保存") }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        scope.launch {
                            ocrClient.saveCredentials(apiKey, secretKey)
                            val baiduResult = if (ocrClient.isConfigured()) ocrClient.testConnection() else "百度未配置"
                            message = baiduResult
                        }
                    }) { Text("测试连接") }
                    TextButton(onClick = { showConfig = false }) { Text("取消") }
                }
            }
        )
    }
}


/**
 * Bitmap 转 JPEG 字节，自适应压缩到不超过 maxBytes。
 */
private fun bitmapToJpegBytes(bitmap: Bitmap, maxBytes: Int): ByteArray {
    var quality = 85
    var bytes: ByteArray
    do {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bytes = out.toByteArray()
        if (bytes.size <= maxBytes || quality <= 40) break
        quality -= 10
    } while (true)
    return bytes
}

