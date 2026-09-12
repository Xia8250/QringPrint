package com.thisko.qringprint.ui.pages

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.bluetooth.renderTextToPixelMap
import com.thisko.qringprint.ui.pages.renderTitledTableToBitmap
import com.thisko.qringprint.ui.pages.schedulePrintRows
import com.thisko.qringprint.ui.pages.renderLabelToBitmap
import com.thisko.qringprint.ui.pages.renderTableToBitmap
import com.thisko.qringprint.ui.pages.tableLineThickness
import com.thisko.qringprint.model.HIST_TYPE_TEXT
import com.thisko.qringprint.model.addHistoryGray
import com.thisko.qringprint.model.loadDefaultThickness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.thisko.qringprint.ui.motion.pageEnter

enum class StructuredKind(val title: String) {
    TABLE("表格打印"),
    TIME("时间打印"),
    SCHEDULE("课程表打印"),
    LABEL("标签打印"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredPrintPage(kind: StructuredKind, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var copies by remember { mutableIntStateOf(1) }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var thickness by remember { mutableStateOf(loadDefaultThickness(context)) }

    BackHandler(enabled = true, onBack = onBack)

    var tableRows by remember(kind) {
        mutableStateOf(
            listOf(
                listOf("商品", "数量"),
                listOf("可乐", "2"),
                listOf("薯片", "1"),
                listOf("矿泉水", "3"),
            )
        )
    }
    var periodCount by remember(kind) { mutableIntStateOf(6) }
    var scheduleCells by remember(kind, periodCount) { mutableStateOf(List(periodCount * 7) { "" }) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingText by remember { mutableStateOf("") }
    var labelTitle by remember(kind) { mutableStateOf("测试") }
    var labelContent by remember(kind) { mutableStateOf("8586") }
    var now by remember { mutableStateOf(Date()) }
    var timePattern by remember(kind) { mutableStateOf("yyyy-MM-dd HH:mm:ss") }
    var timeNote by remember(kind) { mutableStateOf("") }

    val options = TextRenderOptions(
        fontFamily = if (kind == StructuredKind.TABLE || kind == StructuredKind.SCHEDULE) "monospace" else "sans-serif",
        fontSize = fontSize.toInt(),
        lineSpacing = 4f,
        margin = 10,
    )

    LaunchedEffect(Unit) {
        if (kind == StructuredKind.TIME) {
            while (true) {
                now = Date()
                delay(1000)
            }
        }
    }

    val content = when (kind) {
        StructuredKind.TABLE -> tableRows.joinToString("\n") { it.joinToString("|") }
        StructuredKind.SCHEDULE -> schedulePrintRows(periodCount, scheduleCells).joinToString("\n") { it.joinToString("|") }
        StructuredKind.LABEL -> listOf(labelTitle, labelContent).filter { it.isNotBlank() }.joinToString("\n")
        StructuredKind.TIME -> listOf(
            SimpleDateFormat(timePattern.ifBlank { "yyyy-MM-dd HH:mm:ss" }, Locale.getDefault()).format(now),
            timeNote,
        ).filter { it.isNotBlank() }.joinToString("\n")
    }

    LaunchedEffect(content, options, tableRows, periodCount, scheduleCells, thickness) {
        if (content.isBlank()) {
            preview = null
            return@LaunchedEffect
        }
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val source = when (kind) {
                    StructuredKind.TABLE -> renderTableToBitmap(
                        tableRows,
                        fontSize.toInt(),
                        tableLineThickness(thickness),
                    )
                    StructuredKind.SCHEDULE -> renderTitledTableToBitmap(
                        title = "周课程表",
                        subtitle = "",
                        rows = schedulePrintRows(periodCount, scheduleCells),
                        footer = "",
                        fontSize = fontSize.toInt(),
                        lineWidth = tableLineThickness(thickness),
                    )
                    StructuredKind.LABEL -> renderLabelToBitmap(
                        title = labelTitle,
                        content = labelContent,
                        fontSize = fontSize.toInt(),
                        lineWidth = tableLineThickness(thickness),
                    )
                    else -> renderTextToPixelMap(content, options)
                }
                val gray = bitmapToGray(source)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                binaryToPreviewBitmap(binary, gray.width, gray.height)
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(kind.title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ConnectionBanner()
            Spacer(Modifier.height(16.dp))

            when (kind) {
                StructuredKind.TABLE -> {
                    Text("表格内容（点击单元格编辑）", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    tableRows.forEachIndexed { rowIndex, row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            row.forEachIndexed { columnIndex, value ->
                                var cell by remember(rowIndex, columnIndex, value) { mutableStateOf(value) }
                                OutlinedTextField(
                                    value = cell,
                                    onValueChange = { newValue ->
                                        cell = newValue
                                        tableRows = tableRows.mapIndexed { rowIndex2, row2 ->
                                            if (rowIndex2 == rowIndex) {
                                                row2.mapIndexed { columnIndex2, value2 ->
                                                    if (columnIndex2 == columnIndex) newValue else value2
                                                }
                                            } else row2
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (rowIndex > 0) {
                                IconButton(onClick = {
                                    tableRows = tableRows.filterIndexed { index, _ -> index != rowIndex }
                                }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            { tableRows = tableRows + listOf(List(tableRows.firstOrNull()?.size ?: 2) { "" }) },
                            Modifier.weight(1f)
                        ) { Text("+ 行") }
                        OutlinedButton({ tableRows = tableRows.map { it + "" } }, Modifier.weight(1f)) { Text("+ 列") }
                        OutlinedButton(
                            { if ((tableRows.firstOrNull()?.size ?: 0) > 1) tableRows = tableRows.map { it.dropLast(1) } },
                            Modifier.weight(1f)
                        ) { Text("- 列") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("当前：${tableRows.firstOrNull()?.size ?: 0} 列 ${tableRows.size} 行", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                StructuredKind.SCHEDULE -> {
                    Text("节数：$periodCount", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4, 6, 8, 10).forEach { count ->
                            FilterChip(periodCount == count, { scheduleCells = List(count * 7) { "" }; periodCount = count }, { Text("${count}节") })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("课程表（点击填写/清空）", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            Row {
                                Box(Modifier.width(32.dp))
                                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEach { day ->
                                    Box(Modifier.width(72.dp), Alignment.Center) { Text(day, fontSize = 12.sp) }
                                }
                            }
                            repeat(periodCount) { period ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(32.dp)) { Text("${period + 1}", fontSize = 12.sp) }
                                    repeat(7) { day ->
                                        val index = period * 7 + day
                                        Box(
                                            Modifier
                                                .padding(3.dp)
                                                .size(66.dp, 42.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                                .clickable { editingCell = period to day; editingText = scheduleCells[index] },
                                            Alignment.Center
                                        ) {
                                            Text(scheduleCells[index].ifBlank { "-" }, fontSize = 10.sp, maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                StructuredKind.LABEL -> {
                    Text("标签内容", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(labelTitle, { labelTitle = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(labelContent, { labelContent = it }, label = { Text("内容") }, modifier = Modifier.fillMaxWidth())
                }

                StructuredKind.TIME -> {
                    Text("时间格式", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm", "MM月dd日 HH:mm", "HH:mm:ss").forEach { pattern ->
                            FilterChip(timePattern == pattern, { timePattern = pattern }, { Text(pattern) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(timeNote, { timeNote = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("字号：${fontSize.toInt()}sp")
            Slider(fontSize, { fontSize = it }, valueRange = 14f..28f)
            Text("打印浓度：浓度$thickness")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { level ->
                    FilterChip(thickness == level, { thickness = level }, { Text("浓度$level") })
                }
            }

            if (kind == StructuredKind.LABEL) {
                Spacer(Modifier.height(16.dp))
                Text("份数：$copies")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1, 2, 3, 5, 10, 20).forEach { count ->
                        FilterChip(copies == count, { copies = count }, { Text("${count}份") })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PreviewCard(preview, Modifier.fillMaxWidth().heightIn(min = 220.dp))
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    PrinterConnection.preflightCheck()?.let {
                        message = it
                        return@launch
                    }
                    printing = true
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            when (kind) {
                                StructuredKind.TABLE -> renderTableToBitmap(
                                    tableRows,
                                    fontSize.toInt(),
                                    tableLineThickness(thickness),
                                )
                                StructuredKind.SCHEDULE -> renderTitledTableToBitmap(
                                    title = "周课程表",
                                    subtitle = "",
                                    rows = schedulePrintRows(periodCount, scheduleCells),
                                    footer = "",
                                    fontSize = fontSize.toInt(),
                                    lineWidth = tableLineThickness(thickness),
                                )
                                StructuredKind.LABEL -> renderLabelToBitmap(
                                    title = labelTitle,
                                    content = labelContent,
                                    fontSize = fontSize.toInt(),
                                    lineWidth = tableLineThickness(thickness),
                                )
                                else -> renderTextToPixelMap(content, options)
                            }
                        }
                        val gray = bitmapToGrayRaw(bitmap)
                        val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                        val raster = packBinaryToRaster(binary, gray.width, gray.height)
                        PrintQueue.enqueue(PrintJob(title = kind.title, raster = raster, copies = copies.coerceAtLeast(1)))
                        addHistoryGray(context, HIST_TYPE_TEXT, gray, "{\"kind\":\"${kind.name}\"}")
                        message = "已提交到打印队列"
                    } catch (e: Throwable) {
                        message = "提交失败：${e.message}"
                    } finally {
                        printing = false
                    }
                }
            },
            enabled = !printing && content.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            if (printing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            else {
                Icon(Icons.Filled.Print, null)
                Spacer(Modifier.width(8.dp))
                Text("打印")
            }
        }
    }

    editingCell?.let { cell ->
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("第${cell.first + 1}节 · 周${"一二三四五六日".getOrElse(cell.second) { '?' }}") },
            text = { OutlinedTextField(editingText, { editingText = it }, label = { Text("课程") }, singleLine = true) },
            confirmButton = {
                TextButton({
                    val index = cell.first * 6 + cell.second
                    scheduleCells = scheduleCells.toMutableList().also { it[index] = editingText.trim() }
                    editingCell = null
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton({
                        val index = cell.first * 6 + cell.second
                        scheduleCells = scheduleCells.toMutableList().also { it[index] = "" }
                        editingText = ""
                        editingCell = null
                    }) { Text("清空") }
                    TextButton({ editingCell = null }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
fun ConnectionBanner() {
    val status by PrinterConnection.status.collectAsState()
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp)) {
        Text(
            if (status.connState == com.thisko.qringprint.model.ConnState.CONNECTED) "打印机已连接"
            else "未连接打印机 请返回首页点击状态卡选择设备",
            modifier = Modifier.padding(14.dp),
            fontSize = 13.sp,
            color = if (status.connState == com.thisko.qringprint.model.ConnState.CONNECTED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun PreviewCard(bitmap: android.graphics.Bitmap?, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(12.dp)) {
        Box(Modifier.fillMaxSize().padding(12.dp), Alignment.Center) {
            if (bitmap == null) Text("预览", color = Color.Gray)
            else Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

internal fun buildTableText(raw: String): String {
    val rows = raw.lines().filter { it.isNotBlank() }.map { line -> line.split('|', ',', '\t').map { it.trim() } }
    if (rows.isEmpty()) return ""
    val columnCount = rows.maxOf { it.size }.coerceAtMost(4)
    val normalized = rows.map { row -> (0 until columnCount).map { row.getOrNull(it).orEmpty() } }
    val available = 34 - (columnCount * 3 - 1)
    val rawWidths = (0 until columnCount).map { index -> normalized.maxOf { visualWidth(it[index]) } }
    val totalRaw = rawWidths.sum().coerceAtLeast(1)
    val widths = rawWidths.map { ((it.toFloat() / totalRaw) * available).toInt().coerceIn(2, available) }
    val divider = widths.joinToString("+", "+", "+") { "-".repeat(it) }
    val lines = mutableListOf(divider)
    normalized.forEach { row ->
        lines += "| " + row.mapIndexed { index, value -> padVisual(value, widths[index]) }.joinToString(" | ") + " |"
        lines += divider
    }
    return lines.joinToString("\n")
}

internal fun visualWidth(value: String): Int =
    value.fold(0) { width, char -> width + if (char.code > 0x2E7F) 2 else 1 }

internal fun truncateVisual(value: String, width: Int): String {
    var used = 0
    return buildString {
        for (char in value) {
            val charWidth = if (char.code > 0x2E7F) 2 else 1
            if (used + charWidth > width) break
            append(char)
            used += charWidth
        }
    }
}

internal fun padVisual(value: String, width: Int): String {
    val safeValue = truncateVisual(value, width)
    return safeValue + " ".repeat((width - visualWidth(safeValue)).coerceAtLeast(0))
}

private fun buildScheduleGrid(periodCount: Int, cells: List<String>): String {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val rows = mutableListOf(listOf("周课程表"), listOf("节次", *days.toTypedArray()))
    repeat(periodCount) { period ->
        rows += listOf(
            "第${period + 1}节",
            *days.mapIndexed { day, _ -> cells.getOrNull(period * 7 + day)?.ifBlank { "-" } ?: "-" }.toTypedArray()
        )
    }
    return buildTableText(rows.joinToString("\n") { it.joinToString("|") })
}
