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
import androidx.compose.material.icons.filled.Edit
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
import com.thisko.qringprint.bluetooth.ImageProcessOptions
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.QueueState
import com.thisko.qringprint.bluetooth.RasterData
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.TextRenderOptions
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.decodeImageToPrintWidth
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.processGray
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
    /** 当前选中的预设 key(IMAGE/TEXT/TABLE/RECEIPT/FILE 各有自己的一套) */
    val presetKey: String = "",
    /** 图片任务的图像处理选项。IMAGE 任务用;其他任务为 null。 */
    val imageOptions: ImageProcessOptions? = null,
    /** 文字/表格/票据/文件任务的排版选项。TABLE/RECEIPT 任务会忽略部分字段(只取用得到的)。 */
    val textOptions: TextRenderOptions? = null,
    /** 表格专用:字号 + 线宽档位(1~3)。仅 TABLE 用。 */
    val tableFontSize: Int? = null,
    val tableLineWidth: Int? = null,
    /** 表格专用:表头是否加粗。仅 TABLE 用。 */
    val tableBoldFirstRow: Boolean? = null,
    /** 票据专用:字号/线宽/明细展开。仅 RECEIPT 用。 */
    val receiptFontSize: Int? = null,
    val receiptLineWidth: Int? = null,
    val receiptItemized: Boolean? = null,
    /** 热敏头加热浓度 1~3,落到 PrintJob.thickness */
    val thickness: Int = BatchDefaults.THICKNESS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchPrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tasks by remember { mutableStateOf(listOf<BatchTask>()) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    /** 非 null = 对话框处于「编辑任务」模式,保存时更新对应任务 */
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var newType by remember { mutableStateOf(BatchType.TEXT) }
    var newText by remember { mutableStateOf("") }
    var newPresetKey by remember { mutableStateOf(presetListOf(BatchType.TEXT).first().key) }
    var newThickness by remember { mutableStateOf(BatchDefaults.THICKNESS) }
    var newCopies by remember { mutableStateOf(1) }
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

    // key 用整个 selectedTask(data class 相等比较):
    // 编辑任务改了预设/浓度后,即使 id 不变预览也会刷新。
    LaunchedEffect(selectedTask) {
        val task = selectedTask
        preview = when {
            task == null -> null
            task.type == BatchType.IMAGE && task.uri != null -> withContext(Dispatchers.IO) {
                runCatching {
                    // 与 buildJobs 完全一致的管线,保证预览 = 打印
                    val bitmap = decodeImageToPrintWidth(context.contentResolver, task.uri)
                    val gray = bitmapToGray(bitmap)
                    val opts = task.imageOptions ?: BatchDefaults.imageOptions()
                    val enhanced = processGray(gray, opts)
                    val binary = ditherToBinary(enhanced, opts.ditherMode, opts.threshold)
                    binaryToPreviewBitmap(binary, gray.width, gray.height)
                }.getOrNull()
            }
            task.type == BatchType.FILE && task.uri != null -> withContext(Dispatchers.IO) {
                runCatching {
                    val sheet = SpreadsheetReader.read(context, task.uri, task.title)
                    val headerLine = sheet.headers.joinToString(" | ")
                    val sep = "-".repeat(headerLine.length.coerceIn(16, 60))
                    val body = sheet.rows.take(3).map { row ->
                        sheet.headers.mapIndexed { i, _ -> row.getOrNull(i).orEmpty() }.joinToString(" | ")
                    }.joinToString("\n")
                    textPreview(headerLine + "\n" + sep + "\n" + body, task.textOptions ?: BatchDefaults.fileOptions())
                }.getOrNull()
            }
            task.type == BatchType.TABLE && task.text.isNotBlank() -> withContext(Dispatchers.IO) {
                runCatching {
                    val preset = BatchDefaults.tablePreset()
                    val bmp = renderTableToBitmap(
                        parseTableRows(task.text),
                        task.tableFontSize ?: preset.fontSize,
                        tableLineThickness(task.tableLineWidth ?: preset.lineWidth),
                        boldFirstRow = task.tableBoldFirstRow ?: preset.boldFirstRow,
                    )
                    val g = bitmapToGray(bmp)
                    val b = ditherToBinary(g, DitherMode.NONE, THRESHOLD_TEXT)
                    binaryToPreviewBitmap(b, g.width, g.height)
                }.getOrNull()
            }
            task.type == BatchType.RECEIPT && task.text.isNotBlank() -> withContext(Dispatchers.IO) {
                runCatching {
                    val preset = BatchDefaults.receiptPreset()
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
                        itemized = task.receiptItemized ?: preset.itemized,
                        fontSize = task.receiptFontSize ?: preset.fontSize,
                        lineWidth = tableLineThickness(task.receiptLineWidth ?: preset.lineWidth),
                    )
                    val g = bitmapToGray(bmp)
                    val b = ditherToBinary(g, DitherMode.NONE, THRESHOLD_TEXT)
                    binaryToPreviewBitmap(b, g.width, g.height)
                }.getOrNull()
            }
            task.text.isNotBlank() -> withContext(Dispatchers.IO) {
                runCatching { textPreview(task.text, task.textOptions ?: BatchDefaults.textOptions()) }.getOrNull()
            }
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
                                        task.type.label + " · " + presetLabelOf(task.type, task) +
                                            (if (task.jobCount > 1) " · ${task.jobCount} 份" else "") +
                                            " · 浓度${task.thickness}",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton({
                                    // 复用「添加任务」对话框编辑这个任务:改预设/浓度/份数(文字类还能改内容)
                                    editingTaskId = task.id
                                    newType = task.type
                                    newText = task.text
                                    newPresetKey = task.presetKey.ifEmpty { presetListOf(task.type).first().key }
                                    newThickness = task.thickness
                                    newCopies = task.jobCount
                                    pendingUris = emptyList(); pendingNames = emptyList()
                                    showAddDialog = true
                                }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.Edit, "编辑", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                    onClick = {
                        editingTaskId = null
                        showAddDialog = true
                        newType = BatchType.TEXT; newText = ""
                        newPresetKey = presetListOf(BatchType.TEXT).first().key
                        newThickness = BatchDefaults.THICKNESS; newCopies = 1
                        pendingUris = emptyList(); pendingNames = emptyList()
                    },
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
        val editing = editingTaskId != null
        val presets = presetListOf(newType)
        val currentPreset = presets.firstOrNull { it.key == newPresetKey } ?: presets.first()
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editing) "编辑任务（${newType.label}）" else "添加打印任务") },
            text = {
                Column {
                    if (!editing) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BatchType.values().forEach { type ->
                                FilterChip(
                                    newType == type,
                                    {
                                        newType = type
                                        newPresetKey = presetListOf(type).first().key
                                    },
                                    { Text(type.label) }
                                )
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
                        Spacer(Modifier.height(10.dp))
                    } else if (newType != BatchType.IMAGE && newType != BatchType.FILE) {
                        // 编辑文字类任务时允许顺手改内容
                        OutlinedTextField(
                            newText,
                            { newText = it },
                            label = { Text("内容") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // ---- 模板(和图片打印页一样的预设选择) ----
                    Text("模板", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { p ->
                            FilterChip(newPresetKey == p.key, { newPresetKey = p.key }, { Text(p.label) })
                        }
                    }
                    Text(currentPreset.hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    // ---- 打印浓度 ----
                    Text("打印浓度", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..3).forEach { t ->
                            FilterChip(newThickness == t, { newThickness = t }, { Text("浓度 $t") })
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // ---- 打印份数(FILE 的份数由表格行数决定,不手动改) ----
                    if (newType != BatchType.FILE) {
                        Text("打印份数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton({ if (newCopies > 1) newCopies-- }, Modifier.size(84.dp, 36.dp)) {
                                Text("−", fontSize = 16.sp)
                            }
                            Text("× $newCopies", Modifier.padding(horizontal = 14.dp), fontWeight = FontWeight.Medium)
                            OutlinedButton({ if (newCopies < 99) newCopies++ }, Modifier.size(84.dp, 36.dp)) {
                                Text("+", fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    if (editing) {
                        val id = editingTaskId
                        if (id != null) {
                            tasks = tasks.map { task ->
                                if (task.id != id) task
                                else {
                                    val base = task.withPreset(newPresetKey, newThickness, newCopies)
                                    // 文字类任务顺带更新内容
                                    if (task.type != BatchType.IMAGE && task.type != BatchType.FILE) base.copy(text = newText) else base
                                }
                            }
                            showAddDialog = false
                        }
                    } else {
                        val valid = when (newType) {
                            BatchType.IMAGE, BatchType.FILE -> pendingUris.isNotEmpty()
                            else -> newText.isNotBlank()
                        }
                        if (valid) {
                            if (newType == BatchType.IMAGE) {
                                val added = pendingUris.map { uri ->
                                    BatchTask(type = BatchType.IMAGE, title = queryFileName(context, uri), uri = uri)
                                        .withPreset(newPresetKey, newThickness, newCopies)
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
                                            .withPreset(newPresetKey, newThickness, 1)
                                    }
                                }
                            } else {
                                tasks += BatchTask(
                                    type = newType,
                                    title = newType.label + "任务",
                                    text = newText,
                                ).withPreset(newPresetKey, newThickness, newCopies)
                            }
                            selectedTaskId = null
                            showAddDialog = false
                        }
                    }
                }) { Text(if (editing) "保存" else "添加") }
            },
            dismissButton = { TextButton({ showAddDialog = false }) { Text("取消") } }
        )
    }
}

private suspend fun textPreview(text: String, options: TextRenderOptions): android.graphics.Bitmap {
    val source = renderTextToPixelMap(text, options)
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
                val gray = bitmapToGray(bitmap)
                val opts = task.imageOptions ?: BatchDefaults.imageOptions()
                val enhanced = processGray(gray, opts)
                val binary = ditherToBinary(enhanced, opts.ditherMode, opts.threshold)
                jobs += PrintJob(
                    title = task.title,
                    raster = packBinaryToRaster(binary, gray.width, gray.height),
                    copies = task.jobCount.coerceAtLeast(1),
                    thickness = task.thickness,
                )
            }
            BatchType.FILE -> {
                val uri = task.uri ?: return@forEachIndexed
                val sheet = SpreadsheetReader.read(context, uri, task.title)
                val opts = task.textOptions ?: BatchDefaults.fileOptions()
                sheet.rows.forEachIndexed { rowIndex, row ->
                    val text = sheet.headers.mapIndexed { index, header -> header + ": " + row.getOrNull(index).orEmpty() }.joinToString("\n")
                    jobs += textJob(task.title + " " + (rowIndex + 1), text, opts, task.thickness)
                }
            }
            BatchType.TABLE -> {
                val fontSize = task.tableFontSize ?: BatchDefaults.tablePreset().fontSize
                val lineWidth = task.tableLineWidth ?: BatchDefaults.tablePreset().lineWidth
                val bold = task.tableBoldFirstRow ?: BatchDefaults.tablePreset().boldFirstRow
                val bitmap = renderTableToBitmap(
                    parseTableRows(task.text),
                    fontSize,
                    tableLineThickness(lineWidth),
                    boldFirstRow = bold,
                )
                val gray = bitmapToGray(bitmap)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                jobs += PrintJob(
                    title = task.title,
                    raster = packBinaryToRaster(binary, gray.width, gray.height),
                    copies = task.jobCount.coerceAtLeast(1),
                    thickness = task.thickness,
                )
            }
            BatchType.RECEIPT -> {
                val fontSize = task.receiptFontSize ?: BatchDefaults.receiptPreset().fontSize
                val lineWidth = task.receiptLineWidth ?: BatchDefaults.receiptPreset().lineWidth
                val itemized = task.receiptItemized ?: BatchDefaults.receiptPreset().itemized
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
                    itemized = itemized,
                    fontSize = fontSize,
                    lineWidth = tableLineThickness(lineWidth),
                )
                val gray = bitmapToGray(bitmap)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                jobs += PrintJob(
                    title = task.title,
                    raster = packBinaryToRaster(binary, gray.width, gray.height),
                    copies = task.jobCount.coerceAtLeast(1),
                    thickness = task.thickness,
                )
            }
            BatchType.TEXT -> {
                val opts = task.textOptions ?: BatchDefaults.textOptions()
                jobs += textJob(task.title, task.text, opts, task.thickness)
                    .copy(copies = task.jobCount.coerceAtLeast(1))
            }
        }
    }
    // 修挤压:多张图/多任务连续打印时,每个 job 末尾(最后一个除外)额外多走
    // ~3.7 mm(30 个空白点行),让两个任务之间有一条肉眼可见的缝,不至于挤在一起。
    // 单任务打印时(总 job 数 = 1)保持原状,不会多吐纸。
    if (jobs.size > 1) {
        for (i in 0 until jobs.size - 1) {
            val original = jobs[i]
            jobs[i] = original.copy(raster = appendBlankLines(original.raster, JOB_GAP_LINES))
        }
    }
    return jobs
}

