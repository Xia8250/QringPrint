package com.thisko.qringprint.ui.pages

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.common.composeCanvas
import com.thisko.qringprint.common.compositeToBitmap
import com.thisko.qringprint.common.renderElementNow
import com.thisko.qringprint.model.CODE_TYPES
import com.thisko.qringprint.model.CanvasDoc
import com.thisko.qringprint.model.CanvasElement
import com.thisko.qringprint.model.DEFAULT_CODE_1D_WIDTH
import com.thisko.qringprint.model.DEFAULT_CODE_2D_SIZE
import com.thisko.qringprint.model.DEFAULT_IMAGE_WIDTH
import com.thisko.qringprint.bluetooth.DEFAULT_TEXT_OPTIONS
import com.thisko.qringprint.model.HIST_TYPE_CUSTOM
import com.thisko.qringprint.model.ElementKind
import com.thisko.qringprint.model.addHistory
import com.thisko.qringprint.model.centeredX
import com.thisko.qringprint.common.codeOneDAspect
import com.thisko.qringprint.model.findTemplate
import com.thisko.qringprint.model.loadTemplates
import com.thisko.qringprint.model.nextInsertY
import com.thisko.qringprint.model.saveTemplate
import com.thisko.qringprint.model.templateToElements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thisko.qringprint.ui.motion.pageEnter

