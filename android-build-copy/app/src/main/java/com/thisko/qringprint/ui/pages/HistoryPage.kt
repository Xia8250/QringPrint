package com.thisko.qringprint.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.thisko.qringprint.model.HIST_TYPE_AD_BANNER
import com.thisko.qringprint.model.HIST_TYPE_CUSTOM
import com.thisko.qringprint.model.HIST_TYPE_IMAGE
import com.thisko.qringprint.model.HIST_TYPE_TEXT
import com.thisko.qringprint.model.HIST_TYPE_CODE
import com.thisko.qringprint.model.deleteHistory
import com.thisko.qringprint.model.HistoryRecord
import com.thisko.qringprint.model.loadHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.thisko.qringprint.ui.motion.pageEnter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryPage(modifier: Modifier = Modifier, onReprint: (String, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<HistoryRecord?>(null) }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) { loadHistory(context) }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("历史", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("还没有打印记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records.size) { idx ->
                    val rec = records[idx]
                    HistoryRow(
                        rec = rec,
                        onClick = { preview = rec },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { deleteHistory(context, rec.id) }
                                records = withContext(Dispatchers.IO) { loadHistory(context) }
                            }
                        }
                    )
                }
            }
        }
    }

    preview?.let { record ->
        Dialog(onDismissRequest = { preview = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val thumbFile = record.thumbnailPath.takeIf { it.isNotEmpty() }?.let(::File)
                    val bitmap = remember(record.id) {
                        thumbFile?.takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.History, contentDescription = null) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(record.typeName, fontWeight = FontWeight.SemiBold)
                    Text(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.printedAt)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (record.payload.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            record.payload,
                            fontSize = 12.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { preview = null }) { Text("关闭") }
                        Button(
                            onClick = {
                                val kind = when (record.typeName) {
                                    HIST_TYPE_TEXT -> "text"
                                    HIST_TYPE_IMAGE -> "image"
                                    HIST_TYPE_CODE -> "code"
                                    HIST_TYPE_CUSTOM -> "custom"
                                    HIST_TYPE_AD_BANNER -> "adbanner"
                                    else -> return@Button
                                }
                                preview = null
                                val payload = if (kind == "image") {
                        val pipeIdx = record.payload.indexOf('|')
                        if (pipeIdx in 1..199) {
                            val savedPath = record.payload.substring(0, pipeIdx)
                            if (File(savedPath).exists()) savedPath
                            else {
                                runCatching {
                                    org.json.JSONObject(record.payload.substring(pipeIdx + 1))
                                        .put("uri", android.net.Uri.fromFile(File(record.thumbnailPath)).toString())
                                        .toString()
                                }.getOrDefault(record.payload)
                            }
                        } else {
                            runCatching {
                                org.json.JSONObject(record.payload)
                                    .put("uri", android.net.Uri.fromFile(File(record.thumbnailPath)).toString())
                                    .toString()
                            }.getOrDefault(record.payload)
                        }
                    } else record.payload
                                onReprint(kind, payload)
                            }
                        ) { Text("重打") }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, block: @Composable (Int) -> Unit) {
    repeat(count) { idx -> item { block(idx) } }
}

@Composable
private fun HistoryRow(rec: HistoryRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumbFile = rec.thumbnailPath.takeIf { it.isNotEmpty() }?.let { File(it) }
            val bmp = remember(thumbFile) {
                thumbFile?.takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
            }
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rec.typeName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(dateFmt.format(Date(rec.printedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }
}