private fun textJob(
    title: String,
    text: String,
    options: TextRenderOptions,
    thickness: Int,
): PrintJob {
    val bitmap = renderTextToPixelMap(text, options)
    val gray = bitmapToGray(bitmap)
    val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
    return PrintJob(
        title = title,
        raster = packBinaryToRaster(binary, gray.width, gray.height),
        thickness = thickness,
    )
}

/** 任务之间塞的空白行数。30 点 ≈ 3.7 mm,刚好让两段内容不挤。 */
private const val JOB_GAP_LINES: Int = 30

/**
 * 给光栅末尾拼 N 行白(全 0 字节 = 不加热 = 留白)。
 * 不复制整个数组,只新建一个长 30 行的容器,前面的内容用 arraycopy 拷过去,
 * 末尾的空白行 ByteArray 默认就是 0,正好是白色。
 */
private fun appendBlankLines(raster: RasterData, blankLines: Int): RasterData {
    if (blankLines <= 0) return raster
    val newHeight = raster.height + blankLines
    val newData = ByteArray(raster.widthBytes * newHeight)
    System.arraycopy(raster.data, 0, newData, 0, raster.data.size)
    return RasterData(newData, raster.widthBytes, newHeight)
}

private fun queryFileName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else "imported.csv"
        } ?: "imported.csv"
    }.getOrDefault("imported.csv")
}
