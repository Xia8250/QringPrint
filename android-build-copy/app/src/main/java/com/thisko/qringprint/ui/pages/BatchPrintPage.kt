package com.thisko.qringprint.ui.pages

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.DEFAULT_TEXT_OPTIONS
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.QueueState
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.decodeImageToPrintWidth
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.renderTextToPixelMap
import com.thisko.qringprint.model.SpreadsheetReader
import com.thisko.qringprint.ui.motion.pageEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BatchType(val label: String) {
    TEXT("文字"),
    IMAGE("图片"),
    TABLE("表格"),
    RECEIPT("小票"),
    FILE("CSV/Excel"),
}

data class BatchTask(
    val id: String = System.nanoTime().toString(),
    val type: BatchType,
    val title: String,
    val text: String = "",
    val uri: Uri? = null,
    val jobCount: Int = 1,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tasks by remember { mutableStateOf(listOf<BatchTask>()) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newType by remember { mutableStateOf(BatchType.TEXT) }
    var newText by remember { mutableStateOf("") }
    var pendingUris by remember { mutableStateOf(listOf<Uri>()) }
    var pendingNames by remember { mutableStateOf(listOf<String>()) }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val queueState by PrintQueue.state.collectAsState()

    BackHandler(enabled = true, onBack = onBack)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            pendingUris = uris.toList()
            pendingNames = uris.map { queryFileName(context, it) }
            newText = ""
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            pendingUris = uris.toList()
            pendingNames = uris.map { queryFileName(context, it) }
            newText = ""
        }
    }

    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId } ?: tasks.firstOrNull()
    val totalJobs = tasks.sumOf { it.jobCount }

    LaunchedEffect(selectedTask?.id) {
        val task = selectedTask
        preview = when {
            task == null -> null
            task.type == BatchType.IMAGE && task.uri != null -> withContext(Dispatchers.IO) {
                runCatching { decodeImageToPrintWidth(context.contentResolver, task.uri) }.getOrNull()
            }
            task.type == BatchType.FILE && task.uri != null -> withContext(Dispatchers.IO) {
                runCatching {
                    val sheet = SpreadsheetReader.read(context, task.uri, task.title)
                    val headerLine = sheet.headers.joinToString(" | ")
                    val sep = "-".repeat(headerLine.length.coerceIn(16, 60))
                    val body = sheet.rows.take(3).map { row ->
                        sheet.headers.mapIndexed { i, _ -> row.getOrNull(i).orEmpty() }.joinToString(" | ")
                    }.joinToString("\n")
                    textPreview(headerLine + "\n" + sep + "\n" + body)
                }.getOrNull()
            }
            task.type == BatchType.TABLE && task.text.isNotBlank() -> withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = renderTableToBitmap(
                        parseTableRows(task.text),
                        DEFAULT_TEXT_OPTIONS.fontSize.coerceIn(14, 24),
                        tableLineThickness(2),
                    )
                    val g = bitmapToGray(bmp)
                    val b = ditherToBinary(g, DitherMode.NONE, THRESHOLD_TEXT)
                    binaryToPreviewBitmap(b, g.width, g.height)
                }.getOrNull()
            }
            task.type == BatchType.RECEIPT && task.text.isNotBlank() -> withContext(Dispatchers.IO) {
                runCatching {
                    val lines = task.text.lines().mapNotNull { line ->
                        val parts = line.split('|')
                        if (parts.size < 3) null else ReceiptLine(parts[0].trim(), parts[1].trim(), parts[2].trim())
                    }
                    val total = lines.sumOf { (it.quantity.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }
                    val bmp = renderReceiptToBitmap(
                        title = "批量小票",
                        date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()),
                        items = lines,
                        total = total,
                        payment = "微信支付",
                        footer = "谢谢光临",
                        itemized = true,
                        fontSize = 20,
                        lineWidth = tableLineThickness(2),
                    )
                    val g = bitmapToGray(bmp)
                    val b = ditherToBinary(g, DitherMode.NONE, THRESHOLD_TEXT)
                    binaryToPreviewBitmap(b, g.width, g.height)
                }.getOrNull()
            }
            task.text.isNotBlank() -> withContext(Dispatchers.IO) { runCatching { textPreview(task.text) }.getOrNull() }
            else -> null
        }
    }

    Column(Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("批量打印", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )

        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            ConnectionBanner()
            Spacer(Modifier.height(12.dp))

            if (queueState is QueueState.Running) {
                val queue = queueState as QueueState.Running
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        LinearProgressIndicator(queue.current.toFloat() / queue.total.coerceAtLeast(1), Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("正在打印 ${queue.current}/${queue.total} - ${queue.currentTitle}", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (tasks.isEmpty()) {
                Text("队列为空，点击下方按钮添加打印任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                        val isSel = selectedTask != null && selectedTask.id == task.id
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                Modifier.padding(12.dp).clickable { selectedTaskId = task.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(26.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary) {
                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${task.type.label}" + if (task.jobCount > 1) " - ${task.jobCount} 份" else "",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton({
                                    if (index > 0) {
                                        val list = tasks.toMutableList()
                                        val item = list.removeAt(index)
                                        list.add(index - 1, item)
                                        tasks = list
                                    }
                                }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.ArrowUpward, null, tint = if (index == 0) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                IconButton({
                                    if (index < tasks.size - 1) {
                                        val list = tasks.toMutableList()
                                        val item = list.removeAt(index)
                                        list.add(index + 1, item)
                                        tasks = list
                                    }
                                }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.ArrowDownward, null, tint = if (index == tasks.size - 1) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                IconButton({ tasks = tasks.filterNot { it.id == task.id }; if (selectedTaskId == task.id) selectedTaskId = null }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showAddDialog = true; newType = BatchType.TEXT; newText = ""; pendingUris = emptyList(); pendingNames = emptyList() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("添加任务")
                }
                if (tasks.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { tasks = emptyList(); selectedTaskId = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Clear, null); Spacer(Modifier.width(6.dp)); Text("清空")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            PreviewCard(preview, Modifier.fillMaxWidth().heightIn(min = 150.dp))
            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "预览：" + (selectedTask?.title ?: "") + "  |  队列共 " + tasks.size + " 项，" + totalJobs + " 份待打印",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }

        Button(
            onClick = {
                scope.launch {
                    PrinterConnection.preflightCheck()?.let { msg -> message = msg; return@launch }
                    printing = true
                    try {
                        val jobs = withContext(Dispatchers.IO) { buildJobs(context, tasks) }
                        PrintQueue.enqueueAll(jobs)
                        message = "已提交 " + jobs.size + " 个打印任务"
                    } catch (e: Throwable) {
                        message = "打印失败：" + e.message
                    } finally { printing = false }
                }
            },
            enabled = !printing && tasks.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            if (printing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Filled.Print, null); Spacer(Modifier.width(8.dp)); Text("全部打印（" + totalJobs + " 份）")
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加打印任务") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BatchType.values().forEach { type ->
                            FilterChip(newType == type, { newType = type }, { Text(type.label) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    when (newType) {
                        BatchType.IMAGE -> Button({ imagePicker.launch("image/*") }, Modifier.fillMaxWidth()) {
                            Text(if (pendingUris.isNotEmpty()) "已选择 " + pendingUris.size + " 张图片" else "选择图片（可多选）")
                        }
                        BatchType.FILE -> Button({ filePicker.launch("*/*") }, Modifier.fillMaxWidth()) {
                            Text(if (pendingUris.isNotEmpty()) "已选择 " + pendingUris.size + " 个文件" else "选择 CSV/Excel（可多选）")
                        }
                        else -> OutlinedTextField(
                            newText,
                            { newText = it },
                            label = {
                                Text(
                                    when (newType) {
                                        BatchType.TABLE -> "表格（第一行表头，竖线分隔）"
                                        BatchType.RECEIPT -> "商品（名称|数量|单价）"
                                        else -> "打印内容"
                                    }
                                )
                            },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val valid = when (newType) {
                        BatchType.IMAGE, BatchType.FILE -> pendingUris.isNotEmpty()
                        else -> newText.isNotBlank()
                    }
                    if (valid) {
                        if (newType == BatchType.IMAGE) {
                            val added = pendingUris.map { uri ->
                                BatchTask(type = BatchType.IMAGE, title = queryFileName(context, uri), uri = uri, jobCount = 1)
                            }
                            tasks += added
                        } else if (newType == BatchType.FILE) {
                            val uris = pendingUris.toList()
                            val names = pendingNames.toList()
                            scope.launch {
                                uris.forEachIndexed { i, uri ->
                                    val name = names.getOrElse(i) { queryFileName(context, uri) }
                                    val count = withContext(Dispatchers.IO) {
                                        runCatching { SpreadsheetReader.read(context, uri, name).rows.size }.getOrDefault(0)
                                    }
                                    tasks += BatchTask(type = BatchType.FILE, title = name, uri = uri, jobCount = count.coerceAtLeast(1))
                                }
                            }
                        } else {
                            tasks += BatchTask(
                                type = newType,
                                title = newType.label + "任务",
                                text = newText,
                                jobCount = 1
                            )
                        }
                        selectedTaskId = null
                        showAddDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton({ showAddDialog = false }) { Text("取消") } }
        )
    }
}

private suspend fun textPreview(text: String): android.graphics.Bitmap {
    val source = renderTextToPixelMap(text, DEFAULT_TEXT_OPTIONS.copy(fontSize = 20))
    val gray = bitmapToGray(source)
    val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
    return binaryToPreviewBitmap(binary, gray.width, gray.height)
}

private suspend fun buildJobs(context: android.content.Context, tasks: List<BatchTask>): List<PrintJob> {
    val jobs = mutableListOf<PrintJob>()
    tasks.forEachIndexed { taskIndex, task ->
        when (task.type) {
            BatchType.IMAGE -> {
                val uri = task.uri ?: return@forEachIndexed
                val bitmap = decodeImageToPrintWidth(context.contentResolver, uri)
                val gray = com.thisko.qringprint.bluetooth.bitmapToGray(bitmap)
                val binary = ditherToBinary(gray, DitherMode.NONE, com.thisko.qringprint.bluetooth.THRESHOLD_IMAGE)
                jobs += PrintJob(title = task.title, raster = packBinaryToRaster(binary, gray.width, gray.height))
            }
            BatchType.FILE -> {
                val uri = task.uri ?: return@forEachIndexed
                val sheet = SpreadsheetReader.read(context, uri, task.title)
                sheet.rows.forEachIndexed { rowIndex, row ->
                    val text = sheet.headers.mapIndexed { index, header -> header + ": " + row.getOrNull(index).orEmpty() }.joinToString("\n")
                    jobs += textJob(task.title + " " + (rowIndex + 1), text)
                }
            }
            BatchType.TABLE -> {
                val bitmap = renderTableToBitmap(
                    parseTableRows(task.text),
                    DEFAULT_TEXT_OPTIONS.fontSize.coerceIn(14, 24),
                    tableLineThickness(2)
                )
                val gray = bitmapToGray(bitmap)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                jobs += PrintJob(title = task.title, raster = packBinaryToRaster(binary, gray.width, gray.height))
            }
            BatchType.RECEIPT -> {
                val lines = task.text.lines().mapNotNull { line ->
                    val parts = line.split('|')
                    if (parts.size < 3) null else ReceiptLine(parts[0].trim(), parts[1].trim(), parts[2].trim())
                }
                val total = lines.sumOf { (it.quantity.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }
                val bitmap = renderReceiptToBitmap(
                    title = "批量小票",
                    date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()),
                    items = lines,
                    total = total,
                    payment = "微信支付",
                    footer = "谢谢光临",
                    itemized = true,
                    fontSize = 20,
                    lineWidth = tableLineThickness(2)
                )
                val gray = bitmapToGray(bitmap)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                jobs += PrintJob(title = task.title, raster = packBinaryToRaster(binary, gray.width, gray.height))
            }
            else -> jobs += textJob(task.title, task.text)
        }
    }
    return jobs
}

private fun textJob(title: String, text: String): PrintJob {
    val bitmap = renderTextToPixelMap(text, DEFAULT_TEXT_OPTIONS.copy(fontSize = 20))
    val gray = bitmapToGray(bitmap)
    val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
    return PrintJob(title = title, raster = packBinaryToRaster(binary, gray.width, gray.height))
}

private fun queryFileName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else "imported.csv"
        } ?: "imported.csv"
    }.getOrDefault("imported.csv")
}
