package com.thisko.qringprint.ui.pages

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thisko.qringprint.bluetooth.WIDTH_DOTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 共享的预览舞台：
 *   1) 像素 1:1 展示渲染结果（背景为热敏纸等比例栅格）
 *   2) 双指捏合 / 拖拽 / 双击可放大、平移、回正
 *   3) 工具栏提供放大、缩小、回正、下载到本机相册
 */
@Composable
fun PreviewStage(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    filePrefix: String = "preview",
    contentDescription: String = "预览",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var saved by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(bitmap) {
        // 切换预览时重置缩放
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Column(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                            if (newScale != scale) scale = newScale
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { bmp ->
                    val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                    val widthDp = (220 - 24)
                    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { widthDp.dp.toPx() }
                    val displayHeight = (widthPx * aspect).toInt().coerceAtLeast(1)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .width(widthDp.dp)
                            .height(with(androidx.compose.ui.platform.LocalDensity.current) { displayHeight.toDp() })
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                } ?: Text(
                    "暂无预览",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "打印宽 $WIDTH_DOTS 点" + (bitmap?.let { " · 预览高 ${it.height} 点" } ?: ""),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scale = (scale * 0.9f).coerceAtLeast(0.5f) }) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "缩小")
                }
                IconButton(onClick = { scale = (scale * 1.1f).coerceAtMost(5f) }) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "放大")
                }
                IconButton(onClick = {
                    scale = 1f; offsetX = 0f; offsetY = 0f
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "回正")
                }
                IconButton(
                    enabled = bitmap != null && !saving,
                    onClick = {
                        val target = bitmap ?: return@IconButton
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { savePreview(context, target, filePrefix) }
                            saved = result
                            saving = false
                        }
                    },
                ) {
                    if (saving) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = "下载预览")
                    }
                }
            }
        }
        saved?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

private fun savePreview(context: Context, bitmap: Bitmap, prefix: String): String {
    val name = "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())}.png"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QringPrint")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "下载失败：无法创建文件"
            resolver.openOutputStream(uri).use { stream ->
                if (stream == null) return "下载失败：无法写入"
                writePng(bitmap, stream)
            }
            "已保存到相册 /Pictures/QringPrint/$name"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "QringPrint")
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, name)
            FileOutputStream(target).use { writePng(bitmap, it) }
            "已保存到相册 Pictures/QringPrint/$name"
        }
    } catch (t: Throwable) {
        "下载失败：${t.message}"
    }
}

private fun writePng(bitmap: Bitmap, stream: OutputStream) {
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.flush()
}
