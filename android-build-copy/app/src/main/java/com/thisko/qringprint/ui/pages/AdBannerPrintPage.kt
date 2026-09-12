package com.thisko.qringprint.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thisko.qringprint.bluetooth.AD_BANNER_MAX_LENGTH
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.WIDTH_DOTS
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.renderAdBannerToPixelMap
import com.thisko.qringprint.model.HIST_TYPE_AD_BANNER
import com.thisko.qringprint.model.HistoryBus
import com.thisko.qringprint.model.addHistory
import com.thisko.qringprint.ui.motion.pageEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「广告横带」打印页。
 *
 * 锁定的固定模板模式:
 *   - 字体:SANS_SERIF Bold,锁死,不可改
 *   - 字号:根据「文字总长度 + 纸宽 + 横带最大长度」自动最大化,用户不能调
 *   - 排版:整条文字作为单行整体渲染,不能拆行 / 拆列 / 拆排
 *   - 方向:整体顺时针旋转 90°
 *
 * 用户**唯一**能改的就是「内容」文本框;下面的字体 / 字号 / 加粗 / 对齐
 * 全部以「锁定展示」状态显示,只为了让用户理解「这是固定模板,不是普通文字模式」,
 * 改也不会真正影响渲染结果。
 *
 * 预览和打印走 renderAdBannerToPixelMap 同一份逻辑,所见即所打。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBannerPrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val status by PrinterConnection.status.collectAsStateWithLifecycle()

    // ── 内容（唯一可改的输入） ────────────────────────────────
    var text by remember {
        val restored = HistoryBus.consumeAdBanner()?.let { payload ->
            runCatching { org.json.JSONObject(payload).optString("text") }.getOrNull()
        }
        mutableStateOf(restored?.takeIf { it.isNotEmpty() } ?: "Hello, 浣熊快印\n欢迎使用 QringPrint")
    }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // 预览显示的必须是「打印机会实际吐出的 1-bit 二值图」,而不是抗锯齿原图。
    // 缓存对应光栅,点击「打印」时直接复用,保证预览 = 打印。
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previewRaster by remember { mutableStateOf<com.thisko.qringprint.bluetooth.RasterData?>(null) }
    var previewDotsW by remember { mutableStateOf(0) }
    var previewDotsH by remember { mutableStateOf(0) }

    BackHandler(enabled = true, onBack = onBack)

    // 输入变化时即时刷新预览,让用户看到「这条文字旋转 + 硬阈值后是什么样子」。
    // 预览和打印共用同一份 binary → raster,所见即所打。
    LaunchedEffect(text) {
        val pair = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = renderAdBannerToPixelMap(text)
                val gray = bitmapToGray(bitmap)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                val raster = packBinaryToRaster(binary, gray.width, gray.height)
                val previewBmp = binaryToPreviewBitmap(binary, gray.width, gray.height)
                Triple(previewBmp, raster, gray.width to gray.height)
            }.getOrNull()
        }
        if (pair != null) {
            val (previewBmp, raster, size) = pair
            preview?.recycle()
            preview = previewBmp
            previewRaster = raster
            previewDotsW = size.first
            previewDotsH = size.second
        } else {
            preview = null
            previewRaster = null
            previewDotsW = 0
            previewDotsH = 0
        }
    }

    val connected = status.connState == com.thisko.qringprint.model.ConnState.CONNECTED
    val flat = flattenAdBannerPreview(text)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pageEnter()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("广告横带", fontWeight = FontWeight.SemiBold) },
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
            // 连接状态条
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).background(
                            if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (connected) "已连接 ${status.deviceName}" else "未连接打印机 —— 请回首页点状态卡选择设备",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 内容输入 —— 唯一允许编辑的控件
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("广告内容") },
                placeholder = { Text("输入广告内容,换行会被合并成同一行") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                supportingText = {
                    Text(
                        "横带最大长度 ${AD_BANNER_MAX_LENGTH} 点 ≈ ${AD_BANNER_MAX_LENGTH / 8} mm · 文字过长时字号会自动缩小以保证单行不溢出",
                        fontSize = 11.sp,
                    )
                },
            )

            // 固定模板提示
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "固定模板 · 排版参数已锁定",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LockedField("字体", "系统默认(无衬线)")
                    LockedField("字重", "粗体 Bold(强制)")
                    LockedField("字号", "自动最大化")
                    LockedField("排版", "单行 + 顺时针 90°")
                    LockedField("对齐", "整条整体(不可拆分)")
                }
            }

            // 预览
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("预览", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val bmp = preview
                    if (bmp != null) {
                        Text(
                            "纸宽 $WIDTH_DOTS 点 · 横带 ${bmp.width} × ${bmp.height} 点",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // ── 缩放状态 ─────────────────────────────────────
                        // 每次 bmp 换(用户改文字、刷新)就重置回 1.0×,
                        // 避免上一次缩放状态影响新内容。
                        var scale by remember(bmp) { mutableStateOf(1f) }
                        val minScale = 0.5f
                        val maxScale = 4f
                        val transformableState = rememberTransformableState { zoomChange, _, _ ->
                            scale = (scale * zoomChange).coerceIn(minScale, maxScale)
                        }

                        // 预览框按真实纵横比显示:
                        //   - 固定显示高度 200dp,让字看得清
                        //   - 宽度 = 高度 × 真实纵横比(textLength / WIDTH_DOTS)
                        //   - 内容宽度 > 容器宽度时,横向滚动
                        val displayHeight = 200.dp
                        val aspectRatio = if (bmp.height > 0) {
                            bmp.width.toFloat() / bmp.height.toFloat()
                        } else {
                            1f
                        }
                        val displayWidth = displayHeight * aspectRatio

                        // 实际打印尺寸 —— 58mm 纸,WIDTH_DOTS=384 → 1dot = 58/384 mm
                        val dotsToMm = 58f / WIDTH_DOTS
                        val actualLengthCm = bmp.width * dotsToMm / 10f
                        val actualWidthCm = bmp.height * dotsToMm / 10f

                        // 预览相对实际打印的缩放比(像素维度比较):
                        // 200dp × density 像素 = 屏幕显示高度
                        // bmp.height 像素 = 位图实际高度(= 实际打印点数 1:1)
                        // scale=1 表示预览和实际等大,>1 表示预览放大,<1 表示预览缩小
                        val density = LocalDensity.current.density
                        val previewHeightPx = 200f * density * scale
                        val scaleToActual = previewHeightPx / bmp.height

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(displayHeight)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .horizontalScroll(rememberScrollState()),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "广告横带预览",
                                modifier = Modifier
                                    .height(displayHeight)
                                    .width(displayWidth)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center,
                                    )
                                    .transformable(
                                        state = transformableState,
                                        lockRotationOnZoomPan = true,
                                    ),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Text(
                            "实际 ${"%.1f".format(actualLengthCm)} cm × ${"%.1f".format(actualWidthCm)} cm" +
                                " (${bmp.width}×${bmp.height} 点) · 预览 ${"%.2f".format(scaleToActual)}×" +
                                " · 双指可缩放",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color.White, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("请输入广告内容", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

        // 底部操作栏 —— 「打印预览」是 noop(预览已经实时在上方显示),保留按钮只是
        // 与其他页面对齐;真正可点的是「打印」。
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            preview = withContext(Dispatchers.IO) { renderAdBannerToPixelMap(text) }
                            message = "预览已刷新"
                        }
                    },
                    enabled = !printing && flat.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("刷新预览") }
                Button(
                    onClick = {
                        scope.launch {
                            val fault = PrinterConnection.preflightCheck()
                            if (fault != null) { message = fault; return@launch }
                            val cached = previewRaster
                            if (cached == null) {
                                message = "预览未就绪,请稍候"
                                return@launch
                            }
                            printing = true
                            try {
                                // 直接复用预览阶段算好的光栅,保证预览 = 打印。
                                // 缩略图用当前预览图即可,不再重复渲染。
                                PrintQueue.enqueue(
                                    PrintJob(
                                        title = "广告横带",
                                        raster = cached,
                                        copies = 1,
                                    )
                                )
                                val thumb = preview
                                if (thumb != null) {
                                    addHistory(
                                        context,
                                        HIST_TYPE_AD_BANNER,
                                        thumb,
                                        "{\"text\":\"${text.take(200).replace("\"", "\\\"")}\"}",
                                    )
                                }
                                message = "已提交到打印队列"
                            } catch (e: Throwable) {
                                message = "提交失败:${e.message}"
                            } finally {
                                printing = false
                            }
                        }
                    },
                    enabled = !printing && connected && flat.isNotEmpty() && previewRaster != null,
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

/** 锁定的「字段名:固定值」展示行,明确告诉用户这些参数不可改 */
@Composable
private fun LockedField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 与 RasterEncoder.flattenAdBannerText 规则一致,用于打印前判空 */
private fun flattenAdBannerPreview(text: String): String {
    if (text.isEmpty()) return ""
    val replaced = text.replace(Regex("[\\r\\n\\t]+"), " ")
    return replaced.replace(Regex(" {2,}"), " ").trim()
}
