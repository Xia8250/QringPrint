package com.thisko.qringprint.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.model.deleteTemplate
import com.thisko.qringprint.model.loadTemplates
import com.thisko.qringprint.model.TemplateRecord
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
fun TemplatePage(modifier: Modifier = Modifier, onEdit: (String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<TemplateRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) { loadTemplates(context) }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().pageEnter().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("模板", fontWeight = FontWeight.SemiBold) },
            actions = {
                IconButton(onClick = { onEdit(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建模板")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("还没有模板", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onEdit(null) }) { Text("新建模板") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records.size) { idx ->
                    val rec = records[idx]
                    TemplateCard(
                        rec = rec,
                        onClick = { onEdit(rec.id) },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { deleteTemplate(context, rec.id) }
                                records = withContext(Dispatchers.IO) { loadTemplates(context) }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.items(count: Int, block: @Composable (Int) -> Unit) {
    repeat(count) { idx -> item { block(idx) } }
}

@Composable
private fun TemplateCard(rec: TemplateRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp).padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val thumbFile = rec.thumbnailPath.takeIf { it.isNotEmpty() }?.let { File(it) }
                val bmp = remember(thumbFile) {
                    thumbFile?.takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rec.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(dateFmt.format(Date(rec.updatedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Add, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) // 用 Add 占位,实际应该用 Delete
                }
            }
        }
    }
}