/**
 * 自定义打印 —— 多元素画布编辑器。
 *
 * 这是 QringPrint 最复杂的页面:文字/图片/条码三种元素都能在画布上拖动/缩放/层级调整,
 * 全部二值化后合成一张 384 点宽的位图发给打印机。
 *
 * 关键设计:画布坐标系用"打印点"存,屏幕显示用 Canvas 缩放系数换算。
 * 这样元素拖动直接得到打印点位移,合成时不用换单位,所见即所打。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPrintPage(templateId: String? = null, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = remember { mutableStateOf(CanvasDoc()) }
    val selectedId = remember { mutableStateOf("") }
    var stageMessage by remember { mutableStateOf<String?>(null) }
    var printResult by remember { mutableStateOf<String?>(null) }
    var printing by remember { mutableStateOf(false) }
    var copies by remember { mutableStateOf(1) }
    var overviewBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // 二维码内容选择对话框
    var codeDialog by remember { mutableStateOf<CodeDialogState?>(null) }

    // 加载模板
    LaunchedEffect(templateId) {
        if (templateId != null) {
            val rec = withContext(Dispatchers.IO) { findTemplate(context, templateId) }
            if (rec != null) {
                doc.value = CanvasDoc(
                    elements = templateToElements(rec),
                    minLength = rec.minLength,
                )
            }
        }
    }

    // 拦截系统返回键
    BackHandler(enabled = true, onBack = onBack)

    LaunchedEffect(doc.value.elements.size) {
        overviewBmp = withContext(Dispatchers.IO) {
            val composite = composeCanvas(doc.value)
            if (composite.width == 0 || composite.height == 0) null
            else compositeToBitmap(composite)
        }
    }
    DisposableEffect(Unit) { onDispose { overviewBmp?.recycle() } }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val el = CanvasElement(
            kind = ElementKind.IMAGE,
            dotW = DEFAULT_IMAGE_WIDTH,
            dotH = DEFAULT_IMAGE_WIDTH,
            dotX = centeredX(DEFAULT_IMAGE_WIDTH),
            dotY = nextInsertY(doc.value),
            imageUri = uri.toString(),
        )
        doc.value = doc.value.copy(elements = doc.value.elements + el)
        selectedId.value = el.id
        // 触发首次渲染(解码+设置 aspect)
        scope.launch { renderElementNow(el, context.contentResolver) { stageMessage = it } }
    }

    Column(modifier = Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(if (templateId == null) "自定义打印" else "编辑模板", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") } },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        val rec = withContext(Dispatchers.IO) {
                            saveTemplate(context, "模板 ${System.currentTimeMillis() / 1000}", doc.value, templateId)
                        }
                        printResult = "已保存: ${rec.name}"
                    }
                }) { Icon(Icons.Filled.Save, contentDescription = "保存") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // 工具栏
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolbarButton(Icons.Filled.TextFields, "文字") {
                    val el = CanvasElement(kind = ElementKind.TEXT, text = "双击编辑", dotY = nextInsertY(doc.value), dotW = 200, dotH = 40, textOptions = DEFAULT_TEXT_OPTIONS)
                    doc.value = doc.value.copy(elements = doc.value.elements + el)
                    selectedId.value = el.id
                    scope.launch { renderElementNow(el, context.contentResolver) { stageMessage = it } }
                }
                ToolbarButton(Icons.Filled.PhotoLibrary, "图片") { imagePicker.launch("image/*") }
                ToolbarButton(Icons.Filled.HorizontalRule, "分割线") {
                    val el = CanvasElement(kind = ElementKind.LINE, dotX = 16, dotY = nextInsertY(doc.value), dotW = 352, dotH = 2)
                    doc.value = doc.value.add(el)
                }
                ToolbarButton(Icons.Filled.QrCode2, "条码") {
                    // 弹出对话框让用户选择内容类型(网址/WiFi/联系人/日程/纯文本)
                    codeDialog = CodeDialogState(
                        kind = CodeKind.QR,
                        content = "https://example.com",
                        typeIndex = 9,
                    )
                }
                ToolbarButton(Icons.Filled.VerticalAlignTop, "置顶") {
                    val id = selectedId.value.ifEmpty { return@ToolbarButton }
                    doc.value.toTop(id).let { doc.value = it }
                }
                ToolbarButton(Icons.Filled.VerticalAlignBottom, "置底") {
                    val id = selectedId.value.ifEmpty { return@ToolbarButton }
                    doc.value.toBottom(id).let { doc.value = it }
                }
                ToolbarButton(Icons.Filled.Delete, "删除") {
                    val id = selectedId.value.ifEmpty { return@ToolbarButton }
                    val newDoc = doc.value.copy(elements = doc.value.elements.filterNot { it.id == id })
                    doc.value = newDoc
                    selectedId.value = ""
                }
            }
        }

        // 画布区域
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            CanvasSurface(
                doc = doc.value,
                selectedId = selectedId.value,
                onSelect = { id -> selectedId.value = id },
                onMove = { id, dx, dy ->
                    val newElements = doc.value.elements.map {
                        if (it.id == id) it.copy(dotX = (it.dotX + dx).toInt().coerceAtLeast(0), dotY = (it.dotY + dy).toInt().coerceAtLeast(0)) else it
                    }
                    doc.value = doc.value.copy(elements = newElements)
                },
                onSizeChanged = { canvasSize = it },
            )
        }

        // 状态信息
        (stageMessage ?: printResult)?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(it, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
            }
        }

        overviewBmp?.let { ov ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("总览", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.Image(
                        bitmap = ov.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 打印按钮
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("副本", fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { if (copies > 1) copies-- }, enabled = copies > 1) { Text("-", fontSize = 18.sp) }
                    Text("$copies", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
                    TextButton(onClick = { if (copies < 9) copies++ }, enabled = copies < 9) { Text("+", fontSize = 18.sp) }
                }
                Button(
                    onClick = {
                        scope.launch {
                            val fault = PrinterConnection.preflightCheck()
                            if (fault != null) { printResult = fault; return@launch }
                            try {
                                val composite = withContext(Dispatchers.IO) { composeCanvas(doc.value) }
                                val raster = com.thisko.qringprint.bluetooth.packBinaryToRaster(
                                    composite.binary, composite.width, composite.height
                                )
                                com.thisko.qringprint.bluetooth.PrintQueue.enqueue(
                                    com.thisko.qringprint.bluetooth.PrintJob(
                                        title = if (copies > 1) "自定义 (${copies}份)" else "自定义",
                                        raster = raster,
                                        copies = copies.coerceAtLeast(1),
                                    )
                                )
                                printResult = "已提交到队列 (副本×${copies})"
                                val bitmap = compositeToBitmap(composite)
                                addHistory(context, HIST_TYPE_CUSTOM, bitmap, "{\"elements\":${doc.value.elements.size},\"copies\":$copies}")
                                bitmap.recycle()
                            } catch (e: Throwable) {
                                printResult = "提交失败: ${e.message}"
                            }
                        }
                    },
                    enabled = doc.value.elements.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    val n = copies
                    Text(if (n > 1) "打印 ${doc.value.elements.size} 元素 ×$n" else "打印 (${doc.value.elements.size} 元素)")
                }
            }
        }
    }

    // 二维码内容输入对话框
    codeDialog?.let { state ->
        CodeContentDialog(
            state = state,
            onDismiss = { codeDialog = null },
            onConfirm = { finalContent, finalTypeIndex ->
                codeDialog = null
                val el = CanvasElement(
                    kind = ElementKind.CODE,
                    dotW = DEFAULT_CODE_2D_SIZE,
                    dotH = DEFAULT_CODE_2D_SIZE,
                    dotX = centeredX(DEFAULT_CODE_2D_SIZE),
                    dotY = nextInsertY(doc.value),
                    codeContent = finalContent,
                    codeTypeIndex = finalTypeIndex,
                    aspect = 1.0,
                )
                doc.value = doc.value.copy(elements = doc.value.elements + el)
                selectedId.value = el.id
                scope.launch { renderElementNow(el, context.contentResolver) { stageMessage = it } }
            },
        )
    }
}

private enum class CodeKind { QR, ONE_D }

private data class CodeDialogState(
    val kind: CodeKind,
    val content: String,
    val typeIndex: Int,
)

/** 二维码内容输入对话框 —— 网址/WiFi/联系人/日程/纯文本 快捷预设 */
@Composable
private fun CodeContentDialog(
    state: CodeDialogState,
    onDismiss: () -> Unit,
    onConfirm: (content: String, typeIndex: Int) -> Unit,
) {
    var content by remember(state) { mutableStateOf(state.content) }
    var selectedPreset by remember(state) { mutableStateOf(0) }  // 0=text, 1=url, 2=wifi, 3=contact, 4=event
    val presets = listOf(
        "纯文本" to { c: String -> c },
        "网址" to { c: String -> if (c.startsWith("http", ignoreCase = true)) c else "https://$c" },
        "WiFi" to { c: String ->
            // c 格式: ssid|password|security
            val parts = c.split("|")
            val ssid = parts.getOrNull(0) ?: ""
            val pwd = parts.getOrNull(1) ?: ""
            val sec = parts.getOrNull(2) ?: "WPA"
            "WIFI:T:$sec;S:$ssid;P:$pwd;;"
        },
        "联系人" to { c: String ->
            // c 格式: 姓名|电话|邮箱(用 | 分隔)
            val parts = c.split("|")
            val name = parts.getOrNull(0) ?: ""
            val tel = parts.getOrNull(1) ?: ""
            val mail = parts.getOrNull(2) ?: ""
            buildString {
                append("BEGIN:VCARD\nVERSION:3.0\n")
                append("FN:").append(name).append("\n")
                if (tel.isNotEmpty()) append("TEL:").append(tel).append("\n")
                if (mail.isNotEmpty()) append("EMAIL:").append(mail).append("\n")
                append("END:VCARD")
            }
        },
        "日程" to { c: String ->
            // c 格式: 主题|开始时间(yyyyMMddTHHmmss)|结束时间
            val parts = c.split("|")
            val summary = parts.getOrNull(0) ?: ""
            val start = parts.getOrNull(1) ?: "20260101T090000"
            val end = parts.getOrNull(2) ?: "20260101T100000"
            "BEGIN:VEVENT\nSUMMARY:$summary\nDTSTART:$start\nDTEND:$end\nEND:VEVENT"
        },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("二维码内容") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 预设选择
                Text("类型", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets.size) { idx ->
                        FilterChip(
                            selected = selectedPreset == idx,
                            onClick = {
                                selectedPreset = idx
                                // 自动填示例内容
                                content = when (idx) {
                                    0 -> "Hello QringPrint"
                                    1 -> "https://example.com"
                                    2 -> "HomeWiFi|mypassword123|WPA"
                                    3 -> "张三|13800000000|zhangsan@example.com"
                                    4 -> "周会|20260115T140000|20260115T150000"
                                    else -> ""
                                }
                            },
                            label = { Text(presets[idx].first, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 内容输入
                val hint = when (selectedPreset) {
                    1 -> "直接输入网址(可不带 http://)"
                    2 -> "格式: WiFi名|密码|加密方式(WPA/WEP/nopass)"
                    3 -> "格式: 姓名|电话|邮箱"
                    4 -> "格式: 主题|开始(yyyyMMddTHHmmss)|结束"
                    else -> "任意文本"
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    placeholder = { Text(hint, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val transformed = presets[selectedPreset].second(content)
                onConfirm(transformed, 9)  // 9 = QR_CODE
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp)
    }
}

/** 画布表面 —— 显示所有元素,支持点选和拖动 */
@Composable
private fun CanvasSurface(
    doc: CanvasDoc,
    selectedId: String,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onSizeChanged: (IntSize) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { onSizeChanged(it) }
    ) {
        // 画布缩放系数:打印点 (WIDTH_DOTS=384) → 屏幕像素
        val widthDots = com.thisko.qringprint.bluetooth.WIDTH_DOTS
        val heightDots = maxOf(widthDots, doc.height())  // 至少和宽一样高
        // 用 BoxWithConstraints 拿可用尺寸算缩放
        BoxWithConstraintsCalc(
            widthDots = widthDots,
            heightDots = heightDots,
            content = { scale ->
                val density = LocalDensity.current
                Column(modifier = Modifier.fillMaxSize()) {
                    doc.elements.forEach { el ->
                        if (el.binary != null && el.preview != null) {
                            val isSelected = el.id == selectedId
                            Box(
                                modifier = Modifier
                                    .padding(
                                        start = (el.dotX * scale).dp,
                                        top = (el.dotY * scale).dp,
                                    )
                                    .width((el.dotW * scale).dp)
                                    .height((el.dotH * scale).dp)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .pointerInput(el.id) {
                                        detectTapGestures(onTap = { onSelect(el.id) })
                                    }
                                    .pointerInput(el.id) {
                                        detectDragGestures { _, drag ->
                                            val dxPt = drag.x / scale
                                            val dyPt = drag.y / scale
                                            onMove(el.id, dxPt, dyPt)
                                        }
                                    }
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = el.preview!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                if (isSelected) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

/** 用 BoxWithConstraints 拿可用宽度,计算每个打印点对应的 dp */
@Composable
private fun BoxWithConstraintsCalc(
    widthDots: Int,
    heightDots: Int,
    content: @Composable (scale: Float) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availW = maxWidth
        val availH = maxHeight
        // 按宽度比例缩放,保持宽高比
        val scale = (availW.value / widthDots).coerceAtLeast(0.1f)
        val canvasWidthDp = (widthDots * scale).dp
        val canvasHeightDp = (heightDots * scale).dp
        Box(modifier = Modifier.width(canvasWidthDp).height(canvasHeightDp)) {
            content(scale)
        }
    }
}



// items helper for LazyRow used in dialog
private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, block: @Composable (Int) -> Unit) {
    repeat(count) { idx -> item { block(idx) } }
}
