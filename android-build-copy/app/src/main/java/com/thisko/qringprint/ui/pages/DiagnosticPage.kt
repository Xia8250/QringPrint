package com.thisko.qringprint.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.bluetooth.DEFAULT_TEXT_OPTIONS as BLT_DEFAULT
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.renderTextToPixelMap
import com.thisko.qringprint.common.composeCanvas
import com.thisko.qringprint.common.renderElementNow
import com.thisko.qringprint.model.CanvasDoc
import com.thisko.qringprint.model.CanvasElement
import com.thisko.qringprint.model.DEFAULT_CODE_2D_SIZE
import com.thisko.qringprint.model.ElementKind
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val queueState by PrintQueue.state.collectAsState()
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }
    BackHandler(enabled = true, onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("诊断页", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 5 },
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val connStatus = PrinterConnection.currentStatus
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("连接状态", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("连接: ${if (connStatus.connState.name == "CONNECTED") "已连接 ✓" else "未连接 ✗"}", fontSize = 13.sp)
                        Text("电量: ${connStatus.batteryPercent?.let { "$it%" } ?: "--"}", fontSize = 13.sp)
                        Text("纸仓: ${connStatus.paperState.name}", fontSize = 13.sp)
                        Text("机器: ${connStatus.hardwareState.name}", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))

                val isQueueRunning = queueState !is com.thisko.qringprint.bluetooth.QueueState.Idle
                if (isQueueRunning) {
                    val queue = queueState as com.thisko.qringprint.bluetooth.QueueState.Running
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("队列中: ${queue.current}/${queue.total} ${queue.currentTitle}", fontSize = 13.sp)
                            LinearProgressIndicator(
                                progress = { queue.current.toFloat() / queue.total.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                DiagnosticButton(
                    title = "测试打印",
                    subtitle = "打印测试小票,验证连接与出纸",
                    enabled = !running,
                ) {
                    submitTestReceipt(context, scope, setRunning = { running = it }, setStatus = { status = it })
                }
                DiagnosticButton(
                    title = "发送原始测试字节",
                    subtitle = "0x1b 0x40 + 示例文本,检查端口能不能通",
                    enabled = !running,
                ) { sendRawTest(scope, setRunning = { running = it }, setStatus = { status = it }) }
                DiagnosticButton(
                    title = "多份测试 (3 份)",
                    subtitle = "打 3 份相同内容,验证队列与副本",
                    enabled = !running,
                ) { submitMultiCopy(scope, setRunning = { running = it }, setStatus = { status = it }) }
                Spacer(Modifier.height(8.dp))
                DiagnosticButton(
                    title = "仅查询状态",
                    subtitle = "不打印,只诊断连接是否有响应",
                    enabled = !running,
                ) { queryOnly(scope, setStatus = { status = it }) }
                Spacer(Modifier.height(8.dp))
                if (PrintQueue.pendingCount > 0) {
                    OutlinedButton(
                        onClick = { PrintQueue.clear() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清空队列 (${PrintQueue.pendingCount})") }
                }
                status?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                    ) { Text(message, modifier = Modifier.padding(12.dp), fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = { if (enabled) onClick() },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun submitTestReceipt(
    context: android.content.Context,
    scope: CoroutineScope,
    setRunning: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
) {
    setRunning(true)
    scope.launch {
        try {
            val doc = CanvasDoc(
                elements = listOf(
                    CanvasElement(
                        kind = ElementKind.TEXT,
                        dotX = 8, dotY = 8, dotW = 368, dotH = 32,
                        text = "浣熊快印 测试小票",
                        textOptions = BLT_DEFAULT.copy(fontSize = 28, bold = true),
                    ),
                    CanvasElement(
                        kind = ElementKind.TEXT,
                        dotX = 8, dotY = 48, dotW = 368, dotH = 16,
                        text = "-".repeat(46),
                        textOptions = BLT_DEFAULT.copy(fontSize = 16),
                    ),
                    CanvasElement(
                        kind = ElementKind.TEXT,
                        dotX = 8, dotY = 72, dotW = 368, dotH = 72,
                        text = "设备: 58mm 热敏打印机\n状态: 正常\n时间: " +
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                        textOptions = BLT_DEFAULT.copy(fontSize = 20),
                    ),
                    CanvasElement(
                        kind = ElementKind.CODE,
                        dotX = 92, dotY = 160, dotW = DEFAULT_CODE_2D_SIZE, dotH = DEFAULT_CODE_2D_SIZE,
                        codeContent = "https://qringprint.example.com",
                        codeTypeIndex = 9,
                        aspect = 1.0,
                    ),
                ),
                minLength = 360,
            )
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val body = "\n\n 浣熊快印 测试小票 \n" +
                "-".repeat(32) + "\n" +
                "设备 : 58mm 热敏打印机\n" +
                "状态 : 正常\n" +
                "时间 : " + timestamp + "\n" +
                "-".repeat(32) + "\n" +
                "如果看到这行字说明测试 OK\n\n\n"
            val bmp = renderTextToPixelMap(body, BLT_DEFAULT.copy(fontSize = 24, bold = true))
            val gray = bitmapToGray(bmp)
            val bin = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
            val raster = packBinaryToRaster(bin, gray.width, gray.height)
            PrintQueue.enqueue(PrintJob(title = "测试小票", raster = raster))
            setStatus("已提交到队列")
        } catch (error: Throwable) {
            setStatus("提交失败: ${error.message}")
        } finally {
            setRunning(false)
        }
    }
}

private fun sendRawTest(
    scope: CoroutineScope,
    setRunning: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
) {
    setRunning(true)
    scope.launch {
        try {
            val bitmap = renderTextToPixelMap("\n\n浣熊快印 原始测试\n连接正常\n\n\n", BLT_DEFAULT.copy(fontSize = 24))
            val gray = bitmapToGray(bitmap)
            val binary = ditherToBinary(gray, DitherMode.NONE, com.thisko.qringprint.bluetooth.THRESHOLD_TEXT)
            val raster = packBinaryToRaster(binary, gray.width, gray.height)
            PrintQueue.enqueue(PrintJob(title = "原始测试", raster = raster))
            setStatus("已提交")
        } catch (error: Throwable) {
            setStatus("提交失败: ${error.message}")
        } finally {
            setRunning(false)
        }
    }
}

private fun submitMultiCopy(
    scope: CoroutineScope,
    setRunning: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
) {
    setRunning(true)
    scope.launch {
        try {
            val bitmap = renderTextToPixelMap("\n\n队列测试\n3 份副本\n\n\n", BLT_DEFAULT.copy(fontSize = 24))
            val gray = bitmapToGray(bitmap)
            val binary = ditherToBinary(gray, DitherMode.NONE, com.thisko.qringprint.bluetooth.THRESHOLD_TEXT)
            val raster = packBinaryToRaster(binary, gray.width, gray.height)
            PrintQueue.enqueue(PrintJob(title = "队列 3 份", raster = raster, copies = 3))
            setStatus("已提交 3 份")
        } catch (error: Throwable) {
            setStatus("提交失败: ${error.message}")
        } finally {
            setRunning(false)
        }
    }
}

private fun queryOnly(scope: CoroutineScope, setStatus: (String) -> Unit) {
    scope.launch {
        try {
            val status = PrinterConnection.queryStatus()
            val battery = PrinterConnection.queryBattery()
            setStatus("状态: ${status ?: "null"}, 电量: ${battery ?: "null"}")
        } catch (error: Throwable) {
            setStatus("查询失败: ${error.message}")
        }
    }
}
