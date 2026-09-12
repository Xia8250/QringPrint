package com.thisko.qringprint.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.bitmapToGrayRaw
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.common.renderCodeGray
import com.thisko.qringprint.model.CODE_TYPES
import com.thisko.qringprint.model.CodeCategory
import com.thisko.qringprint.model.CodeType
import com.thisko.qringprint.model.HIST_TYPE_CODE
import com.thisko.qringprint.model.HistoryBus
import com.thisko.qringprint.model.addHistoryGray
import com.thisko.qringprint.model.loadDefaultThickness
import com.thisko.qringprint.model.sampleContent
import com.thisko.qringprint.model.typesOf
import com.thisko.qringprint.model.validateContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thisko.qringprint.ui.motion.pageEnter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodePrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by PrinterConnection.status.collectAsStateWithLifecycle()
    val handoff = remember {
        HistoryBus.consumeCode()?.let { payload ->
            runCatching {
                val json = org.json.JSONObject(payload)
                val type = CODE_TYPES.firstOrNull { it.label == json.optString("type") }
                json.optString("code") to type
            }.getOrNull()
        }
    }
    var category by remember { mutableStateOf(handoff?.second?.category ?: CodeCategory.ONE_D) }
    var typeIndex by remember {
        val restoredType = handoff?.second
        mutableStateOf(CODE_TYPES.indexOfFirst { it == restoredType }.coerceAtLeast(0))
    }
    var content by remember { mutableStateOf(handoff?.first.orEmpty()) }
    var thickness by remember { mutableStateOf(loadDefaultThickness(context)) }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var encodeError by remember { mutableStateOf<String?>(null) }

    val typesInCategory = remember(category) { typesOf(category) }
    val currentType: CodeType = typesInCategory.getOrNull(typeIndex) ?: typesInCategory.first()
    val validation = validateContent(currentType, content)

    // 拦截系统返回键
    BackHandler(enabled = true, onBack = onBack)

    // 内容或类型变了,生成预览
    LaunchedEffect(content, currentType) {
        if (content.isEmpty() || validation != null) {
            preview = null
            encodeError = null
            return@LaunchedEffect
        }
        try {
            preview = withContext(Dispatchers.IO) {
                val gray = renderCodeGray(content, currentType)
                com.thisko.qringprint.bluetooth.binaryToPreviewBitmap(
                    com.thisko.qringprint.bluetooth.ditherToBinary(gray, com.thisko.qringprint.bluetooth.DitherMode.NONE, com.thisko.qringprint.bluetooth.THRESHOLD_IMAGE),
                    gray.width, gray.height
                )
            }
            encodeError = null
        } catch (e: Throwable) {
            preview = null
            encodeError = "生成失败: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    Column(modifier = Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("条码打印", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = category == CodeCategory.ONE_D,
                    onClick = { category = CodeCategory.ONE_D; typeIndex = 0; content = "" },
                    label = { Text("一维码") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = category == CodeCategory.TWO_D,
                    onClick = { category = CodeCategory.TWO_D; typeIndex = 0; content = "" },
                    label = { Text("二维码") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("类型", fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(typesInCategory.size) { idx ->
                    FilterChip(
                        selected = typeIndex == idx,
                        onClick = { typeIndex = idx; content = sampleContent(typesInCategory[idx]) },
                        label = { Text(typesInCategory[idx].label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(currentType.hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            // 内容快捷预设
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = { content = "https://" },
                    label = { Text("网址", fontSize = 11.sp) },
                )
                FilterChip(
                    selected = false,
                    onClick = { content = "WIFI:T:WPA;S:网络名;P:密码;;" },
                    label = { Text("WiFi", fontSize = 11.sp) },
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        content = "BEGIN:VCARD\n" +
                            "VERSION:3.0\n" +
                            "FN:姓名\n" +
                            "TEL:13800000000\n" +
                            "EMAIL:example@x.com\n" +
                            "END:VCARD"
                    },
                    label = { Text("联系人", fontSize = 11.sp) },
                )
                FilterChip(
                    selected = false,
                    onClick = { content = "BEGIN:VEVENT\n" +
                        "SUMMARY:事件\n" +
                        "DTSTART:20260101T090000\n" +
                        "DTEND:20260101T100000\n" +
                        "END:VEVENT"
                    },
                    label = { Text("日程", fontSize = 11.sp) },
                )
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                modifier = Modifier.fillMaxWidth(),
                isError = validation != null,
                supportingText = {
                    validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            )

            encodeError?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(err, modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(8.dp))
            }
            preview?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("浓度 $thickness", fontSize = 13.sp)
            Slider(value = thickness.toFloat(), onValueChange = { thickness = it.toInt() }, valueRange = 1f..3f, steps = 1)
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
                        printing = true
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val gray = renderCodeGray(content, currentType)
                                val binary = com.thisko.qringprint.bluetooth.ditherToBinary(gray, com.thisko.qringprint.bluetooth.DitherMode.NONE, com.thisko.qringprint.bluetooth.THRESHOLD_IMAGE)
                                val raster = packBinaryToRaster(binary, gray.width, gray.height)
                                PrinterConnection.printRaster(raster, thickness)
                            }
                            message = result.message
                            if (result.ok) {
                                val gray = renderCodeGray(content, currentType)
                                addHistoryGray(context, HIST_TYPE_CODE, gray, "{\"code\":\"${content.take(200).replace("\"","\\\"")}\",\"type\":\"${currentType.label}\"}")
                            }
                        } finally { printing = false }
                    }
                },
                enabled = !printing && content.isNotEmpty() && validation == null,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (printing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打印")
                }
            }
        }
    }
}

// items helper for LazyRow
private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, block: @Composable (Int) -> Unit) {
    repeat(count) { idx -> item { block(idx) } }
}


