package com.thisko.qringprint.ui.pages

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.PrintJob
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.bitmapToGrayRaw
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.ui.pages.tableLineThickness
import com.thisko.qringprint.bluetooth.packBinaryToRaster
import com.thisko.qringprint.model.HIST_TYPE_TEXT
import com.thisko.qringprint.model.addHistoryGray
import com.thisko.qringprint.model.loadDefaultThickness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.thisko.qringprint.ui.motion.pageEnter

data class ReceiptLine(var name: String, var quantity: String, var price: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPrintPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var template by remember { mutableIntStateOf(0) }
    var shopName by remember { mutableStateOf("浣熊快印 门店") }
    var dateText by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
    }
    var items by remember {
        mutableStateOf(listOf(ReceiptLine("可乐", "2", "3"), ReceiptLine("薯片", "1", "8")))
    }
    var payment by remember { mutableStateOf("微信支付") }
    var footer by remember { mutableStateOf("谢谢光临") }
    var fontSize by remember { mutableFloatStateOf(20f) }
    var thickness by remember { mutableStateOf(loadDefaultThickness(context)) }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    BackHandler(enabled = true, onBack = onBack)

    val total = items.sumOf { line ->
        (line.quantity.toDoubleOrNull() ?: 0.0) * (line.price.toDoubleOrNull() ?: 0.0)
    }

    LaunchedEffect(template) {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        when (template) {
            0 -> {
                shopName = "浣熊快印 门店"
                dateText = dateTimeFormatter.format(Date())
                items = listOf(ReceiptLine("美式咖啡", "1", "15"), ReceiptLine("可乐", "2", "9"))
                footer = "谢谢惠顾"
            }
            1 -> {
                shopName = "我的店铺"
                dateText = dateFormatter.format(Date())
                items = listOf(ReceiptLine("可乐", "2", "3"), ReceiptLine("薯片", "1", "8"))
                footer = "谢谢光临"
            }
            else -> {
                shopName = "美味餐厅"
                dateText = dateFormatter.format(Date())
                items = listOf(
                    ReceiptLine("宫保鸡丁", "1", "38"),
                    ReceiptLine("米饭", "2", "3"),
                    ReceiptLine("酸梅汤", "2", "6"),
                )
                footer = "谢谢光临"
            }
        }
    }

    LaunchedEffect(template, shopName, dateText, items, payment, footer, fontSize, thickness) {
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val source = renderReceiptToBitmap(
                    title = shopName,
                    date = dateText,
                    items = items,
                    total = total,
                    payment = payment,
                    footer = footer,
                    itemized = template != 0,
                    fontSize = fontSize.toInt(),
                    lineWidth = tableLineThickness(thickness),
                )
                val gray = bitmapToGray(source)
                val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                binaryToPreviewBitmap(binary, gray.width, gray.height)
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("发票/小票", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ConnectionBanner()
            Spacer(Modifier.height(16.dp))
            Text("模板", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(template == 0, { template = 0 }, { Text("通用小票") })
                FilterChip(template == 1, { template = 1 }, { Text("店铺小票") })
                FilterChip(template == 2, { template = 2 }, { Text("餐厅小票") })
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(shopName, { shopName = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                dateText,
                { dateText = it },
                label = { Text(if (template == 0) "日期时间" else "日期") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (template == 0) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(payment, { payment = it }, label = { Text("支付方式") }, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(14.dp))
            Text("商品列表", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("商品", Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("数量", Modifier.width(64.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("单价", Modifier.width(64.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(44.dp))
            }
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(
                        item.name,
                        { value -> items = items.toMutableList().also { list -> list[index] = item.copy(name = value) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        item.quantity,
                        { value -> items = items.toMutableList().also { list -> list[index] = item.copy(quantity = value) } },
                        modifier = Modifier.width(64.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        item.price,
                        { value -> items = items.toMutableList().also { list -> list[index] = item.copy(price = value) } },
                        modifier = Modifier.width(64.dp),
                        singleLine = true,
                    )
                    IconButton({ items = items.filterIndexed { position, _ -> position != index } }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            OutlinedButton(
                { items += ReceiptLine("", "1", "0") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("添加商品")
            }

            Spacer(Modifier.height(12.dp))
            Text("合计金额：¥%.2f".format(total), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text("字号：${fontSize.toInt()}sp")
            Slider(fontSize, { fontSize = it }, valueRange = 14f..28f)
            Text("打印浓度：$thickness")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { level ->
                    FilterChip(thickness == level, { thickness = level }, { Text("浓度$level") })
                }
            }

            Spacer(Modifier.height(16.dp))
            PreviewCard(preview, Modifier.fillMaxWidth().heightIn(min = 240.dp))
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
                        val bitmap = withContext(Dispatchers.IO) {
                            renderReceiptToBitmap(
                                title = shopName,
                                date = dateText,
                                items = items,
                                total = total,
                                payment = payment,
                                footer = footer,
                                itemized = template != 0,
                                fontSize = fontSize.toInt(),
                                lineWidth = tableLineThickness(thickness),
                            )
                        }
                        val gray = bitmapToGrayRaw(bitmap)
                        val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
                        val raster = packBinaryToRaster(binary, gray.width, gray.height)
                        PrintQueue.enqueue(PrintJob(title = "发票/小票", raster = raster))
                        addHistoryGray(context, HIST_TYPE_TEXT, gray, "{\"kind\":\"RECEIPT\",\"template\":$template}")
                        message = "已提交到打印队列"
                    } catch (e: Throwable) {
                        message = "提交失败：${e.message}"
                    } finally {
                        printing = false
                    }
                }
            },
            enabled = !printing && items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            if (printing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Filled.Print, null)
                Spacer(Modifier.width(8.dp))
                Text("打印")
            }
        }
    }
}